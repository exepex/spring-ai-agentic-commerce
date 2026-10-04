package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.ConfigKeys;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How payments reach the card processor.
 *
 * @param stripeSecretKey      a Stripe test-mode key, or empty for the simulator
 * @param stripeConnectTimeout how long to wait for a connection to Stripe
 * @param stripeReadTimeout    how long to wait for Stripe's answer once connected. Together with the connect timeout
 *                             it stays below the five seconds the checkout waits for this service, so a slow Stripe
 *                             never holds a payment's lock and connection after the caller left
 */
@ConfigurationProperties(ConfigKeys.PAYMENTS_PREFIX)
record PaymentProperties(
        String stripeSecretKey,
        @DefaultValue("1s") Duration stripeConnectTimeout,
        @DefaultValue("3s") Duration stripeReadTimeout) {

    boolean usesStripe() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }
}
