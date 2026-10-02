package io.github.exepex.commerce.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class PaymentProviderUnavailableException extends ErrorResponseException {

    PaymentProviderUnavailableException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The card processor did not respond; try again"),
                cause);
    }
}
