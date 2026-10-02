package io.github.exepex.commerce.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class InsufficientStockException extends ErrorResponseException {

    InsufficientStockException(String sku, int requested, int available) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "Requested " + requested + " of " + sku + " but only " + available + " available"),
                null);
    }
}
