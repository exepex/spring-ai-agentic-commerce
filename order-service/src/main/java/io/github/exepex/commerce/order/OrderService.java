package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.CatalogHttpApi.CatalogProduct;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.ErrorResponseException;

/**
 * Checkout and cancellation. This is the deterministic path: no AI agent is involved in placing an order.
 *
 * <p>Placing an order reserves its stock in the catalog, saves it as {@link OrderStatus#PLACED}, then takes payment.
 * The order is saved before the card is charged, so a payment is never taken for an order that does not exist. If
 * any step fails, the reserved stock is released. No database transaction is held open across remote calls.
 */
@Service
public class OrderService {

    public record RequestedLine(UUID productId, int quantity) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

    private final CustomerOrderRepository orders;
    private final CatalogGateway catalog;
    private final PaymentGateway payments;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    OrderService(CustomerOrderRepository orders, CatalogGateway catalog, PaymentGateway payments,
            ApplicationEventPublisher events, TransactionTemplate transaction, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.payments = payments;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
    }

    public CustomerOrder placeOrder(String customerEmail, List<RequestedLine> requestedLines, String paymentMethod) {
        rejectDuplicateProducts(requestedLines);
        CustomerOrder order = reserveAndSave(UUID.randomUUID(), customerEmail, requestedLines);
        try {
            payments.charge(order.getId(), customerEmail, order.getTotalAmount(), order.getCurrency(), paymentMethod);
        } catch (PaymentGateway.PaymentDeclinedException declined) {
            failPayment(order, declined.getMessage());
            throw naming(order, OrderRejectedException.paymentDeclined(declined.getMessage()));
        } catch (PaymentGateway.PaymentUnavailableException unavailable) {
            LOGGER.warn("Payment for order {} could not be taken", order.getId(), unavailable);
            failPayment(order, unavailable.getMessage());
            throw naming(order, new DependencyUnavailableException("payment service", unavailable));
        }
        return transaction.execute(status -> {
            order.confirm();
            events.publishEvent(OrderEvent.of(OrderEvent.Type.ORDER_CONFIRMED, order, Instant.now(clock)));
            return orders.save(order);
        });
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
     * Gives the order's stock back to the catalog, then marks it cancelled; the shipping service cancels the shipment
     * from the event. Cancelling twice changes nothing. Refunds are a separate decision, made through the payment
     * service.
     */
    public CustomerOrder cancelOrder(UUID orderId, String reason) {
        CustomerOrder order = getOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        if (!order.isCancellable()) {
            throw OrderRejectedException.notCancellable(orderId, order.getStatus());
        }
        catalog.releaseOrderReservations(orderId);
        return transaction.execute(status -> {
            order.cancel(reason, Instant.now(clock));
            events.publishEvent(OrderEvent.of(OrderEvent.Type.ORDER_CANCELLED, order, Instant.now(clock)));
            return orders.save(order);
        });
    }

    private CustomerOrder reserveAndSave(UUID orderId, String customerEmail, List<RequestedLine> requestedLines) {
        try {
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
                    Instant.now(clock)));
        } catch (RuntimeException failure) {
            releaseQuietly(orderId);
            throw failure;
        }
    }

    private void failPayment(CustomerOrder order, String reason) {
        releaseQuietly(order.getId());
        order.markPaymentFailed(reason);
        orders.save(order);
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

    private void releaseQuietly(UUID orderId) {
        try {
            catalog.releaseOrderReservations(orderId);
        } catch (DependencyUnavailableException releaseFailure) {
            LOGGER.warn("Could not release the stock reserved for order {}; it stays reserved", orderId, releaseFailure);
        }
    }
}
