package io.github.exepex.commerce.order;

public enum OrderStatus {
    /** Stock is reserved; payment has not completed yet. */
    PLACED,
    /** Paid; the shipment is being prepared. */
    CONFIRMED,
    /** The card was declined or the payment service could not be reached; the stock was released. */
    PAYMENT_FAILED,
    CANCELLED
}
