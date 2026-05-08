package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.CompanyProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompanyProductCatalogRepository extends JpaRepository<CompanyProductCatalog, UUID> {

    List<CompanyProductCatalog> findByCompanyId(UUID companyId);

    boolean existsByProductId(UUID productId);

    boolean existsByCompanyIdAndProductId(UUID companyId, UUID productId);

    void deleteByCompanyIdAndProductId(UUID companyId, UUID productId);

    @Query("SELECT c.productId FROM CompanyProductCatalog c WHERE c.companyId = :companyId")
    List<UUID> findProductIdsByCompanyId(@Param("companyId") UUID companyId);
}
