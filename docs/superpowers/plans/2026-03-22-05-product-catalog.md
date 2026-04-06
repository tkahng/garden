# Product Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a full product catalog domain with admin CRUD and storefront read endpoints, including products, variants, options, images, tags, and inventory stubs.

**Architecture:** Package-by-feature under `io.k2dv.garden.product`. Services are injected into controllers; no domain logic in controllers. Soft deletes via `deletedAt` on products and variants. Cursor pagination (ascending by `id`) for list endpoints.

**Tech Stack:** Spring Boot 4.x, Spring Data JPA, Hibernate 6, Lombok, AssertJ (tests), MockMvc (slice tests), PostgreSQL (Testcontainers for IT).

---

## File Map

**New files — main:**
- `src/main/resources/db/migration/V9__create_products.sql`
- `src/main/java/io/k2dv/garden/product/model/ProductStatus.java`
- `src/main/java/io/k2dv/garden/product/model/Product.java`
- `src/main/java/io/k2dv/garden/product/model/ProductImage.java`
- `src/main/java/io/k2dv/garden/product/model/ProductOption.java`
- `src/main/java/io/k2dv/garden/product/model/ProductOptionValue.java`
- `src/main/java/io/k2dv/garden/product/model/ProductVariant.java`
- `src/main/java/io/k2dv/garden/product/model/ProductTag.java`
- `src/main/java/io/k2dv/garden/product/model/InventoryItem.java`

> **Note:** `VariantOptionValue` is NOT a separate entity class. The `variant_option_values` join table is managed entirely via the `@ManyToMany` relationship on `ProductVariant`. Do NOT create a `VariantOptionValue.java` entity or `VariantOptionValueRepository.java`.
- `src/main/java/io/k2dv/garden/product/repository/ProductRepository.java`
- `src/main/java/io/k2dv/garden/product/repository/ProductImageRepository.java`
- `src/main/java/io/k2dv/garden/product/repository/ProductOptionRepository.java`
- `src/main/java/io/k2dv/garden/product/repository/ProductOptionValueRepository.java`
- `src/main/java/io/k2dv/garden/product/repository/ProductVariantRepository.java`
- `src/main/java/io/k2dv/garden/product/repository/ProductTagRepository.java`
- `src/main/java/io/k2dv/garden/product/repository/InventoryItemRepository.java`
- `src/main/java/io/k2dv/garden/product/dto/CreateProductRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/UpdateProductRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/ProductStatusRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/CreateVariantRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/UpdateVariantRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/CreateOptionRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/RenameOptionRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/CreateOptionValueRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/RenameOptionValueRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/CreateImageRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/ImagePositionItem.java`
- `src/main/java/io/k2dv/garden/product/dto/UpdateInventoryRequest.java`
- `src/main/java/io/k2dv/garden/product/dto/OptionValueLabel.java`
- `src/main/java/io/k2dv/garden/product/dto/ProductVariantResponse.java`
- `src/main/java/io/k2dv/garden/product/dto/AdminVariantResponse.java`
- `src/main/java/io/k2dv/garden/product/dto/ProductImageResponse.java`
- `src/main/java/io/k2dv/garden/product/dto/ProductSummaryResponse.java`
- `src/main/java/io/k2dv/garden/product/dto/ProductDetailResponse.java`
- `src/main/java/io/k2dv/garden/product/dto/AdminProductResponse.java`
- `src/main/java/io/k2dv/garden/product/dto/InventoryItemResponse.java`
- `src/main/java/io/k2dv/garden/product/service/ProductService.java`
- `src/main/java/io/k2dv/garden/product/service/VariantService.java`
- `src/main/java/io/k2dv/garden/product/service/OptionService.java`
- `src/main/java/io/k2dv/garden/product/service/ProductImageService.java`
- `src/main/java/io/k2dv/garden/product/controller/AdminProductController.java`
- `src/main/java/io/k2dv/garden/product/controller/AdminInventoryController.java`
- `src/main/java/io/k2dv/garden/product/controller/StorefrontProductController.java`

**New files — test:**
- `src/test/java/io/k2dv/garden/product/controller/AdminProductControllerTest.java`
- `src/test/java/io/k2dv/garden/product/controller/StorefrontProductControllerTest.java`
- `src/test/java/io/k2dv/garden/product/service/ProductServiceIT.java`
- `src/test/java/io/k2dv/garden/product/service/ProductImageServiceIT.java`

---

### Task 1: Flyway Migration

**Files:**
- Create: `src/main/resources/db/migration/V9__create_products.sql`

- [ ] **Step 1: Create the migration file**

```sql
CREATE TABLE products (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title             TEXT        NOT NULL,
    description       TEXT,
    handle            TEXT        NOT NULL UNIQUE,
    vendor            TEXT,
    product_type      TEXT,
    status            TEXT        NOT NULL DEFAULT 'DRAFT',
    featured_image_id UUID,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_images (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES products(id),
    blob_id    UUID        NOT NULL REFERENCES blob_objects(id),
    alt_text   TEXT,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_images
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_options (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES products(id),
    name       TEXT        NOT NULL,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_options
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_option_values (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    option_id  UUID        NOT NULL REFERENCES product_options(id),
    label      TEXT        NOT NULL,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_option_values
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_variants (
    id               UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id       UUID           NOT NULL REFERENCES products(id),
    title            TEXT           NOT NULL,
    sku              TEXT           UNIQUE,
    barcode          TEXT,
    price            NUMERIC(19,4)  NOT NULL,
    compare_at_price NUMERIC(19,4),
    weight           NUMERIC(10,4),
    weight_unit      TEXT,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE variant_option_values (
    variant_id      UUID NOT NULL REFERENCES product_variants(id),
    option_value_id UUID NOT NULL REFERENCES product_option_values(id),
    PRIMARY KEY (variant_id, option_value_id)
);

CREATE TABLE product_tags (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_tags
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_product_tags (
    product_id UUID NOT NULL REFERENCES products(id),
    tag_id     UUID NOT NULL REFERENCES product_tags(id),
    PRIMARY KEY (product_id, tag_id)
);

CREATE TABLE inventory_items (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    variant_id        UUID        NOT NULL UNIQUE REFERENCES product_variants(id),
    requires_shipping BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

- [ ] **Step 2: Run full test suite to verify migration applies cleanly**

```bash
./mvnw test -q
```

Expected: all existing tests pass (migration runs, schema is created).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V9__create_products.sql
git commit -m "feat(product): add V9 product catalog migration"
```

---

### Task 2: JPA Entities and Repositories

**Files:** all entity and repository files listed in the file map above.

- [ ] **Step 1: Create `ProductStatus` enum**

```java
package io.k2dv.garden.product.model;

public enum ProductStatus {
    DRAFT, ACTIVE, ARCHIVED
}
```

- [ ] **Step 2: Create `Product` entity**

```java
package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true)
    private String handle;

    private String vendor;

    @Column(name = "product_type")
    private String productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "featured_image_id")
    private UUID featuredImageId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_product_tags",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<ProductTag> tags = new LinkedHashSet<>();
}
```

