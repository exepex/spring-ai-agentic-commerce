package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** A write-off larger than the units on hand: stock on hand never goes below zero. */
public class InvalidStockAdjustmentException extends CommerceException {

    public InvalidStockAdjustmentException(String sku, int onHand, int delta) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.INVALID_STOCK_ADJUSTMENT.formatted(sku, delta, onHand));
    }
}
