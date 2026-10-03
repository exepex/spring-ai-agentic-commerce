package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.dto.RequestedLine;
import io.github.exepex.commerce.order.exception.MixedCurrenciesException;
import io.github.exepex.commerce.order.exception.OrderIdTakenException;
import io.github.exepex.commerce.order.exception.OrderNotCancellableException;
import io.github.exepex.commerce.order.exception.OrderNotFoundException;
import io.github.exepex.commerce.order.exception.OrderNotShippableException;
import io.github.exepex.commerce.order.exception.OrderPaymentFailedException;
import io.github.exepex.commerce.order.exception.PaymentDeclinedException;
import io.github.exepex.commerce.order.exception.PaymentUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CustomerOrderRepository orders;
    private final CatalogGateway catalog;
    private final PaymentGateway payments;
    private final StockReleases releases;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /**
     * Places an order and takes its payment. With {@code requestedOrderId}, placing is idempotent: asking again with
     * the same id returns the order placed the first time instead of placing a second one.
     *
     * @return the order: confirmed, with its payment pending, or still being placed by a concurrent request with the
     *     same id
     * @throws OrderPaymentFailedException when the card was declined, naming the order
     */
    public CustomerOrder placeOrder(UUID requestedOrderId, String customerEmail, List<RequestedLine> requestedLines,
            String paymentMethod) {
        if (requestedOrderId != null) {
            var placedBefore = orders.findById(requestedOrderId);
            if (placedBefore.isPresent()) {
                return outcomeOf(placedBefore.get(), customerEmail);
            }
        }
        CheckoutLines.rejectDuplicateProducts(requestedLines);
        var orderId = requestedOrderId != null ? requestedOrderId : UUID.randomUUID();
        CustomerOrder order;
        try {
            order = reserveAndSave(orderId, customerEmail, requestedLines, paymentMethod);
        } catch (RuntimeException failure) {
            var placedMeanwhile = orders.findById(orderId);
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
        } catch (PaymentDeclinedException declined) {
            return change(order, () -> {
                order.markPaymentFailed(declined.getMessage());
                releases.request(order.getId());
            }, () -> releases.attempt(order.getId()));
        } catch (PaymentUnavailableException unknown) {
            log.warn("The payment for order {} could not be confirmed; it will be asked for again", order.getId(),
                    unknown);
            return order.getStatus() == OrderStatus.PAYMENT_PENDING ? order : change(order, order::markPaymentPending, null);
        }
        return change(order, () -> {
            order.confirm();
            announce(OrderEvent.Type.ORDER_CONFIRMED, order);
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
        var order = getOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        if (!order.isCancellable()) {
            throw new OrderNotCancellableException(orderId, order.getStatus());
        }
        var cancelled = change(order, () -> {
            order.cancel(reason, Instant.now(clock));
            announce(OrderEvent.Type.ORDER_CANCELLED, order);
            releases.request(orderId);
        }, () -> releases.attempt(orderId));
        if (cancelled.getStatus() != OrderStatus.CANCELLED) {
            throw new OrderNotCancellableException(orderId, cancelled.getStatus());
        }
        return cancelled;
    }

    /**
     * Ships a confirmed order: the catalog takes its stock out of the warehouse, then the order is marked shipped and
     * announced, and the shipping service hands the parcel to the carrier. This order service decides between shipping
     * and cancelling, so an order is never both: whichever change saves first wins, and the other is refused. If the
     * order is cancelled after its stock was dispatched, the cancellation's stock release puts the units back.
     * Shipping twice changes nothing.
     */
    public CustomerOrder shipOrder(UUID orderId) {
        var order = getOrder(orderId);
        if (order.hasShipped()) {
            return order;
        }
        if (!order.isShippable()) {
            throw new OrderNotShippableException(orderId, order.getStatus());
        }
        catalog.dispatchOrder(orderId);
        var shipped = change(order, () -> {
            order.ship();
            announce(OrderEvent.Type.ORDER_SHIPPED, order);
        }, null);
        if (!shipped.hasShipped()) {
            throw new OrderNotShippableException(orderId, shipped.getStatus());
        }
        return shipped;
    }

    /**
     * Records what the carrier reported for a shipped order. The carrier reports each parcel once, but Kafka may
     * deliver the report again: an order that already has the outcome is left as it is.
     */
    void recordCarrierOutcome(UUID orderId, OrderStatus outcome) {
        var order = getOrder(orderId);
        if (order.getStatus() == outcome) {
            return;
        }
        if (order.getStatus() != OrderStatus.SHIPPED) {
            log.warn("Ignoring carrier outcome {} for order {}, which is {}", outcome, orderId, order.getStatus());
            return;
        }
        transaction.executeWithoutResult(status -> {
            order.recordCarrierOutcome(outcome);
            orders.save(order);
        });
    }

    /** Checkout's answer for an order it placed now or before. */
    private static CustomerOrder outcomeOf(CustomerOrder order, String customerEmail) {
        if (!order.isPlacedBy(customerEmail)) {
            throw new OrderIdTakenException(order.getId());
        }
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            throw new OrderPaymentFailedException(order.getId(), order.getPaymentFailure());
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
        var lines = new ArrayList<OrderLine>();
        var currencies = new HashSet<String>();
        for (var requested : requestedLines) {
            var product = catalog.getProduct(requested.productId());
            catalog.reserveStock(product.id(), orderId, requested.quantity());
            lines.add(CheckoutLines.orderLineOf(product, requested.quantity()));
            currencies.add(product.currency());
        }
        if (currencies.size() > 1) {
            throw new MixedCurrenciesException();
        }
        return orders.save(CustomerOrder.place(orderId, customerEmail, currencies.iterator().next(), lines,
                paymentMethod, Instant.now(clock)));
    }

    /** Announces the change once the transaction it is part of has committed. */
    private void announce(OrderEvent.Type type, CustomerOrder order) {
        events.publishEvent(OrderEvent.of(type, order, Instant.now(clock)));
    }
}
