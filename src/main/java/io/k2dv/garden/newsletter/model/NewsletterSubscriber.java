package io.k2dv.garden.newsletter.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "marketing", name = "newsletter_subscribers")
@Getter
@Setter
public class NewsletterSubscriber {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    private String source;

    @Column(name = "subscribed_at", nullable = false, insertable = false, updatable = false)
    private Instant subscribedAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;
}
