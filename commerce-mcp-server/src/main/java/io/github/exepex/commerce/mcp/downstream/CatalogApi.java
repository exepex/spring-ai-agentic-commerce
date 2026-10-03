package io.github.exepex.commerce.mcp.downstream;

import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.downstream.dto.Product;
import java.util.List;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(DownstreamApis.CATALOG_API)
public interface CatalogApi {

    @GetExchange(DownstreamApis.PRODUCTS)
    List<Product> listProducts();
}
