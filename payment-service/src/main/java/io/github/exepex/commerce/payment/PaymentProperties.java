package io.github.exepex.commerce.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("commerce.payments")
record PaymentProperties(String stripeSecretKey) {

    boolean usesStripe() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }
}