- [ ] **Step 3: Create `ProductImage` entity**

```java
package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "product_images")
@Getter
@Setter
public class ProductImage extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "blob_id", nullable = false)
    private UUID blobId;

    @Column(name = "alt_text")
    private String altText;

    @Column(nullable = false)
    private int position;
}
```

- [ ] **Step 4: Create `ProductOption` entity**

```java
package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "product_options")
@Getter
@Setter
public class ProductOption extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int position;
}
```

- [ ] **Step 5: Create `ProductOptionValue` entity**

```java
package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "product_option_values")
@Getter
@Setter
public class ProductOptionValue extends BaseEntity {

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int position;
}
```

- [ ] **Step 6: Create `ProductVariant` entity**

```java
package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
public class ProductVariant extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String title;

    @Column(unique = true)
    private String sku;

    private String barcode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 19, scale = 4)
    private BigDecimal compareAtPrice;

    @Column(precision = 10, scale = 4)
    private BigDecimal weight;

    @Column(name = "weight_unit")
    private String weightUnit;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "variant_option_values",
        joinColumns = @JoinColumn(name = "variant_id"),
        inverseJoinColumns = @JoinColumn(name = "option_value_id")
    )
    private List<ProductOptionValue> optionValues = new ArrayList<>();
}
```

- [ ] **Step 7: Create `ProductTag` entity**

```java
package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_tags")
@Getter
@Setter
public class ProductTag extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
}
```

- [ ] **Step 8: Create `InventoryItem` entity**

```java
package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
public class InventoryItem extends BaseEntity {

    @Column(name = "variant_id", nullable = false, unique = true)
    private UUID variantId;

    @Column(name = "requires_shipping", nullable = false)
    private boolean requiresShipping = true;
}
```

- [ ] **Step 9: Create all repositories**

```java
// ProductRepository.java
package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByHandleAndDeletedAtIsNull(String handle);

    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);

    Optional<Product> findByHandle(String handle);

    // Admin list: optional status filter, cursor-based
    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL " +
           "AND (:status IS NULL OR p.status = :status) " +
           "ORDER BY p.id ASC")
    List<Product> findForAdminList(@Param("status") ProductStatus status, Limit limit);

    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND p.id > :cursor ORDER BY p.id ASC")
    List<Product> findForAdminListAfterCursor(@Param("status") ProductStatus status,
                                               @Param("cursor") UUID cursor,
                                               Limit limit);

    // Storefront: ACTIVE only
    List<Product> findByStatusAndDeletedAtIsNullOrderByIdAsc(ProductStatus status, Limit limit);

    List<Product> findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
        ProductStatus status, UUID cursor, Limit limit);
}
```

```java
// ProductImageRepository.java
package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findByProductIdOrderByPositionAsc(UUID productId);
    int countByProductId(UUID productId);
}
```

```java
// ProductOptionRepository.java
package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductOptionRepository extends JpaRepository<ProductOption, UUID> {
    List<ProductOption> findByProductIdOrderByPositionAsc(UUID productId);
}
```

```java
// ProductOptionValueRepository.java
package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, UUID> {
    List<ProductOptionValue> findByOptionIdOrderByPositionAsc(UUID optionId);
}
```

```java
// ProductVariantRepository.java
package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID productId);

    Optional<ProductVariant> findByIdAndDeletedAtIsNull(UUID id);

    // Find all non-deleted variants linked to a given option value (for title recomputation)
    @Query("SELECT v FROM ProductVariant v JOIN v.optionValues ov " +
           "WHERE ov.id = :optionValueId AND v.deletedAt IS NULL")
    List<ProductVariant> findByOptionValueIdAndDeletedAtIsNull(@Param("optionValueId") UUID optionValueId);
}
```

```java
// ProductTagRepository.java
package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductTagRepository extends JpaRepository<ProductTag, UUID> {
    Optional<ProductTag> findByName(String name);
}
```

```java
// InventoryItemRepository.java
package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByVariantId(UUID variantId);
    List<InventoryItem> findByVariantIdIn(List<UUID> variantIds);
}
```

- [ ] **Step 10: Run tests to confirm all entities map correctly**

```bash
./mvnw test -q
```

Expected: all tests pass (Hibernate validates schema against entities).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/
git commit -m "feat(product): add JPA entities and repositories"
```

---

### Task 3: Request/Response DTOs

**Files:** all dto files listed in the file map above.

- [ ] **Step 1: Create request DTOs**

```java
// CreateProductRequest.java
package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateProductRequest(
    @NotBlank String title,
    String description,
    String handle,
    String vendor,
    String productType,
    List<String> tags
) {}
```

```java
// UpdateProductRequest.java
package io.k2dv.garden.product.dto;

import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
    String title,
    String description,
    String handle,
    String vendor,
    String productType,
    UUID featuredImageId,
    List<String> tags
) {}
```

```java
// ProductStatusRequest.java
package io.k2dv.garden.product.dto;

import io.k2dv.garden.product.model.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull ProductStatus status) {}
```

```java
// CreateVariantRequest.java
package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateVariantRequest(
    @NotNull BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    String barcode,
    BigDecimal weight,
    String weightUnit,
    List<UUID> optionValueIds
) {}
```

```java
// UpdateVariantRequest.java
package io.k2dv.garden.product.dto;

import java.math.BigDecimal;

public record UpdateVariantRequest(
    BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    String barcode,
    BigDecimal weight,
    String weightUnit
) {}
```

```java
// CreateOptionRequest.java
package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOptionRequest(@NotBlank String name, int position) {}
```

```java
// RenameOptionRequest.java
package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameOptionRequest(@NotBlank String name) {}
```

```java
// CreateOptionValueRequest.java
package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOptionValueRequest(@NotBlank String label, int position) {}
```

```java
// RenameOptionValueRequest.java
package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameOptionValueRequest(@NotBlank String label) {}
```

```java
// CreateImageRequest.java
package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateImageRequest(@NotNull UUID blobId, String altText) {}
```

```java
// ImagePositionItem.java
package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ImagePositionItem(UUID id, int position) {}
```

```java
// UpdateInventoryRequest.java
package io.k2dv.garden.product.dto;

public record UpdateInventoryRequest(boolean requiresShipping) {}
```

- [ ] **Step 2: Create response DTOs**

```java
// OptionValueLabel.java
package io.k2dv.garden.product.dto;

public record OptionValueLabel(String optionName, String valueLabel) {}
```

```java
// ProductVariantResponse.java
package io.k2dv.garden.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductVariantResponse(
    UUID id,
    String title,
    String sku,
    BigDecimal price,
    BigDecimal compareAtPrice,
    List<OptionValueLabel> optionValues
) {}
```

```java
// AdminVariantResponse.java
package io.k2dv.garden.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminVariantResponse(
    UUID id,
    String title,
    String sku,
    String barcode,
    BigDecimal price,
    BigDecimal compareAtPrice,
    BigDecimal weight,
    String weightUnit,
    List<OptionValueLabel> optionValues,
    Instant deletedAt
) {}
```

```java
// ProductImageResponse.java
package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ProductImageResponse(UUID id, String url, String altText, int position) {}
```

```java
// ProductSummaryResponse.java
package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ProductSummaryResponse(UUID id, String title, String handle, String vendor) {}
```

```java
// ProductDetailResponse.java
package io.k2dv.garden.product.dto;

