package io.github.exepex.commerce.payment;

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
            if (!properties.stripeSecretKey().startsWith("sk_test_")) {
                throw new IllegalStateException("This demo only accepts a Stripe test-mode key (sk_test_...)");
            }
            log.info("Payments go to Stripe");
            return new StripePaymentGateway(properties.stripeSecretKey());
        }
        log.info("No Stripe key configured: payments are simulated");
        return new SimulatedPaymentGateway();
    }
}
