package io.github.exepex.commerce.catalog;

import io.github.exepex.commerce.catalog.constants.ApiPaths;
import io.github.exepex.commerce.catalog.dto.AdjustStockRequest;
import io.github.exepex.commerce.catalog.dto.ProductView;
import io.github.exepex.commerce.catalog.dto.ReservationView;
import io.github.exepex.commerce.catalog.dto.ReserveStockRequest;
import io.github.exepex.commerce.catalog.exception.ProductNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class CatalogController {

    private final ProductRepository products;
    private final StockService stock;

    @GetMapping(ApiPaths.PRODUCTS)
    @Transactional(readOnly = true)
    List<ProductView> listProducts() {
        return products.findAllByOrderBySku().stream().map(CatalogMapper::toView).toList();
    }

    @GetMapping(ApiPaths.PRODUCT)
    @Transactional(readOnly = true)
    ProductView getProduct(@PathVariable UUID productId) {
        return products.findById(productId).map(CatalogMapper::toView)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @PostMapping(ApiPaths.PRODUCT_RESERVATIONS)
    @ResponseStatus(HttpStatus.CREATED)
    ReservationView reserveStock(@PathVariable UUID productId, @Valid @RequestBody ReserveStockRequest request) {
        return CatalogMapper.toView(stock.reserve(request.orderId(), productId, request.quantity()));
    }

    @DeleteMapping(ApiPaths.ORDER_RESERVATIONS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void releaseOrderReservations(@PathVariable UUID orderId) {
        stock.releaseOrder(orderId);
    }

    @PostMapping(ApiPaths.ORDER_DISPATCH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dispatchOrder(@PathVariable UUID orderId) {
        stock.dispatchOrder(orderId);
    }

    @PostMapping(ApiPaths.STOCK_ADJUSTMENTS)
    ProductView adjustStock(@PathVariable UUID productId, @Valid @RequestBody AdjustStockRequest request) {
        return CatalogMapper.toView(stock.adjustStock(productId, request.delta(), request.reason()));
    }
}
