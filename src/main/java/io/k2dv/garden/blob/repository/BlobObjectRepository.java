package io.k2dv.garden.blob.repository;

import io.k2dv.garden.blob.model.BlobObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlobObjectRepository extends JpaRepository<BlobObject, UUID> {
}
