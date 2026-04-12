package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
}
