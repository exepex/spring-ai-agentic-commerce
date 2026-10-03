package io.github.exepex.commerce.order;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/** Calls the payment-service and turns its HTTP answers into order-domain outcomes. */
@Component
@RequiredArgsConstructor
class PaymentGateway {

    /** The card was declined; {@code reason} is the processor's message, safe to show the customer. */
    static class PaymentDeclinedException extends RuntimeException {

        PaymentDeclinedException(String reason) {
            super(reason);
        }
    }

    /** The payment's outcome is not known: it may or may not have been taken. */
    static class PaymentUnavailableException extends RuntimeException {

        PaymentUnavailableException(Throwable cause) {
            super("The payment service is unavailable", cause);
        }
    }

    private final PaymentHttpApi payments;

    void charge(UUID orderId, String customerEmail, BigDecimal amount, String currency, String paymentMethod) {
        try {
            payments.charge(new PaymentHttpApi.ChargeRequest(orderId, customerEmail, amount, currency, paymentMethod));
        } catch (HttpClientErrorException declined) {
            if (declined.getStatusCode().value() != 402) {
                throw new PaymentUnavailableException(declined);
            }
            throw new PaymentDeclinedException(RemoteProblems.detailOf(declined, "The card was declined"));
        } catch (RestClientException failure) {
            throw new PaymentUnavailableException(failure);
        }
    }
}
