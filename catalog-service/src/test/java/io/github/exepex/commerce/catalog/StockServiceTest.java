package io.github.exepex.commerce.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class StockServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final StockReservationRepository reservations = mock(StockReservationRepository.class);
    private final StockService stock = new StockService(products, reservations, mock(ApplicationEventPublisher.class),
            Clock.systemUTC());

    @Test
    void dispatchingWhileTheStockCoversEveryOrderDoesNotReadTheProductsOtherReservations() {
        var orderId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var product = mock(Product.class);
        when(product.shortfall()).thenReturn(0);
        when(products.findForUpdate(productId)).thenReturn(Optional.of(product));
        when(reservations.findByOrderIdOrderByProductId(orderId))
                .thenReturn(List.of(new StockReservation(orderId, productId, 2, Instant.now())));

        stock.dispatchOrder(orderId);

        verify(product).dispatch(2);
        verify(reservations, never()).findByProductIdAndStatusOrderByCreatedAtDesc(any(), any());
    }
}
