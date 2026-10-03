package io.github.exepex.commerce.mcp.downstream;

import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.downstream.dto.Payment;
import io.github.exepex.commerce.mcp.downstream.dto.Refund;
import io.github.exepex.commerce.mcp.downstream.dto.RefundRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(DownstreamApis.PAYMENTS)
public interface PaymentApi {

    @GetExchange(DownstreamApis.BY_ORDER_ID)
    Payment getPayment(@PathVariable UUID orderId);

    @PostExchange(DownstreamApis.REFUNDS)
    Refund refund(@PathVariable UUID orderId, @RequestBody RefundRequest request);
}
