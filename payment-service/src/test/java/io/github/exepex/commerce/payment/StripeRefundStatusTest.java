package io.github.exepex.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exepex.commerce.payment.PaymentGateway.RefundStatus;
import org.junit.jupiter.api.Test;

class StripeRefundStatusTest {

    @Test
    void aFailedOrCancelledRefundDoesNotCountAsRefunded() {
        assertThat(StripePaymentGateway.statusOf("failed")).isEqualTo(RefundStatus.FAILED);
        assertThat(StripePaymentGateway.statusOf("canceled")).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void aRefundThatIsNotFinalYetIsPending() {
        assertThat(StripePaymentGateway.statusOf("pending")).isEqualTo(RefundStatus.PENDING);
        assertThat(StripePaymentGateway.statusOf("requires_action")).isEqualTo(RefundStatus.PENDING);
        assertThat(StripePaymentGateway.statusOf("succeeded")).isEqualTo(RefundStatus.SUCCEEDED);
    }
}
