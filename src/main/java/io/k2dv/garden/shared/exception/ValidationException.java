package io.k2dv.garden.shared.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends DomainException {

    public ValidationException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
}
