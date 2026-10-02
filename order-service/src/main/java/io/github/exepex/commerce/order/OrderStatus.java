package io.github.exepex.commerce.order;

public enum OrderStatus {
    /** Stock is reserved; checkout is taking the payment. */
    PLACED,
    /**
     * The payment's outcome is not known yet, because the payment service could not be reached or did not answer.
     * The stock stays reserved, and the payment is asked for again until it succeeds or is declined.
     */
    PAYMENT_PENDING,
    /** Paid; the shipment is being prepared. */
    CONFIRMED,
    /** The card was declined; the stock is released. */
    PAYMENT_FAILED,
    CANCELLED,
    /** The parcel left the warehouse with the carrier. From here on the order can no longer be cancelled. */
    SHIPPED,
    /** The carrier delivered the parcel. */
    DELIVERED,
    /** The carrier could not deliver the parcel. */
    DELIVERY_FAILED,
    /** The carrier lost the parcel. */
    LOST
}
