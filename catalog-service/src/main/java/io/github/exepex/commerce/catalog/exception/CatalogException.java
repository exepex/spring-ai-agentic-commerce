package io.github.exepex.commerce.catalog.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Something the catalog was asked to do cannot be done; the status says how a caller should take it. */
@Getter
public abstract class CatalogException extends RuntimeException {

    private final HttpStatus status;

    protected CatalogException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