import java.util.List;
import java.util.UUID;

public record ProductDetailResponse(
    UUID id,
    String title,
    String description,
    String handle,
    String vendor,
    String productType,
    List<ProductVariantResponse> variants,
    List<ProductImageResponse> images,
    List<String> tags
) {}
```

```java
// AdminProductResponse.java
package io.k2dv.garden.product.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProductResponse(
    UUID id,
    String title,
    String description,
    String handle,
    String vendor,
    String productType,
    String status,
    UUID featuredImageId,
    List<AdminVariantResponse> variants,
    List<ProductImageResponse> images,
    List<String> tags,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {}
```

```java
// InventoryItemResponse.java
package io.k2dv.garden.product.dto;

import java.util.UUID;

public record InventoryItemResponse(UUID id, UUID variantId, boolean requiresShipping) {}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/dto/
git commit -m "feat(product): add request and response DTOs"
```

---

### Task 4: ProductService

**Files:**
- Create: `src/main/java/io/k2dv/garden/product/service/ProductService.java`

`ProductService` handles: create (with handle slugification and uniqueness check), admin list, admin get, update fields, status change, and soft delete. Tag management (find-or-create) is done inside this service.

- [ ] **Step 1: Write the failing integration test stubs**

Create `src/test/java/io/k2dv/garden/product/service/ProductServiceIT.java`:

```java
package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceIT extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepo;

    @Test
    void createProduct_persistsWithDraftStatusAndAutoHandle() {
        var req = new CreateProductRequest("My Product!", null, null, null, null, List.of());
        var resp = productService.create(req);

        assertThat(resp.title()).isEqualTo("My Product!");
        assertThat(resp.handle()).isEqualTo("my-product");
        assertThat(resp.status()).isEqualTo("DRAFT");
        assertThat(productRepo.findByIdAndDeletedAtIsNull(resp.id())).isPresent();
    }

    @Test
    void createProduct_withExplicitHandle_usesProvidedHandle() {
        var req = new CreateProductRequest("T-Shirt", null, "custom-tee", null, null, List.of());
        var resp = productService.create(req);
        assertThat(resp.handle()).isEqualTo("custom-tee");
    }

    @Test
    void createProduct_duplicateHandle_throwsValidationException() {
        productService.create(new CreateProductRequest("First", null, null, null, null, List.of()));
        assertThatThrownBy(() ->
            productService.create(new CreateProductRequest("First", null, null, null, null, List.of()))
        ).isInstanceOf(ValidationException.class);
    }

    @Test
    void softDeleteProduct_excludedFromListQueries() {
        var resp = productService.create(new CreateProductRequest("Gone", null, null, null, null, List.of()));
        productService.softDelete(resp.id());
        assertThat(productRepo.findByIdAndDeletedAtIsNull(resp.id())).isEmpty();
    }

    @Test
    void changeStatus_updatesProductStatus() {
        var resp = productService.create(new CreateProductRequest("T-Shirt", null, null, null, null, List.of()));
        productService.changeStatus(resp.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        var updated = productService.getAdmin(resp.id());
        assertThat(updated.status()).isEqualTo("ACTIVE");
    }

    @Test
    void storefrontList_returnsOnlyActiveProducts() {
        // Create one DRAFT (default) and one ACTIVE product
        productService.create(new CreateProductRequest("Draft Product", null, null, null, null, List.of()));
        var active = productService.create(new CreateProductRequest("Active Product", null, null, null, null, List.of()));
        productService.changeStatus(active.id(), new ProductStatusRequest(ProductStatus.ACTIVE));

        var result = productService.listStorefront(null);
        @SuppressWarnings("unchecked")
        var items = (java.util.List<?>) result.get("items");
        assertThat(items).hasSize(1);
        assertThat(((io.k2dv.garden.product.dto.ProductSummaryResponse) items.get(0)).title())
            .isEqualTo("Active Product");
    }
}
```

- [ ] **Step 2: Run the test — expect compilation failure (ProductService not yet created)**

```bash
./mvnw test -Dtest=ProductServiceIT -q 2>&1 | tail -20
```

Expected: compilation error — `ProductService` does not exist.

- [ ] **Step 3: Implement `ProductService`**

```java
package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.*;
import io.k2dv.garden.product.repository.*;
import io.k2dv.garden.shared.dto.CursorMeta;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int PAGE_SIZE = 20;

    private final ProductRepository productRepo;
    private final ProductTagRepository tagRepo;
    private final ProductImageRepository imageRepo;
    private final ProductOptionRepository optionRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryItemRepository inventoryRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    @Transactional
    public AdminProductResponse create(CreateProductRequest req) {
        String handle = req.handle() != null ? req.handle() : slugify(req.title());
        checkHandleUnique(handle, null);

        Product p = new Product();
        p.setTitle(req.title());
        p.setDescription(req.description());
        p.setHandle(handle);
        p.setVendor(req.vendor());
        p.setProductType(req.productType());
        p.setStatus(ProductStatus.DRAFT);
        if (req.tags() != null) {
            req.tags().forEach(name -> p.getTags().add(findOrCreateTag(name)));
        }
        p = productRepo.save(p);
        return toAdminResponse(p);
    }

    @Transactional(readOnly = true)
    public AdminProductResponse getAdmin(UUID id) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return toAdminResponse(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAdmin(ProductStatus status, String cursor) {
        UUID cursorId = cursor != null ? UUID.fromString(cursor) : null;
        int limit = PAGE_SIZE + 1;
        List<Product> raw = cursorId == null
            ? productRepo.findForAdminList(status, Limit.of(limit))
            : productRepo.findForAdminListAfterCursor(status, cursorId, Limit.of(limit));

        boolean hasMore = raw.size() > PAGE_SIZE;
        List<Product> page = hasMore ? raw.subList(0, PAGE_SIZE) : raw;
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;

        List<AdminProductResponse> items = page.stream().map(this::toAdminResponse).toList();
        CursorMeta meta = CursorMeta.builder()
            .nextCursor(nextCursor).hasMore(hasMore).pageSize(PAGE_SIZE).build();
        return Map.of("items", items, "meta", meta);
    }

    @Transactional
    public AdminProductResponse update(UUID id, UpdateProductRequest req) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        if (req.title() != null) p.setTitle(req.title());
        if (req.description() != null) p.setDescription(req.description());
        if (req.handle() != null) {
            checkHandleUnique(req.handle(), id);
            p.setHandle(req.handle());
        }
        if (req.vendor() != null) p.setVendor(req.vendor());
        if (req.productType() != null) p.setProductType(req.productType());
        if (req.featuredImageId() != null) p.setFeaturedImageId(req.featuredImageId());
        if (req.tags() != null) {
            p.getTags().clear();
            req.tags().forEach(name -> p.getTags().add(findOrCreateTag(name)));
        }
        return toAdminResponse(productRepo.save(p));
    }

    @Transactional
    public AdminProductResponse changeStatus(UUID id, ProductStatusRequest req) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        p.setStatus(req.status());
        return toAdminResponse(productRepo.save(p));
    }

    @Transactional
    public void softDelete(UUID id) {
        Product p = productRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        p.setDeletedAt(Instant.now());
        productRepo.save(p);
    }

    // Storefront
    @Transactional(readOnly = true)
    public Map<String, Object> listStorefront(String cursor) {
        UUID cursorId = cursor != null ? UUID.fromString(cursor) : null;
        int limit = PAGE_SIZE + 1;
        List<Product> raw = cursorId == null
            ? productRepo.findByStatusAndDeletedAtIsNullOrderByIdAsc(ProductStatus.ACTIVE, Limit.of(limit))
            : productRepo.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(ProductStatus.ACTIVE, cursorId, Limit.of(limit));

        boolean hasMore = raw.size() > PAGE_SIZE;
        List<Product> page = hasMore ? raw.subList(0, PAGE_SIZE) : raw;
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;

        List<ProductSummaryResponse> items = page.stream()
            .map(p -> new ProductSummaryResponse(p.getId(), p.getTitle(), p.getHandle(), p.getVendor()))
            .toList();
        CursorMeta meta = CursorMeta.builder()
            .nextCursor(nextCursor).hasMore(hasMore).pageSize(PAGE_SIZE).build();
        return Map.of("items", items, "meta", meta);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getByHandle(String handle) {
        Product p = productRepo.findByHandle(handle)
            .filter(prod -> prod.getStatus() == ProductStatus.ACTIVE && prod.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return toDetailResponse(p);
    }

    // --- helpers ---

    private void checkHandleUnique(String handle, UUID excludeId) {
        boolean conflict = excludeId == null
            ? productRepo.existsByHandleAndDeletedAtIsNull(handle)
            : productRepo.existsByHandleAndDeletedAtIsNullAndIdNot(handle, excludeId);
        if (conflict) {
            throw new ValidationException("HANDLE_CONFLICT", "A product with this handle already exists");
        }
    }

    private ProductTag findOrCreateTag(String name) {
        return tagRepo.findByName(name).orElseGet(() -> {
            ProductTag t = new ProductTag();
            t.setName(name);
            return tagRepo.save(t);
        });
    }

    static String slugify(String title) {
        String slug = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "product" : slug;
    }

    AdminProductResponse toAdminResponse(Product p) {
        List<ProductVariant> variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(p.getId());
        List<ProductImage> images = imageRepo.findByProductIdOrderByPositionAsc(p.getId());
        List<ProductOption> options = optionRepo.findByProductIdOrderByPositionAsc(p.getId());
        Map<UUID, String> optionNameById = options.stream()
            .collect(Collectors.toMap(o -> o.getId(), ProductOption::getName));

        List<AdminVariantResponse> variantResponses = variants.stream().map(v -> {
            List<OptionValueLabel> labels = v.getOptionValues().stream()
                .map(ov -> new OptionValueLabel(
                    optionNameById.getOrDefault(ov.getOptionId(), ""),
                    ov.getLabel()))
                .toList();
            return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
                v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
                labels, v.getDeletedAt());
        }).toList();

        List<ProductImageResponse> imageResponses = images.stream().map(img -> {
            String url = blobRepo.findById(img.getBlobId())
                .map(b -> storageService.resolveUrl(b.getKey()))
                .orElse("");
            return new ProductImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
        }).toList();

        List<String> tagNames = p.getTags().stream().map(ProductTag::getName).toList();

        return new AdminProductResponse(p.getId(), p.getTitle(), p.getDescription(), p.getHandle(),
            p.getVendor(), p.getProductType(), p.getStatus().name(), p.getFeaturedImageId(),
            variantResponses, imageResponses, tagNames,
            p.getCreatedAt(), p.getUpdatedAt(), p.getDeletedAt());
    }

    private ProductDetailResponse toDetailResponse(Product p) {
        List<ProductVariant> variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(p.getId());
        List<ProductImage> images = imageRepo.findByProductIdOrderByPositionAsc(p.getId());
        List<ProductOption> options = optionRepo.findByProductIdOrderByPositionAsc(p.getId());
        Map<UUID, String> optionNameById = options.stream()
            .collect(Collectors.toMap(o -> o.getId(), ProductOption::getName));

        List<ProductVariantResponse> variantResponses = variants.stream().map(v -> {
            List<OptionValueLabel> labels = v.getOptionValues().stream()
                .map(ov -> new OptionValueLabel(
                    optionNameById.getOrDefault(ov.getOptionId(), ""),
                    ov.getLabel()))
                .toList();
            return new ProductVariantResponse(v.getId(), v.getTitle(), v.getSku(),
                v.getPrice(), v.getCompareAtPrice(), labels);
        }).toList();

        List<ProductImageResponse> imageResponses = images.stream().map(img -> {
            String url = blobRepo.findById(img.getBlobId())
                .map(b -> storageService.resolveUrl(b.getKey()))
                .orElse("");
            return new ProductImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
        }).toList();

        List<String> tagNames = p.getTags().stream().map(ProductTag::getName).toList();

        return new ProductDetailResponse(p.getId(), p.getTitle(), p.getDescription(), p.getHandle(),
            p.getVendor(), p.getProductType(), variantResponses, imageResponses, tagNames);
    }
}
```

- [ ] **Step 4: Run the test**

```bash
./mvnw test -Dtest=ProductServiceIT -q
```

Expected: all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/service/ProductService.java \
        src/test/java/io/k2dv/garden/product/service/ProductServiceIT.java
git commit -m "feat(product): add ProductService with CRUD, handle slugification, soft delete"
```

