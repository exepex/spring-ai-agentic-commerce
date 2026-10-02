package io.github.exepex.commerce.order;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class OrderNotFoundException extends ErrorResponseException {

    OrderNotFoundException(UUID orderId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Order " + orderId + " does not exist"),
                null);
    }
}
