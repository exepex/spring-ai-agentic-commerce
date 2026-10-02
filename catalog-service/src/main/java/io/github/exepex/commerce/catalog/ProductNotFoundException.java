package io.github.exepex.commerce.catalog;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class ProductNotFoundException extends ErrorResponseException {

    ProductNotFoundException(UUID productId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Product " + productId + " does not exist"),
                null);
    }
}