---

### Task 5: VariantService and OptionService

**Files:**
- Create: `src/main/java/io/k2dv/garden/product/service/VariantService.java`
- Create: `src/main/java/io/k2dv/garden/product/service/OptionService.java`
- Modify: `src/test/java/io/k2dv/garden/product/service/ProductServiceIT.java` (add variant/option tests)

- [ ] **Step 1: Add variant and option tests to `ProductServiceIT`**

Add these test methods to the existing `ProductServiceIT` class:

```java
@Autowired VariantService variantService;
@Autowired OptionService optionService;
@Autowired ProductVariantRepository variantRepo;
@Autowired InventoryItemRepository inventoryRepo;

@Test
void createVariant_autoCreatesInventoryItem() {
    var product = productService.create(new CreateProductRequest("Widget", null, null, null, null, List.of()));
    var req = new CreateVariantRequest(new java.math.BigDecimal("9.99"), null, null, null, null, null, List.of());
    var variant = variantService.create(product.id(), req);

    assertThat(variant.title()).isEqualTo("Default Title");
    assertThat(inventoryRepo.findByVariantId(variant.id())).isPresent();
    assertThat(inventoryRepo.findByVariantId(variant.id()).get().isRequiresShipping()).isTrue();
}

@Test
void createVariant_withOptionValues_generatesTitle() {
    var product = productService.create(new CreateProductRequest("Shirt", null, null, null, null, List.of()));
    var colorOpt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
    var redVal = optionService.createOptionValue(colorOpt.id(), new CreateOptionValueRequest("Red", 1));
    var sizeOpt = optionService.createOption(product.id(), new CreateOptionRequest("Size", 2));
    var lgVal = optionService.createOptionValue(sizeOpt.id(), new CreateOptionValueRequest("Large", 1));

    var req = new CreateVariantRequest(new java.math.BigDecimal("19.99"), null, null, null, null, null,
        List.of(redVal.id(), lgVal.id()));
    var variant = variantService.create(product.id(), req);

    assertThat(variant.title()).isEqualTo("Red / Large");
}

@Test
void renameOptionValue_recomputesVariantTitles() {
    var product = productService.create(new CreateProductRequest("Hat", null, null, null, null, List.of()));
    var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
    var val = optionService.createOptionValue(opt.id(), new CreateOptionValueRequest("Blu", 1)); // typo

    var req = new CreateVariantRequest(new java.math.BigDecimal("25.00"), null, null, null, null, null,
        List.of(val.id()));
    variantService.create(product.id(), req);

    optionService.renameOptionValue(opt.id(), val.id(), new RenameOptionValueRequest("Blue"));

    var updated = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.id());
    assertThat(updated.get(0).getTitle()).isEqualTo("Blue");
}

@Test
void softDeleteVariant_excludedFromProductVariants() {
    var product = productService.create(new CreateProductRequest("Cap", null, null, null, null, List.of()));
    var req = new CreateVariantRequest(new java.math.BigDecimal("15.00"), null, null, null, null, null, List.of());
    var variant = variantService.create(product.id(), req);
    variantService.softDelete(product.id(), variant.id());

    var variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.id());
    assertThat(variants).isEmpty();
}
```

