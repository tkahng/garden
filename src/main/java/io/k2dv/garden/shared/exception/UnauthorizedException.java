package io.k2dv.garden.shared.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNAUTHORIZED);
    }
}
