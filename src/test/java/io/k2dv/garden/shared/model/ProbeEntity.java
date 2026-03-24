package io.k2dv.garden.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "shared", name = "probe")
@Getter
@Setter
public class ProbeEntity extends BaseEntity {

    @Column(nullable = false)
    private String label;
}
