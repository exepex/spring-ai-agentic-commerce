package io.github.exepex.commerce.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class InvalidStockAdjustmentException extends ErrorResponseException {

    InvalidStockAdjustmentException(String sku, int onHand, int delta) {
        super(HttpStatus.UNPROCESSABLE_CONTENT,
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Adjusting " + sku + " by " + delta + " would take its " + onHand + " units on hand below zero"),
                null);
    }
}
