package io.github.exepex.commerce.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.ErrorResponseException;

class StripeRefundStatusTest {

    @Test
    void aFailedOrCancelledRefundDoesNotCountAsRefunded() {
        assertThatThrownBy(() -> StripePaymentGateway.ensureAccepted("failed")).isInstanceOf(ErrorResponseException.class);
        assertThatThrownBy(() -> StripePaymentGateway.ensureAccepted("canceled")).isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void aSucceededOrPendingRefundIsAccepted() {
        assertThatCode(() -> StripePaymentGateway.ensureAccepted("succeeded")).doesNotThrowAnyException();
        assertThatCode(() -> StripePaymentGateway.ensureAccepted("pending")).doesNotThrowAnyException();
    }
}