Also add helper DTOs that the tests reference — `colorOpt.id()`, `redVal.id()` — these come from option/value response records. Add the response records:

```java
// ProductOptionResponse.java
package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ProductOptionResponse(UUID id, String name, int position) {}
```

```java
// ProductOptionValueResponse.java
package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ProductOptionValueResponse(UUID id, String label, int position) {}
```

- [ ] **Step 2: Run the test — expect compilation failure**

```bash
./mvnw test -Dtest=ProductServiceIT -q 2>&1 | tail -10
```

Expected: compilation errors for `VariantService`, `OptionService`, `ProductOptionResponse`, `ProductOptionValueResponse`.

- [ ] **Step 3: Implement `VariantService`**

```java
package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.*;
import io.k2dv.garden.product.repository.*;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VariantService {

    private final ProductVariantRepository variantRepo;
    private final ProductOptionValueRepository optionValueRepo;
    private final ProductOptionRepository optionRepo;
    private final InventoryItemRepository inventoryRepo;
    private final ProductRepository productRepo;

    @Transactional
    public AdminVariantResponse create(UUID productId, CreateVariantRequest req) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        if (req.compareAtPrice() != null && req.price() != null
                && req.compareAtPrice().compareTo(req.price()) <= 0) {
            throw new ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than price");
        }

        // Resolve option values
        List<ProductOptionValue> optionValues = req.optionValueIds() == null ? List.of()
            : req.optionValueIds().stream()
                .map(id -> optionValueRepo.findById(id)
                    .orElseThrow(() -> new NotFoundException("OPTION_VALUE_NOT_FOUND", "Option value not found")))
                .toList();

        // Build title
        String title = buildTitle(optionValues);

        ProductVariant v = new ProductVariant();
        v.setProductId(productId);
        v.setTitle(title);
        v.setSku(req.sku());
        v.setBarcode(req.barcode());
        v.setPrice(req.price());
        v.setCompareAtPrice(req.compareAtPrice());
        v.setWeight(req.weight());
        v.setWeightUnit(req.weightUnit());
        v.getOptionValues().addAll(optionValues);
        v = variantRepo.save(v);

        // Auto-create inventory item
        InventoryItem inv = new InventoryItem();
        inv.setVariantId(v.getId());
        inv.setRequiresShipping(true);
        inventoryRepo.save(inv);

        return toResponse(v);
    }

    @Transactional
    public AdminVariantResponse update(UUID productId, UUID variantId, UpdateVariantRequest req) {
        ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(variantId)
            .filter(vv -> vv.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));

        // Validate compareAtPrice against the effective price (new or existing)
        java.math.BigDecimal effectivePrice = req.price() != null ? req.price() : v.getPrice();
        if (req.compareAtPrice() != null && req.compareAtPrice().compareTo(effectivePrice) <= 0) {
            throw new ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than price");
        }
        if (req.price() != null) v.setPrice(req.price());
        if (req.compareAtPrice() != null) v.setCompareAtPrice(req.compareAtPrice());
        if (req.sku() != null) v.setSku(req.sku());
        if (req.barcode() != null) v.setBarcode(req.barcode());
        if (req.weight() != null) v.setWeight(req.weight());
        if (req.weightUnit() != null) v.setWeightUnit(req.weightUnit());
        return toResponse(variantRepo.save(v));
    }

    @Transactional
    public void softDelete(UUID productId, UUID variantId) {
        ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(variantId)
            .filter(vv -> vv.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        v.setDeletedAt(Instant.now());
        variantRepo.save(v);
    }

    String buildTitle(List<ProductOptionValue> values) {
        if (values.isEmpty()) return "Default Title";
        return values.stream().map(ProductOptionValue::getLabel).reduce((a, b) -> a + " / " + b).orElse("Default Title");
    }

    private AdminVariantResponse toResponse(ProductVariant v) {
        List<OptionValueLabel> labels = v.getOptionValues().stream()
            .map(ov -> {
                String optName = optionRepo.findById(ov.getOptionId())
                    .map(ProductOption::getName).orElse("");
                return new OptionValueLabel(optName, ov.getLabel());
            }).toList();
        return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
            v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
            labels, v.getDeletedAt());
    }
}
```

- [ ] **Step 4: Implement `OptionService`**

