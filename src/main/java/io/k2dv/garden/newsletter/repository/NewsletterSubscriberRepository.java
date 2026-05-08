package io.k2dv.garden.newsletter.repository;

import io.k2dv.garden.newsletter.model.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, UUID> {
    boolean existsByEmail(String email);
    Optional<NewsletterSubscriber> findByEmail(String email);
}
