package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.CatalogHttpApi.CatalogProduct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How the lines a customer asks for are checked and turned into the lines of their order. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CheckoutLines {

    /** Each product goes on one line only, so it is reserved and charged once. */
    static void rejectDuplicateProducts(List<OrderService.RequestedLine> requestedLines) {
        Set<UUID> seenProductIds = new HashSet<>();
        for (OrderService.RequestedLine requested : requestedLines) {
            if (!seenProductIds.add(requested.productId())) {
                throw OrderRejectedException.duplicateProduct(requested.productId());
            }
        }
    }

    /** The order keeps the product's name and price as the catalog had them at checkout. */
    static OrderLine orderLineOf(CatalogProduct product, int quantity) {
        return new OrderLine(product.id(), product.sku(), product.name(), quantity, product.price());
    }
}
