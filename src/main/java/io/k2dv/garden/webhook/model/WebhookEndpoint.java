package io.k2dv.garden.webhook.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "webhook", name = "endpoints")
@Getter
@Setter
public class WebhookEndpoint extends BaseEntity {

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String secret;

    @Column
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> events = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
