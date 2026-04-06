package io.k2dv.garden.iam.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "auth", name = "permissions")
@Getter
@Setter
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private String action;
}
