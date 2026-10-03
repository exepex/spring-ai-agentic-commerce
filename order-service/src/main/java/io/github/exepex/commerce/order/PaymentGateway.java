package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import io.github.exepex.commerce.order.dto.ChargeRequest;
import io.github.exepex.commerce.order.exception.PaymentDeclinedException;
import io.github.exepex.commerce.order.exception.PaymentUnavailableException;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Calls the payment-service and turns its HTTP answers into order-domain outcomes: a declined card is a
 * {@link PaymentDeclinedException}, an unknown outcome a {@link PaymentUnavailableException}.
 */
@Component
@RequiredArgsConstructor
class PaymentGateway {

    private final PaymentHttpApi payments;

    void charge(UUID orderId, String customerEmail, BigDecimal amount, String currency, String paymentMethod) {
        try {
            payments.charge(new ChargeRequest(orderId, customerEmail, amount, currency, paymentMethod));
        } catch (HttpClientErrorException declined) {
            if (declined.getStatusCode().value() != 402) {
                throw new PaymentUnavailableException(declined);
            }
            throw new PaymentDeclinedException(RemoteProblems.detailOf(declined, ErrorMessages.CARD_DECLINED));
        } catch (RestClientException failure) {
            throw new PaymentUnavailableException(failure);
        }
    }
}
