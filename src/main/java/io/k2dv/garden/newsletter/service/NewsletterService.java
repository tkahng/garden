package io.k2dv.garden.newsletter.service;

import io.k2dv.garden.newsletter.dto.SubscribeRequest;
import io.k2dv.garden.newsletter.dto.SubscribeResponse;
import io.k2dv.garden.newsletter.model.NewsletterSubscriber;
import io.k2dv.garden.newsletter.repository.NewsletterSubscriberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles newsletter subscription sign-ups, normalising email addresses and
 * enforcing idempotency so that re-subscribing an already-subscribed address
 * is a no-op rather than an error.
 */
@Service
@RequiredArgsConstructor
public class NewsletterService {

    private final NewsletterSubscriberRepository repo;

    /**
     * Subscribes an email address to the newsletter, creating a new subscriber record if
     * this email has not been seen before. The response indicates whether the address was
     * already subscribed, which callers can use to tailor confirmation messaging.
     */
    @Transactional
    public SubscribeResponse subscribe(SubscribeRequest req) {
        String email = req.email().trim().toLowerCase();

        if (repo.existsByEmail(email)) {
            return new SubscribeResponse(true);
        }

        NewsletterSubscriber subscriber = new NewsletterSubscriber();
        subscriber.setEmail(email);
        subscriber.setSource(req.source());
        repo.save(subscriber);

        return new SubscribeResponse(false);
    }
}
