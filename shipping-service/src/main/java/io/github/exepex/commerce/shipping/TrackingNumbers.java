package io.github.exepex.commerce.shipping;

import java.security.SecureRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Tracking numbers the customer can read out: "AC" and ten characters with no 0/O or 1/I to confuse. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TrackingNumbers {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    static String next() {
        StringBuilder trackingNumber = new StringBuilder("AC");
        for (int position = 0; position < 10; position++) {
            trackingNumber.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return trackingNumber.toString();
    }
}
