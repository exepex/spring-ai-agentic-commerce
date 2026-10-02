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
import org.springframework.stereotype.Service;

/**
 * Places and cancels orders. Stock lives in the catalog, so placing an order reserves it there first and gives it
 * back if any step fails. No database transaction is held open across those remote calls.
 */
@Service
public class OrderService {

    public record RequestedLine(UUID productId, int quantity) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

    private final CustomerOrderRepository orders;
    private final CatalogGateway catalog;
    private final Clock clock;

    OrderService(CustomerOrderRepository orders, CatalogGateway catalog, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.clock = clock;
    }

    public CustomerOrder placeOrder(String customerEmail, List<RequestedLine> requestedLines) {
        rejectDuplicateProducts(requestedLines);
        UUID orderId = UUID.randomUUID();
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
            releaseAfterFailedPlacement(orderId);
            throw failure;
        }
    }

    public CustomerOrder getOrder(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<CustomerOrder> findOrdersOf(String customerEmail) {
        return orders.findByCustomerEmailOrderByCreatedAtDesc(customerEmail);
    }

    /** Gives the order's stock back to the catalog, then marks it cancelled. Cancelling twice changes nothing. */
    public CustomerOrder cancelOrder(UUID orderId, String reason) {
        CustomerOrder order = getOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        catalog.releaseOrderReservations(orderId);
        order.cancel(reason, Instant.now(clock));
        return orders.save(order);
    }

    private static void rejectDuplicateProducts(List<RequestedLine> requestedLines) {
        Set<UUID> seenProductIds = new HashSet<>();
        for (RequestedLine requested : requestedLines) {
            if (!seenProductIds.add(requested.productId())) {
                throw OrderRejectedException.duplicateProduct(requested.productId());
            }
        }
    }

    private void releaseAfterFailedPlacement(UUID orderId) {
        try {
            catalog.releaseOrderReservations(orderId);
        } catch (CatalogUnavailableException releaseFailure) {
            LOGGER.warn("Could not release the stock reserved for failed order {}; it stays reserved", orderId,
                    releaseFailure);
        }
    }
}
