package io.k2dv.garden.blob.repository;

import io.k2dv.garden.blob.model.BlobObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BlobObjectRepository extends JpaRepository<BlobObject, UUID>, JpaSpecificationExecutor<BlobObject> {

    @Query("SELECT DISTINCT b.folder FROM BlobObject b WHERE b.folder IS NOT NULL ORDER BY b.folder")
    List<String> findDistinctFolders();

    @Query("SELECT COUNT(b), COALESCE(SUM(b.size), 0) FROM BlobObject b")
    Object[] findStats();
}
