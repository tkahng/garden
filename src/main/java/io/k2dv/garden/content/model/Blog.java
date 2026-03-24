package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "content", name = "blogs")
@Getter
@Setter
public class Blog extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, unique = true)
    private String handle;
}