```java
package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.*;
import io.k2dv.garden.product.repository.*;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OptionService {

    private final ProductOptionRepository optionRepo;
    private final ProductOptionValueRepository optionValueRepo;
    private final ProductVariantRepository variantRepo;
    private final VariantService variantService;

    @Transactional
    public ProductOptionResponse createOption(UUID productId, CreateOptionRequest req) {
        ProductOption opt = new ProductOption();
        opt.setProductId(productId);
        opt.setName(req.name());
        opt.setPosition(req.position());
        opt = optionRepo.save(opt);
        return new ProductOptionResponse(opt.getId(), opt.getName(), opt.getPosition());
    }

    @Transactional
    public ProductOptionResponse renameOption(UUID productId, UUID optionId, RenameOptionRequest req) {
        ProductOption opt = optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        opt.setName(req.name());
        opt = optionRepo.save(opt);
        return new ProductOptionResponse(opt.getId(), opt.getName(), opt.getPosition());
    }

    @Transactional
    public void deleteOption(UUID productId, UUID optionId) {
        ProductOption opt = optionRepo.findById(optionId)
            .filter(o -> o.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        optionRepo.delete(opt);
    }

    @Transactional
    public ProductOptionValueResponse createOptionValue(UUID optionId, CreateOptionValueRequest req) {
        optionRepo.findById(optionId)
            .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND", "Option not found"));
        ProductOptionValue val = new ProductOptionValue();
        val.setOptionId(optionId);
        val.setLabel(req.label());
        val.setPosition(req.position());
        val = optionValueRepo.save(val);
        return new ProductOptionValueResponse(val.getId(), val.getLabel(), val.getPosition());
    }

    @Transactional
    public ProductOptionValueResponse renameOptionValue(UUID optionId, UUID valueId, RenameOptionValueRequest req) {
        ProductOptionValue val = optionValueRepo.findById(valueId)
            .filter(v -> v.getOptionId().equals(optionId))
            .orElseThrow(() -> new NotFoundException("OPTION_VALUE_NOT_FOUND", "Option value not found"));
        val.setLabel(req.label());
        val = optionValueRepo.save(val);

        // Recompute title of all non-deleted variants linked to this value
        List<ProductVariant> affected = variantRepo.findByOptionValueIdAndDeletedAtIsNull(valueId);
        for (ProductVariant v : affected) {
            v.setTitle(variantService.buildTitle(v.getOptionValues()));
            variantRepo.save(v);
        }

        return new ProductOptionValueResponse(val.getId(), val.getLabel(), val.getPosition());
    }
}
```

- [ ] **Step 5: Run the tests**

```bash
./mvnw test -Dtest=ProductServiceIT -q
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/service/VariantService.java \
        src/main/java/io/k2dv/garden/product/service/OptionService.java \
        src/main/java/io/k2dv/garden/product/dto/ProductOptionResponse.java \
        src/main/java/io/k2dv/garden/product/dto/ProductOptionValueResponse.java \
        src/test/java/io/k2dv/garden/product/service/ProductServiceIT.java
git commit -m "feat(product): add VariantService and OptionService with title recomputation"
```

---

### Task 6: ProductImageService

**Files:**
- Create: `src/main/java/io/k2dv/garden/product/service/ProductImageService.java`
- Create: `src/test/java/io/k2dv/garden/product/service/ProductImageServiceIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.model.BlobObject;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.product.dto.CreateImageRequest;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageServiceIT extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductImageService imageService;
    @Autowired ProductRepository productRepo;
    @Autowired BlobObjectRepository blobRepo;

    UUID productId;
    UUID blobId;

    @BeforeEach
    void setup() {
        // Create a dummy BlobObject to satisfy FK
        BlobObject blob = new BlobObject();
        blob.setKey("uploads/test.jpg");
        blob.setFilename("test.jpg");
        blob.setContentType("image/jpeg");
        blob.setSize(100L);
        blobId = blobRepo.save(blob).getId();

        productId = productService.create(
            new CreateProductRequest("Img Product", null, null, null, null, List.of())
        ).id();
    }

    @Test
    void addFirstImage_setsFeaturedImageId() {
        var img = imageService.addImage(productId, new CreateImageRequest(blobId, "alt"));
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isEqualTo(img.id());
    }

    @Test
    void addSecondImage_doesNotChangeFeaturedImageId() {
        var first = imageService.addImage(productId, new CreateImageRequest(blobId, "first"));

        BlobObject blob2 = new BlobObject();
        blob2.setKey("uploads/test2.jpg");
        blob2.setFilename("test2.jpg");
        blob2.setContentType("image/jpeg");
        blob2.setSize(100L);
        UUID blobId2 = blobRepo.save(blob2).getId();

        imageService.addImage(productId, new CreateImageRequest(blobId2, "second"));
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isEqualTo(first.id());
    }

    @Test
    void deleteFeaturedImage_promoteNextByPosition() {
        var first = imageService.addImage(productId, new CreateImageRequest(blobId, "first"));

        BlobObject blob2 = new BlobObject();
        blob2.setKey("uploads/second.jpg");
        blob2.setFilename("second.jpg");
        blob2.setContentType("image/jpeg");
        blob2.setSize(100L);
        UUID blobId2 = blobRepo.save(blob2).getId();
        var second = imageService.addImage(productId, new CreateImageRequest(blobId2, "second"));

        imageService.deleteImage(productId, first.id());
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isEqualTo(second.id());
    }

    @Test
    void deleteLastImage_setsFeaturedImageIdNull() {
        imageService.addImage(productId, new CreateImageRequest(blobId, "only"));
        var img = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow().getFeaturedImageId();
        assertThat(img).isNotNull();

        imageService.deleteImage(productId, img);
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isNull();
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./mvnw test -Dtest=ProductImageServiceIT -q 2>&1 | tail -10
```

- [ ] **Step 3: Implement `ProductImageService`**

```java
package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.product.dto.CreateImageRequest;
import io.k2dv.garden.product.dto.ImagePositionItem;
import io.k2dv.garden.product.dto.ProductImageResponse;
import io.k2dv.garden.product.model.ProductImage;
import io.k2dv.garden.product.repository.ProductImageRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository imageRepo;
    private final ProductRepository productRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    @Transactional
    public ProductImageResponse addImage(UUID productId, CreateImageRequest req) {
        var product = productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        int nextPosition = imageRepo.countByProductId(productId) + 1;

        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setBlobId(req.blobId());
        img.setAltText(req.altText());
        img.setPosition(nextPosition);
        img = imageRepo.save(img);

        // Auto-set featuredImageId if this is the first image
        if (product.getFeaturedImageId() == null) {
            product.setFeaturedImageId(img.getId());
            productRepo.save(product);
        }

        return toResponse(img);
    }

    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        var product = productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        var img = imageRepo.findById(imageId)
            .filter(i -> i.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "Image not found"));

        boolean wasFeatured = imageId.equals(product.getFeaturedImageId());
        imageRepo.delete(img);

        if (wasFeatured) {
            // Promote next image by lowest position
            List<ProductImage> remaining = imageRepo.findByProductIdOrderByPositionAsc(productId);
            product.setFeaturedImageId(remaining.isEmpty() ? null : remaining.get(0).getId());
            productRepo.save(product);
        }
    }

    @Transactional
    public void reorderImages(UUID productId, List<ImagePositionItem> items) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        for (ImagePositionItem item : items) {
            imageRepo.findById(item.id())
                .filter(img -> img.getProductId().equals(productId))
                .ifPresent(img -> {
                    img.setPosition(item.position());
                    imageRepo.save(img);
                });
        }
    }

    private ProductImageResponse toResponse(ProductImage img) {
        String url = blobRepo.findById(img.getBlobId())
            .map(b -> storageService.resolveUrl(b.getKey()))
            .orElse("");
        return new ProductImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -Dtest=ProductImageServiceIT -q
```

