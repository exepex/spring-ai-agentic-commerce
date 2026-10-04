package io.github.exepex.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PaymentGatewayConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentProperties.class)
    static class Payments {
    }

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Payments.class, PaymentGatewayConfiguration.class);

    @Test
    void stripeAnswersOrIsGivenUpOnBeforeTheCheckoutStopsWaitingForThisService() {
        contexts.withPropertyValues("commerce.payments.stripe-secret-key=sk_test_example").run(context -> {
            var properties = context.getBean(PaymentProperties.class);

            assertThat(context.getBean(PaymentGateway.class)).isInstanceOf(StripePaymentGateway.class);
            // order-service waits five seconds for this service: connecting and reading together must end before.
            assertThat(properties.stripeConnectTimeout().plus(properties.stripeReadTimeout()))
                    .isLessThan(Duration.ofSeconds(5));
        });
    }

    @Test
    void withoutAKeyPaymentsAreSimulated() {
        contexts.run(context -> assertThat(context.getBean(PaymentGateway.class))
                .isInstanceOf(SimulatedPaymentGateway.class));
    }
}
