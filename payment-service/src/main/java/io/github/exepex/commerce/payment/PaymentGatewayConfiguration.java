package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.PaymentValues;
import io.github.exepex.commerce.payment.exception.UnsupportedStripeKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Charges through Stripe when a test-mode key is configured, and through the simulator otherwise. */
@Slf4j
@Configuration(proxyBeanMethods = false)
class PaymentGatewayConfiguration {

    @Bean
    PaymentGateway paymentGateway(PaymentProperties properties) {
        if (properties.usesStripe()) {
            if (!properties.stripeSecretKey().startsWith(PaymentValues.STRIPE_TEST_KEY_PREFIX)) {
                throw new UnsupportedStripeKeyException();
            }
            log.info("Payments go to Stripe");
            return new StripePaymentGateway(properties.stripeSecretKey());
        }
        log.info("No Stripe key configured: payments are simulated");
        return new SimulatedPaymentGateway();
    }
}
