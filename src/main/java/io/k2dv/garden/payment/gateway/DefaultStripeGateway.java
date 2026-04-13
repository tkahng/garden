package io.k2dv.garden.payment.gateway;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import io.k2dv.garden.payment.config.StripeProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultStripeGateway implements StripeGateway {

    private final StripeProperties stripeProperties;

    @PostConstruct
    void initApiKey() {
        Stripe.apiKey = stripeProperties.getSecretKey();
    }

    @Override
    public Session createCheckoutSession(SessionCreateParams params) throws StripeException {
        return Session.create(params);
    }

    @Override
    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    @Override
    public Event constructEvent(String payload, String sigHeader, String secret)
            throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, secret);
    }

    @Override
    public Refund createRefund(RefundCreateParams params) throws StripeException {
        return Refund.create(params);
    }
}
