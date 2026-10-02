package io.github.exepex.commerce.order;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The payment-service endpoint this service calls. The base URL is {@code spring.http.serviceclient.payment}. */
@HttpExchange("/api")
interface PaymentHttpApi {

    record ChargeRequest(UUID orderId, String customerEmail, BigDecimal amount, String currency, String paymentMethod) {}

    @PostExchange("/payments")
    void charge(@RequestBody ChargeRequest request);
}
