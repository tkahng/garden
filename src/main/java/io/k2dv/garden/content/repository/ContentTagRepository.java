package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ContentTagRepository extends JpaRepository<ContentTag, UUID> {
    Optional<ContentTag> findByName(String name);
}
