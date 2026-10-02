package io.github.exepex.commerce.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PaymentGatewayConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentGatewayConfiguration.class);

    @Bean
    PaymentGateway paymentGateway(PaymentProperties properties) {
        if (properties.usesStripe()) {
            if (!properties.stripeSecretKey().startsWith("sk_test_")) {
                throw new IllegalStateException("This demo only accepts a Stripe test-mode key (sk_test_...)");
            }
            LOGGER.info("Payments go to Stripe");
            return new StripePaymentGateway(properties.stripeSecretKey());
        }
        LOGGER.info("No Stripe key configured: payments are simulated");
        return new SimulatedPaymentGateway();
    }
}
