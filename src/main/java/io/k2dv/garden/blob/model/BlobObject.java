package io.k2dv.garden.blob.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "blob_objects")
@Getter
@Setter
public class BlobObject extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;
}
