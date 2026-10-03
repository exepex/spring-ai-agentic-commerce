package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.OrderValues;
import io.github.exepex.commerce.order.dto.CatalogProduct;
import io.github.exepex.commerce.order.dto.ReserveStockRequest;
import io.github.exepex.commerce.order.exception.DependencyUnavailableException;
import io.github.exepex.commerce.order.exception.DispatchRefusedException;
import io.github.exepex.commerce.order.exception.InsufficientStockException;
import io.github.exepex.commerce.order.exception.ProductNotFoundException;
import io.github.exepex.commerce.platform.remote.RemoteProblems;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/** Calls the catalog and turns its HTTP failures into order-domain errors. */
@Component
@RequiredArgsConstructor
class CatalogGateway {

    private final CatalogHttpApi catalog;

    CatalogProduct getProduct(UUID productId) {
        try {
            return catalog.getProduct(productId);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new ProductNotFoundException(productId);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException(OrderValues.CATALOG, failure);
        }
    }

    void reserveStock(UUID productId, UUID orderId, int quantity) {
        try {
            catalog.reserveStock(productId, new ReserveStockRequest(orderId, quantity));
        } catch (HttpClientErrorException.Conflict conflict) {
            throw new InsufficientStockException(catalogDetail(conflict));
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException(OrderValues.CATALOG, failure);
        }
    }

    void releaseOrderReservations(UUID orderId) {
        try {
            catalog.releaseOrderReservations(orderId);
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException(OrderValues.CATALOG, failure);
        }
    }

    /** Takes the order's reserved stock out of the warehouse; refused when that stock is no longer there. */
    void dispatchOrder(UUID orderId) {
        try {
            catalog.dispatchOrder(orderId);
        } catch (HttpClientErrorException.Conflict conflict) {
            throw new DispatchRefusedException(catalogDetail(conflict));
        } catch (RestClientException failure) {
            throw new DependencyUnavailableException(OrderValues.CATALOG, failure);
        }
    }

    private static String catalogDetail(HttpClientErrorException.Conflict conflict) {
        return RemoteProblems.detailOf(conflict, HttpStatus.CONFLICT.getReasonPhrase());
    }
}
