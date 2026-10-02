package io.github.exepex.commerce.mcp.downstream;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/payments")
public interface PaymentApi {

    record Refund(UUID id, BigDecimal amount, String reason, String idempotencyKey, String providerReference,
            Instant createdAt) {}

    record Payment(UUID id, UUID orderId, BigDecimal amount, BigDecimal refundedAmount, BigDecimal refundable,
            String currency, String status, String provider, String providerReference, String failureMessage,
            List<Refund> refunds) {}

    record RefundRequest(BigDecimal amount, String reason, String idempotencyKey) {}

    @GetExchange("/{orderId}")
    Payment getPayment(@PathVariable UUID orderId);

    @PostExchange("/{orderId}/refunds")
    Refund refund(@PathVariable UUID orderId, @RequestBody RefundRequest request);
}
