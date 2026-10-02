package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.CatalogHttpApi.CatalogProduct;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.ErrorResponseException;

/**
 * Checkout and cancellation. This is the deterministic path: no AI agent is involved in placing an order.
 *
 * <p>Placing an order reserves its stock in the catalog, saves it as {@link OrderStatus#PLACED}, then takes payment.
 * The order is saved before the card is charged, so a payment is never taken for an order that does not exist. A
 * declined card fails the order and releases its stock. When the payment's outcome is not known, the order waits as
 * {@link OrderStatus#PAYMENT_PENDING} with its stock kept, and {@link OrderReconciler} asks for the same payment again
 * until it is settled: the charge is idempotent, so asking again never charges twice.
 *
 * <p>Stock to give back is recorded in the same transaction as the order change that needs it ({@link StockReleases}),
 * so it is released even if the catalog is down at that moment. No database transaction is held open across remote
 * calls.
 */
@Service
public class OrderService {

    public record RequestedLine(UUID productId, int quantity) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

    private final CustomerOrderRepository orders;
    private final CatalogGateway catalog;
    private final PaymentGateway payments;
    private final StockReleases releases;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    OrderService(CustomerOrderRepository orders, CatalogGateway catalog, PaymentGateway payments,
            StockReleases releases, ApplicationEventPublisher events, TransactionTemplate transaction, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.payments = payments;
        this.releases = releases;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Places an order and takes its payment. With {@code requestedOrderId}, placing is idempotent: asking again with
     * the same id returns the order placed the first time instead of placing a second one.
     *
     * @return the order: confirmed, with its payment pending, or still being placed by a concurrent request with the
     *     same id
     * @throws OrderRejectedException when the card was declined, naming the order
     */
    public CustomerOrder placeOrder(UUID requestedOrderId, String customerEmail, List<RequestedLine> requestedLines,
            String paymentMethod) {
        if (requestedOrderId != null) {
            Optional<CustomerOrder> placedBefore = orders.findById(requestedOrderId);
            if (placedBefore.isPresent()) {
                return outcomeOf(placedBefore.get(), customerEmail);
            }
        }
        rejectDuplicateProducts(requestedLines);
        UUID orderId = requestedOrderId != null ? requestedOrderId : UUID.randomUUID();
        CustomerOrder order;
        try {
            order = reserveAndSave(orderId, customerEmail, requestedLines, paymentMethod);
        } catch (RuntimeException failure) {
            Optional<CustomerOrder> placedMeanwhile = orders.findById(orderId);
            if (placedMeanwhile.isPresent()) {
                // A concurrent request with the same id placed it; that request owns its stock and its payment.
                return outcomeOf(placedMeanwhile.get(), customerEmail);
            }
            transaction.executeWithoutResult(status -> releases.request(orderId));
            releases.attempt(orderId);
            throw failure;
        }
        return outcomeOf(settlePayment(order), customerEmail);
    }

    /**
     * Takes the order's payment, or asks for it again, and settles the order: confirmed when it succeeds, failed with
     * its stock released when the card is declined, pending when the outcome is not known. If another request settled
     * the order at the same time, the order is returned as that request left it.
     */
    CustomerOrder settlePayment(CustomerOrder order) {
        try {
            payments.charge(order.getId(), order.getCustomerEmail(), order.getTotalAmount(), order.getCurrency(),
                    order.getPaymentMethod());
        } catch (PaymentGateway.PaymentDeclinedException declined) {
            return change(order, () -> {
                order.markPaymentFailed(declined.getMessage());
                releases.request(order.getId());
            }, () -> releases.attempt(order.getId()));
        } catch (PaymentGateway.PaymentUnavailableException unknown) {
            LOGGER.warn("The payment for order {} could not be confirmed; it will be asked for again", order.getId(),
                    unknown);
            return order.getStatus() == OrderStatus.PAYMENT_PENDING ? order : change(order, order::markPaymentPending, null);
        }
        return change(order, () -> {
            order.confirm();
            events.publishEvent(OrderEvent.of(OrderEvent.Type.ORDER_CONFIRMED, order, Instant.now(clock)));
        }, null);
    }

    public CustomerOrder getOrder(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<CustomerOrder> findOrdersOf(String customerEmail) {
        return orders.findByCustomerEmailOrderByCreatedAtDesc(customerEmail);
    }

    public List<CustomerOrder> findRecentOrders() {
        return orders.findTop100ByOrderByCreatedAtDesc();
    }

    /**
     * Marks the order cancelled and records its stock for release in one transaction, then releases the stock; the
     * shipping service cancels the shipment from the event. If the catalog is down, the release is retried until it
     * happens. Cancelling twice changes nothing. Refunds are a separate decision, made through the payment service.
     */
    public CustomerOrder cancelOrder(UUID orderId, String reason) {
        CustomerOrder order = getOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        if (!order.isCancellable()) {
            throw OrderRejectedException.notCancellable(orderId, order.getStatus());
        }
        CustomerOrder cancelled = change(order, () -> {
            order.cancel(reason, Instant.now(clock));
            events.publishEvent(OrderEvent.of(OrderEvent.Type.ORDER_CANCELLED, order, Instant.now(clock)));
            releases.request(orderId);
        }, () -> releases.attempt(orderId));
        if (cancelled.getStatus() != OrderStatus.CANCELLED) {
            throw OrderRejectedException.notCancellable(orderId, cancelled.getStatus());
        }
        return cancelled;
    }

    /** Checkout's answer for an order it placed now or before. */
    private static CustomerOrder outcomeOf(CustomerOrder order, String customerEmail) {
        if (!order.getCustomerEmail().equalsIgnoreCase(customerEmail)) {
            throw OrderRejectedException.idTaken(order.getId());
        }
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            throw naming(order, OrderRejectedException.paymentDeclined(order.getPaymentFailure()));
        }
        return order;
    }

    /**
     * Applies a change to the order and saves it in one transaction, then runs {@code afterCommit}. If a concurrent
     * request changed the order first, nothing is applied and the order is returned as that request left it.
     */
    private CustomerOrder change(CustomerOrder order, Runnable update, Runnable afterCommit) {
        CustomerOrder saved;
        try {
            saved = transaction.execute(status -> {
                update.run();
                return orders.save(order);
            });
        } catch (ObjectOptimisticLockingFailureException changedMeanwhile) {
            return getOrder(order.getId());
        }
        if (afterCommit != null) {
            afterCommit.run();
        }
        return saved;
    }

    /** Reserves every line in the catalog and saves the order as placed. On failure the caller releases the stock. */
    private CustomerOrder reserveAndSave(UUID orderId, String customerEmail, List<RequestedLine> requestedLines,
            String paymentMethod) {
        List<OrderLine> lines = new ArrayList<>();
        Set<String> currencies = new HashSet<>();
        for (RequestedLine requested : requestedLines) {
            CatalogProduct product = catalog.getProduct(requested.productId());
            catalog.reserveStock(product.id(), orderId, requested.quantity());
            lines.add(new OrderLine(product.id(), product.sku(), product.name(), requested.quantity(), product.price()));
            currencies.add(product.currency());
        }
        if (currencies.size() > 1) {
            throw OrderRejectedException.mixedCurrencies();
        }
        return orders.save(CustomerOrder.place(orderId, customerEmail, currencies.iterator().next(), lines,
                paymentMethod, Instant.now(clock)));
    }

    /** The order whose payment failed is kept, so the error names it: callers can link what they record to it. */
    private static <E extends ErrorResponseException> E naming(CustomerOrder order, E failure) {
        failure.getBody().setProperty("orderId", order.getId());
        return failure;
    }

    private static void rejectDuplicateProducts(List<RequestedLine> requestedLines) {
        Set<UUID> seenProductIds = new HashSet<>();
        for (RequestedLine requested : requestedLines) {
            if (!seenProductIds.add(requested.productId())) {
                throw OrderRejectedException.duplicateProduct(requested.productId());
            }
        }
    }
}
