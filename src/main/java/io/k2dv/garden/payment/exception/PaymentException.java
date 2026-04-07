package io.k2dv.garden.payment.exception;

import io.k2dv.garden.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PaymentException extends DomainException {
    public PaymentException(String code, String message) {
        super(code, message, HttpStatus.BAD_GATEWAY);
    }
}
