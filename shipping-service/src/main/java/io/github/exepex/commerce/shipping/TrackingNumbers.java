package io.github.exepex.commerce.shipping;

import io.github.exepex.commerce.shipping.constants.ShippingValues;
import java.security.SecureRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Tracking numbers the customer can read out: "AC" and ten characters with no 0/O or 1/I to confuse. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TrackingNumbers {

    private static final SecureRandom RANDOM = new SecureRandom();

    static String next() {
        var trackingNumber = new StringBuilder(ShippingValues.TRACKING_NUMBER_PREFIX);
        for (var position = 0; position < 10; position++) {
            trackingNumber.append(ShippingValues.TRACKING_NUMBER_ALPHABET.charAt(
                    RANDOM.nextInt(ShippingValues.TRACKING_NUMBER_ALPHABET.length())));
        }
        return trackingNumber.toString();
    }
}
