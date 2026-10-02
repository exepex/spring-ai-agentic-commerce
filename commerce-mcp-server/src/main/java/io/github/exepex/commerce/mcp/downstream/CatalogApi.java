package io.github.exepex.commerce.mcp.downstream;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api")
public interface CatalogApi {

    record Product(UUID id, String sku, String name, String description, BigDecimal price, String currency,
            int available) {}

    @GetExchange("/products")
    List<Product> listProducts();
}
