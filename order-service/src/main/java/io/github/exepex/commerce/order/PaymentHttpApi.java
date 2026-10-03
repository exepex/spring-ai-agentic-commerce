package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ApiPaths;
import io.github.exepex.commerce.order.dto.ChargeRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The payment-service endpoint this service calls. The base URL is {@code spring.http.serviceclient.payment}. */
@HttpExchange(ApiPaths.REMOTE_API)
interface PaymentHttpApi {

    @PostExchange(ApiPaths.PAYMENTS)
    void charge(@RequestBody ChargeRequest request);
}
