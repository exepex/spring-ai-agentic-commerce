package io.github.exepex.commerce.mcp.downstream;

import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.downstream.dto.Shipment;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(DownstreamApis.SHIPMENTS)
public interface ShippingApi {

    @GetExchange(DownstreamApis.BY_ORDER_ID)
    Shipment getShipment(@PathVariable UUID orderId);
}
