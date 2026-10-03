package io.github.exepex.commerce.shipping.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The texts a parcel is recorded with: its tracking number, and what went wrong when the carrier did not say. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShippingValues {

    public static final String TRACKING_NUMBER_PREFIX = "AC";
    /** No 0/O or 1/I, which a customer reading the number out could confuse. */
    public static final String TRACKING_NUMBER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public static final String PARCEL_LOST = "The carrier lost the parcel";
    public static final String PARCEL_NOT_DELIVERED = "The carrier could not deliver the parcel";
}
