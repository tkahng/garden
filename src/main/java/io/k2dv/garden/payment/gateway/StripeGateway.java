package io.k2dv.garden.payment.gateway;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

public interface StripeGateway {
    Session createCheckoutSession(SessionCreateParams params) throws StripeException;
    Session retrieveSession(String sessionId) throws StripeException;
    Event constructEvent(String payload, String sigHeader, String secret) throws SignatureVerificationException;
}
