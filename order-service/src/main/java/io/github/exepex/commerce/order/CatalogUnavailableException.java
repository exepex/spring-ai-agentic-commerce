package io.github.exepex.commerce.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class CatalogUnavailableException extends ErrorResponseException {

    CatalogUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The catalog is unavailable; try again"),
                cause);
    }
}