Expected: all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/service/ProductImageService.java \
        src/test/java/io/k2dv/garden/product/service/ProductImageServiceIT.java
git commit -m "feat(product): add ProductImageService with auto-featured image logic"
```

---

### Task 7: Admin Controllers

**Files:**
- Create: `src/main/java/io/k2dv/garden/product/controller/AdminProductController.java`
- Create: `src/main/java/io/k2dv/garden/product/controller/AdminInventoryController.java`

- [ ] **Step 1: Create `AdminProductController`**

```java
package io.k2dv.garden.product.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.*;
import io.k2dv.garden.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final VariantService variantService;
    private final OptionService optionService;
    private final ProductImageService imageService;

    // Product CRUD
    @PostMapping
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminProductResponse>> create(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(productService.create(req)));
    }

    @GetMapping
    @HasPermission("product:read")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(productService.listAdmin(status, cursor));
    }

    @GetMapping("/{id}")
    @HasPermission("product:read")
    public ResponseEntity<ApiResponse<AdminProductResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(productService.getAdmin(id)));
    }

    @PatchMapping("/{id}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminProductResponse>> update(
            @PathVariable UUID id, @RequestBody UpdateProductRequest req) {
        return ResponseEntity.ok(ApiResponse.of(productService.update(id, req)));
    }

    @PatchMapping("/{id}/status")
    @HasPermission("product:publish")
    public ResponseEntity<ApiResponse<AdminProductResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ProductStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(productService.changeStatus(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // Variants
    @PostMapping("/{id}/variants")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminVariantResponse>> createVariant(
            @PathVariable UUID id, @Valid @RequestBody CreateVariantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(variantService.create(id, req)));
    }

    @PatchMapping("/{id}/variants/{vId}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminVariantResponse>> updateVariant(
            @PathVariable UUID id, @PathVariable UUID vId, @RequestBody UpdateVariantRequest req) {
        return ResponseEntity.ok(ApiResponse.of(variantService.update(id, vId, req)));
    }

    @DeleteMapping("/{id}/variants/{vId}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> deleteVariant(@PathVariable UUID id, @PathVariable UUID vId) {
        variantService.softDelete(id, vId);
        return ResponseEntity.noContent().build();
    }

    // Images — NOTE: /positions must be declared BEFORE /{imgId} to avoid routing conflict
    @PostMapping("/{id}/images")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductImageResponse>> addImage(
            @PathVariable UUID id, @Valid @RequestBody CreateImageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(imageService.addImage(id, req)));
    }

    @PatchMapping("/{id}/images/positions")
    @HasPermission("product:write")
    public ResponseEntity<Void> reorderImages(
            @PathVariable UUID id, @RequestBody List<ImagePositionItem> items) {
        imageService.reorderImages(id, items);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/images/{imgId}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imgId) {
        imageService.deleteImage(id, imgId);
        return ResponseEntity.noContent().build();
    }

    // Options
    @PostMapping("/{id}/options")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductOptionResponse>> createOption(
            @PathVariable UUID id, @Valid @RequestBody CreateOptionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(optionService.createOption(id, req)));
    }

    @PatchMapping("/{id}/options/{optId}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductOptionResponse>> renameOption(
            @PathVariable UUID id, @PathVariable UUID optId, @Valid @RequestBody RenameOptionRequest req) {
        return ResponseEntity.ok(ApiResponse.of(optionService.renameOption(id, optId, req)));
    }

    @DeleteMapping("/{id}/options/{optId}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> deleteOption(@PathVariable UUID id, @PathVariable UUID optId) {
        optionService.deleteOption(id, optId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/options/{optId}/values/{valId}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductOptionValueResponse>> renameOptionValue(
            @PathVariable UUID id, @PathVariable UUID optId, @PathVariable UUID valId,
            @Valid @RequestBody RenameOptionValueRequest req) {
        return ResponseEntity.ok(ApiResponse.of(optionService.renameOptionValue(optId, valId, req)));
    }

    // Inventory GET — delegates to VariantService.getInventoryForProduct (added in Step 2)
    @GetMapping("/{id}/inventory")
    @HasPermission("inventory:read")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> getInventory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(variantService.getInventoryForProduct(id)));
    }
}
```

- [ ] **Step 2: Add `getInventoryForProduct` to `VariantService` — append to existing class, do NOT replace existing methods**

The complete final `VariantService` (all methods — Task 5 methods plus the new one):

```java
package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.*;
import io.k2dv.garden.product.repository.*;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VariantService {

    private final ProductVariantRepository variantRepo;
    private final ProductOptionValueRepository optionValueRepo;
    private final ProductOptionRepository optionRepo;
    private final InventoryItemRepository inventoryRepo;
    private final ProductRepository productRepo;

    @Transactional
    public AdminVariantResponse create(UUID productId, CreateVariantRequest req) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        if (req.compareAtPrice() != null && req.price() != null
                && req.compareAtPrice().compareTo(req.price()) <= 0) {
            throw new ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than price");
        }

        List<ProductOptionValue> optionValues = req.optionValueIds() == null ? List.of()
            : req.optionValueIds().stream()
                .map(id -> optionValueRepo.findById(id)
                    .orElseThrow(() -> new NotFoundException("OPTION_VALUE_NOT_FOUND", "Option value not found")))
                .toList();

        String title = buildTitle(optionValues);

        ProductVariant v = new ProductVariant();
        v.setProductId(productId);
        v.setTitle(title);
        v.setSku(req.sku());
        v.setBarcode(req.barcode());
        v.setPrice(req.price());
        v.setCompareAtPrice(req.compareAtPrice());
        v.setWeight(req.weight());
        v.setWeightUnit(req.weightUnit());
        v.getOptionValues().addAll(optionValues);
        v = variantRepo.save(v);

        InventoryItem inv = new InventoryItem();
        inv.setVariantId(v.getId());
        inv.setRequiresShipping(true);
        inventoryRepo.save(inv);

        return toResponse(v);
    }

    @Transactional
    public AdminVariantResponse update(UUID productId, UUID variantId, UpdateVariantRequest req) {
        ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(variantId)
            .filter(vv -> vv.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));

        BigDecimal effectivePrice = req.price() != null ? req.price() : v.getPrice();
        if (req.compareAtPrice() != null && req.compareAtPrice().compareTo(effectivePrice) <= 0) {
            throw new ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than price");
        }
        if (req.price() != null) v.setPrice(req.price());
        if (req.compareAtPrice() != null) v.setCompareAtPrice(req.compareAtPrice());
        if (req.sku() != null) v.setSku(req.sku());
        if (req.barcode() != null) v.setBarcode(req.barcode());
        if (req.weight() != null) v.setWeight(req.weight());
        if (req.weightUnit() != null) v.setWeightUnit(req.weightUnit());
        return toResponse(variantRepo.save(v));
    }

    @Transactional
    public void softDelete(UUID productId, UUID variantId) {
        ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(variantId)
            .filter(vv -> vv.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        v.setDeletedAt(Instant.now());
        variantRepo.save(v);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getInventoryForProduct(UUID productId) {
        List<ProductVariant> variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(productId);
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        return inventoryRepo.findByVariantIdIn(variantIds).stream()
            .map(inv -> new InventoryItemResponse(inv.getId(), inv.getVariantId(), inv.isRequiresShipping()))
            .toList();
    }

    String buildTitle(List<ProductOptionValue> values) {
        if (values.isEmpty()) return "Default Title";
        return values.stream().map(ProductOptionValue::getLabel)
            .reduce((a, b) -> a + " / " + b).orElse("Default Title");
    }

    private AdminVariantResponse toResponse(ProductVariant v) {
        List<OptionValueLabel> labels = v.getOptionValues().stream()
            .map(ov -> {
                String optName = optionRepo.findById(ov.getOptionId())
                    .map(ProductOption::getName).orElse("");
                return new OptionValueLabel(optName, ov.getLabel());
            }).toList();
        return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
            v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
            labels, v.getDeletedAt());
    }
}
```

- [ ] **Step 3: Create `AdminInventoryController`**

```java
package io.k2dv.garden.product.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.product.dto.InventoryItemResponse;
import io.k2dv.garden.product.dto.UpdateInventoryRequest;
import io.k2dv.garden.product.model.InventoryItem;
import io.k2dv.garden.product.repository.InventoryItemRepository;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryItemRepository inventoryRepo;

    @PatchMapping("/{itemId}")
    @HasPermission("inventory:write")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> update(
            @PathVariable UUID itemId, @RequestBody UpdateInventoryRequest req) {
        InventoryItem inv = inventoryRepo.findById(itemId)
            .orElseThrow(() -> new NotFoundException("INVENTORY_NOT_FOUND", "Inventory item not found"));
        inv.setRequiresShipping(req.requiresShipping());
        inv = inventoryRepo.save(inv);
        return ResponseEntity.ok(ApiResponse.of(
            new InventoryItemResponse(inv.getId(), inv.getVariantId(), inv.isRequiresShipping())));
    }
}
```

- [ ] **Step 4: Run full test suite**

```bash
./mvnw test -q
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/controller/ \
        src/main/java/io/k2dv/garden/product/service/VariantService.java
