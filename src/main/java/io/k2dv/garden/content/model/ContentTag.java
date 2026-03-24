package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "content", name = "content_tags")
@Getter
@Setter
public class ContentTag extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;
}
