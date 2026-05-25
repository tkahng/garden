package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByCompanyId(UUID companyId);
    List<Department> findByCompanyIdAndParentIdIsNull(UUID companyId);
    List<Department> findByParentId(UUID parentId);
    boolean existsByCompanyIdAndName(UUID companyId, String name);
}
