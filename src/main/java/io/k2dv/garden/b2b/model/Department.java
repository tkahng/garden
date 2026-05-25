package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "departments")
@Getter
@Setter
public class Department extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false)
    private String name;
}