git commit -m "feat(product): add AdminProductController and AdminInventoryController"
```

---

### Task 8: Storefront Controller

**Files:**
- Create: `src/main/java/io/k2dv/garden/product/controller/StorefrontProductController.java`

- [ ] **Step 1: Create `StorefrontProductController`**

```java
package io.k2dv.garden.product.controller;

import io.k2dv.garden.product.dto.ProductDetailResponse;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class StorefrontProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(productService.listStorefront(cursor));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(productService.getByHandle(handle)));
    }
}
```

- [ ] **Step 2: Run full test suite**

```bash
./mvnw test -q
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/controller/StorefrontProductController.java
git commit -m "feat(product): add StorefrontProductController"
```

---

### Task 9: Controller Slice Tests

**Files:**
- Create: `src/test/java/io/k2dv/garden/product/controller/AdminProductControllerTest.java`
- Create: `src/test/java/io/k2dv/garden/product/controller/StorefrontProductControllerTest.java`

- [ ] **Step 1: Write `AdminProductControllerTest`**

```java
package io.k2dv.garden.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.service.*;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminProductController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminProductControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean ProductService productService;
    @MockitoBean VariantService variantService;
    @MockitoBean OptionService optionService;
    @MockitoBean ProductImageService imageService;

    private AdminProductResponse stubProduct() {
        return new AdminProductResponse(UUID.randomUUID(), "T-Shirt", null, "t-shirt",
            null, null, "DRAFT", null, List.of(), List.of(), List.of(), null, null, null);
    }

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        when(productService.create(any())).thenReturn(stubProduct());

        mvc.perform(post("/api/v1/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateProductRequest("T-Shirt", null, null, null, null, List.of()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("T-Shirt"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createProduct_missingTitle_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        when(productService.getAdmin(any()))
            .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        mvc.perform(get("/api/v1/admin/products/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void changeStatus_returns200() throws Exception {
        when(productService.changeStatus(any(), any())).thenReturn(stubProduct());

        mvc.perform(patch("/api/v1/admin/products/{id}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void deleteProduct_returns204() throws Exception {
        doNothing().when(productService).softDelete(any());

        mvc.perform(delete("/api/v1/admin/products/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void reorderImages_routingBeforeImageId_returns200() throws Exception {
        doNothing().when(imageService).reorderImages(any(), any());

        mvc.perform(patch("/api/v1/admin/products/{id}/images/positions", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Write `StorefrontProductControllerTest`**

```java
package io.k2dv.garden.product.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.product.dto.ProductDetailResponse;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.dto.CursorMeta;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontProductController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class StorefrontProductControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductService productService;

    @Test
    void listProducts_returns200WithCursorMeta() throws Exception {
        var items = List.of(new ProductSummaryResponse(UUID.randomUUID(), "Shirt", "shirt", null));
        var meta = CursorMeta.builder().nextCursor(null).hasMore(false).pageSize(20).build();
        when(productService.listStorefront(any())).thenReturn(Map.of("items", items, "meta", meta));

        mvc.perform(get("/api/v1/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("Shirt"));
    }

    @Test
    void getByHandle_activeProduct_returns200() throws Exception {
        var detail = new ProductDetailResponse(UUID.randomUUID(), "Shirt", null, "shirt",
            null, null, List.of(), List.of(), List.of());
        when(productService.getByHandle("shirt")).thenReturn(detail);

        mvc.perform(get("/api/v1/products/shirt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.handle").value("shirt"));
    }

    @Test
    void getByHandle_draftProduct_returns404() throws Exception {
        when(productService.getByHandle(any()))
            .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        mvc.perform(get("/api/v1/products/draft-product"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 3: Run slice tests**

```bash
./mvnw test -Dtest="AdminProductControllerTest,StorefrontProductControllerTest" -q
```

Expected: all tests pass.

- [ ] **Step 4: Run full test suite**

```bash
./mvnw test -q
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/io/k2dv/garden/product/controller/
git commit -m "test(product): add controller slice tests for admin and storefront"
```

---

### Task 10: Final Verification

- [ ] **Step 1: Run the complete test suite**

```bash
./mvnw test -q
```

Expected: all tests pass, including all pre-existing tests (auth, account, IAM, blob, etc.) plus the new product tests.

- [ ] **Step 2: Verify migration version sequence is intact**

```bash
ls src/main/resources/db/migration/
```

Expected: V1 through V9 in order, no gaps.

- [ ] **Step 3: Commit if anything was missed**

If any files weren't committed in prior steps, add them and commit. Then the branch is ready for review.
