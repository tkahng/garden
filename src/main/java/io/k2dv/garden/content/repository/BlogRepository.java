package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.Blog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface BlogRepository extends JpaRepository<Blog, UUID>,
        JpaSpecificationExecutor<Blog> {
    Optional<Blog> findByHandle(String handle);
    boolean existsByHandle(String handle);
    boolean existsByHandleAndIdNot(String handle, UUID id);
}
