package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.ConfigKeys;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(ConfigKeys.PAYMENTS_PREFIX)
record PaymentProperties(String stripeSecretKey) {

    boolean usesStripe() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }
}
