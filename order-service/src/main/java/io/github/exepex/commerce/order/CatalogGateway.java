package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.CatalogHttpApi.CatalogProduct;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/** Calls the catalog and turns its HTTP failures into order-domain errors. */
@Component
class CatalogGateway {

    private final CatalogHttpApi catalog;

    CatalogGateway(CatalogHttpApi catalog) {
        this.catalog = catalog;
    }

    CatalogProduct getProduct(UUID productId) {
        try {
            return catalog.getProduct(productId);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw OrderRejectedException.unknownProduct(productId);
        } catch (RestClientException failure) {
            throw new CatalogUnavailableException(failure);
        }
    }

    void reserveStock(UUID productId, UUID orderId, int quantity) {
        try {
            catalog.reserveStock(productId, new CatalogHttpApi.ReserveStockRequest(orderId, quantity));
        } catch (HttpClientErrorException.Conflict conflict) {
            throw OrderRejectedException.stockUnavailable(catalogDetail(conflict));
        } catch (RestClientException failure) {
            throw new CatalogUnavailableException(failure);
        }
    }

    void releaseOrderReservations(UUID orderId) {
        try {
            catalog.releaseOrderReservations(orderId);
        } catch (RestClientException failure) {
            throw new CatalogUnavailableException(failure);
        }
    }

    private static String catalogDetail(HttpClientErrorException failure) {
        ProblemDetail problem = failure.getResponseBodyAs(ProblemDetail.class);
        return problem != null && problem.getDetail() != null
                ? problem.getDetail()
                : HttpStatus.CONFLICT.getReasonPhrase();
    }
}
