# Collections Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Shopify-style Manual and Automated Collections for the Garden e-commerce backend, including admin CRUD, rule-based membership sync, and storefront read APIs.

**Architecture:** A new `collection` package mirrors the existing `product` package structure. A `CollectionMembershipService` owns all logic for computing and syncing automated collection membership. `ProductService` calls into it on tag change and product archive/delete.

**Tech Stack:** Spring Boot 4.0, Java 25, JPA/Hibernate, PostgreSQL, Flyway, Lombok, AssertJ, Testcontainers

**Spec:** `docs/superpowers/specs/2026-03-24-collections-design.md`

---

## File Map

### New Files

| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V13__create_collections.sql` | DB schema: tables, indexes, CHECK constraints, permissions |
| `src/main/java/io/k2dv/garden/collection/model/CollectionType.java` | Enum: MANUAL, AUTOMATED |
| `src/main/java/io/k2dv/garden/collection/model/CollectionStatus.java` | Enum: DRAFT, ACTIVE |
| `src/main/java/io/k2dv/garden/collection/model/CollectionRuleField.java` | Enum: TAG |
| `src/main/java/io/k2dv/garden/collection/model/CollectionRuleOperator.java` | Enum: EQUALS, NOT_EQUALS, CONTAINS |
| `src/main/java/io/k2dv/garden/collection/model/Collection.java` | JPA entity for `catalog.collections` |
| `src/main/java/io/k2dv/garden/collection/model/CollectionRule.java` | JPA entity for `catalog.collection_rules` |
| `src/main/java/io/k2dv/garden/collection/model/CollectionProduct.java` | JPA entity for `catalog.collection_products` |
| `src/main/java/io/k2dv/garden/collection/repository/CollectionRepository.java` | Finders with soft-delete awareness |
| `src/main/java/io/k2dv/garden/collection/repository/CollectionRuleRepository.java` | Finders by collection_id |
| `src/main/java/io/k2dv/garden/collection/repository/CollectionProductRepository.java` | Finders, max-position query, bulk delete |
| `src/main/java/io/k2dv/garden/collection/specification/CollectionSpecification.java` | Admin filter spec (type, status, title) |
| `src/main/java/io/k2dv/garden/collection/dto/request/CreateCollectionRequest.java` | title, handle?, description?, type, disjunctive?, featuredImageId? |
| `src/main/java/io/k2dv/garden/collection/dto/request/UpdateCollectionRequest.java` | All nullable |
| `src/main/java/io/k2dv/garden/collection/dto/request/CollectionStatusRequest.java` | status field |
| `src/main/java/io/k2dv/garden/collection/dto/request/AddCollectionProductRequest.java` | productId |
| `src/main/java/io/k2dv/garden/collection/dto/request/UpdateCollectionProductPositionRequest.java` | position |
| `src/main/java/io/k2dv/garden/collection/dto/request/CreateCollectionRuleRequest.java` | field, operator, value |
| `src/main/java/io/k2dv/garden/collection/dto/request/CollectionFilterRequest.java` | type?, status?, titleContains? |
| `src/main/java/io/k2dv/garden/collection/dto/response/AdminCollectionResponse.java` | Full detail: all fields + rules list + productCount |
| `src/main/java/io/k2dv/garden/collection/dto/response/AdminCollectionSummaryResponse.java` | List item: id, title, handle, type, status, productCount, createdAt |
| `src/main/java/io/k2dv/garden/collection/dto/response/CollectionDetailResponse.java` | Storefront detail: id, title, handle, description, featuredImageId |
| `src/main/java/io/k2dv/garden/collection/dto/response/CollectionSummaryResponse.java` | Storefront list item: id, title, handle |
| `src/main/java/io/k2dv/garden/collection/dto/response/CollectionProductResponse.java` | id, productId, title, handle, position |
| `src/main/java/io/k2dv/garden/collection/dto/response/CollectionRuleResponse.java` | id, field, operator, value, createdAt |
| `src/main/java/io/k2dv/garden/collection/service/CollectionMembershipService.java` | Rule evaluation + sync logic |
| `src/main/java/io/k2dv/garden/collection/service/CollectionService.java` | CRUD + rules + manual membership |
| `src/main/java/io/k2dv/garden/collection/controller/AdminCollectionController.java` | Admin REST endpoints |
| `src/main/java/io/k2dv/garden/collection/controller/StorefrontCollectionController.java` | Storefront REST endpoints |
| `src/test/java/io/k2dv/garden/collection/service/CollectionMembershipServiceTest.java` | Unit tests for rule evaluation |
| `src/test/java/io/k2dv/garden/collection/service/CollectionServiceIT.java` | Integration tests |

### Modified Files

| File | Change |
|---|---|
| `src/main/java/io/k2dv/garden/product/repository/ProductRepository.java` | Add `findAllByStatusAndDeletedAtIsNull(ProductStatus status)` bulk query |
| `src/main/java/io/k2dv/garden/product/service/ProductService.java` | Inject `CollectionMembershipService`; call sync on tag update, archive, and soft-delete |

---

## Task 1: Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V13__create_collections.sql`

- [ ] **Step 1: Write migration**

```sql
-- catalog.collections
CREATE TABLE catalog.collections (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title             TEXT        NOT NULL,
    handle            TEXT        NOT NULL UNIQUE,
    description       TEXT,
    collection_type   TEXT        NOT NULL CHECK (collection_type IN ('MANUAL', 'AUTOMATED')),
    status            TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE')),
    featured_image_id UUID,
    disjunctive       BOOLEAN     NOT NULL DEFAULT false,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_collections_handle     ON catalog.collections (handle);
CREATE INDEX idx_collections_status     ON catalog.collections (status);
CREATE INDEX idx_collections_deleted_at ON catalog.collections (deleted_at);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.collections
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- catalog.collection_rules
CREATE TABLE catalog.collection_rules (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    collection_id UUID        NOT NULL REFERENCES catalog.collections(id) ON DELETE CASCADE,
    field         TEXT        NOT NULL CHECK (field IN ('TAG')),
    operator      TEXT        NOT NULL CHECK (operator IN ('EQUALS', 'NOT_EQUALS', 'CONTAINS')),
    value         TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_collection_rules_collection_id ON catalog.collection_rules (collection_id);

-- catalog.collection_products
CREATE TABLE catalog.collection_products (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    collection_id UUID        NOT NULL REFERENCES catalog.collections(id) ON DELETE CASCADE,
    product_id    UUID        NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    position      INTEGER     NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (collection_id, product_id)
);

CREATE INDEX idx_collection_products_collection_id ON catalog.collection_products (collection_id);
CREATE INDEX idx_collection_products_product_id    ON catalog.collection_products (product_id);

-- Permissions
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000029', 'collection:read',    'collection', 'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000030', 'collection:write',   'collection', 'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000031', 'collection:publish', 'collection', 'publish', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000032', 'collection:delete',  'collection', 'delete',  clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name IN ('STAFF', 'MANAGER', 'OWNER')
  AND p.name IN ('collection:read', 'collection:write', 'collection:publish', 'collection:delete')
ON CONFLICT DO NOTHING;
```

- [ ] **Step 2: Start app and verify migration runs**

```bash
./mvnw spring-boot:run -q 2>&1 | grep -E "(migration|ERROR|Started)" | head -20
```

Expected: Flyway logs `V13__create_collections.sql` as successfully applied.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V13__create_collections.sql
git commit -m "feat(db): add V13 collections migration"
```

---

## Task 2: Domain Models and Enums

**Files:**
- Create: `src/main/java/io/k2dv/garden/collection/model/CollectionType.java`
- Create: `src/main/java/io/k2dv/garden/collection/model/CollectionStatus.java`
- Create: `src/main/java/io/k2dv/garden/collection/model/CollectionRuleField.java`
- Create: `src/main/java/io/k2dv/garden/collection/model/CollectionRuleOperator.java`
- Create: `src/main/java/io/k2dv/garden/collection/model/Collection.java`
- Create: `src/main/java/io/k2dv/garden/collection/model/CollectionRule.java`
- Create: `src/main/java/io/k2dv/garden/collection/model/CollectionProduct.java`

- [ ] **Step 1: Write enums**

```java
// CollectionType.java
package io.k2dv.garden.collection.model;
public enum CollectionType { MANUAL, AUTOMATED }
```

```java
// CollectionStatus.java
package io.k2dv.garden.collection.model;
public enum CollectionStatus { DRAFT, ACTIVE }
```

```java
// CollectionRuleField.java
package io.k2dv.garden.collection.model;
public enum CollectionRuleField { TAG }
```

```java
// CollectionRuleOperator.java
package io.k2dv.garden.collection.model;
public enum CollectionRuleOperator { EQUALS, NOT_EQUALS, CONTAINS }
```

- [ ] **Step 2: Write Collection entity**

```java
// Collection.java
package io.k2dv.garden.collection.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "collections")
@Getter
@Setter
public class Collection extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String handle;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_type", nullable = false)
    private CollectionType collectionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionStatus status = CollectionStatus.DRAFT;

    @Column(name = "featured_image_id")
    private UUID featuredImageId;

    @Column(nullable = false)
    private boolean disjunctive = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

- [ ] **Step 3: Write CollectionRule entity**

`CollectionRule` does NOT extend `BaseEntity` — the DB table has no `updated_at` column.

```java
// CollectionRule.java
package io.k2dv.garden.collection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "collection_rules")
@Getter
@Setter
public class CollectionRule {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionRuleField field;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionRuleOperator operator;

    @Column(nullable = false)
    private String value;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
```

- [ ] **Step 4: Write CollectionProduct entity**

`CollectionProduct` does NOT extend `BaseEntity` — no `updated_at` column.

```java
// CollectionProduct.java
package io.k2dv.garden.collection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    schema = "catalog",
    name = "collection_products",
    uniqueConstraints = @UniqueConstraint(columnNames = {"collection_id", "product_id"})
)
@Getter
@Setter
public class CollectionProduct {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer position;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
```

- [ ] **Step 5: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/k2dv/garden/collection/model/
git commit -m "feat(collection): add domain models and enums"
```

---

## Task 3: Repositories

**Files:**
- Create: `src/main/java/io/k2dv/garden/collection/repository/CollectionRepository.java`
- Create: `src/main/java/io/k2dv/garden/collection/repository/CollectionRuleRepository.java`
- Create: `src/main/java/io/k2dv/garden/collection/repository/CollectionProductRepository.java`

- [ ] **Step 1: Add `findAllByStatusAndDeletedAtIsNull` to ProductRepository**

Open `src/main/java/io/k2dv/garden/product/repository/ProductRepository.java` and add:

```java
List<Product> findAllByStatusAndDeletedAtIsNull(ProductStatus status);
```

Also add the import: `import io.k2dv.garden.product.model.ProductStatus;` and `import java.util.List;` if not already present.

This method is used by `CollectionMembershipService` during sync — only ACTIVE, non-deleted products qualify for automated collection membership. Using ACTIVE-only at sync time keeps `collection_products` consistent with the remove-on-archive trigger (otherwise a subsequent sync could re-add archived products).

- [ ] **Step 2: Write repositories**

```java
// CollectionRepository.java
package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID>, JpaSpecificationExecutor<Collection> {
    Optional<Collection> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Collection> findByHandleAndDeletedAtIsNullAndStatus(String handle, io.k2dv.garden.collection.model.CollectionStatus status);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
    List<Collection> findAllByCollectionTypeAndDeletedAtIsNull(CollectionType type);
}
```

```java
// CollectionRuleRepository.java
package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.CollectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CollectionRuleRepository extends JpaRepository<CollectionRule, UUID> {
    List<CollectionRule> findByCollectionIdOrderByCreatedAtAsc(UUID collectionId);
    void deleteByCollectionId(UUID collectionId);
}
```

```java
// CollectionProductRepository.java
package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.CollectionProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionProductRepository extends JpaRepository<CollectionProduct, UUID> {
    Page<CollectionProduct> findByCollectionIdOrderByPositionAscCreatedAtAsc(UUID collectionId, Pageable pageable);
    List<CollectionProduct> findByCollectionId(UUID collectionId);
    Optional<CollectionProduct> findByCollectionIdAndProductId(UUID collectionId, UUID productId);
    boolean existsByCollectionIdAndProductId(UUID collectionId, UUID productId);
    void deleteByCollectionId(UUID collectionId);
    void deleteByCollectionIdAndProductId(UUID collectionId, UUID productId);
    void deleteByCollectionIdAndProductIdIn(UUID collectionId, Collection<UUID> productIds);
    void deleteByProductId(UUID productId);

    @Query("SELECT MAX(cp.position) FROM CollectionProduct cp WHERE cp.collectionId = :collectionId")
    Integer findMaxPositionByCollectionId(UUID collectionId);
}
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/k2dv/garden/collection/repository/
git commit -m "feat(collection): add repositories"
```

---

## Task 4: DTOs

**Files:**
- Create all request and response records under `src/main/java/io/k2dv/garden/collection/dto/`

- [ ] **Step 1: Write request DTOs**

```java
// CreateCollectionRequest.java
package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCollectionRequest(
    @NotBlank String title,
    String handle,
    String description,
    @NotNull CollectionType collectionType,
    boolean disjunctive,
    UUID featuredImageId
) {}
```

```java
// UpdateCollectionRequest.java
package io.k2dv.garden.collection.dto.request;

import java.util.UUID;

public record UpdateCollectionRequest(
    String title,
    String handle,
    String description,
    Boolean disjunctive,
    UUID featuredImageId
) {}
```

```java
// CollectionStatusRequest.java
package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionStatus;
import jakarta.validation.constraints.NotNull;

public record CollectionStatusRequest(@NotNull CollectionStatus status) {}
```

```java
// AddCollectionProductRequest.java
package io.k2dv.garden.collection.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCollectionProductRequest(@NotNull UUID productId) {}
```

```java
// UpdateCollectionProductPositionRequest.java
package io.k2dv.garden.collection.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateCollectionProductPositionRequest(@NotNull Integer position) {}
```

```java
// CreateCollectionRuleRequest.java
package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionRuleField;
import io.k2dv.garden.collection.model.CollectionRuleOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCollectionRuleRequest(
    @NotNull CollectionRuleField field,
    @NotNull CollectionRuleOperator operator,
    @NotBlank String value
) {}
```

```java
// CollectionFilterRequest.java
package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;

public record CollectionFilterRequest(
    CollectionType collectionType,
    CollectionStatus status,
    String titleContains
) {}
```

- [ ] **Step 2: Write response DTOs**

```java
// CollectionRuleResponse.java
package io.k2dv.garden.collection.dto.response;

import io.k2dv.garden.collection.model.CollectionRuleField;
import io.k2dv.garden.collection.model.CollectionRuleOperator;
import java.time.Instant;
import java.util.UUID;

public record CollectionRuleResponse(
    UUID id,
    CollectionRuleField field,
    CollectionRuleOperator operator,
    String value,
    Instant createdAt
) {}
```

```java
// CollectionProductResponse.java
package io.k2dv.garden.collection.dto.response;

import java.util.UUID;

public record CollectionProductResponse(
    UUID id,
    UUID productId,
    String title,
    String handle,
    int position
) {}
```

```java
// AdminCollectionResponse.java
package io.k2dv.garden.collection.dto.response;

import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminCollectionResponse(
    UUID id,
    String title,
    String handle,
    String description,
    CollectionType collectionType,
    CollectionStatus status,
    UUID featuredImageId,
    boolean disjunctive,
    long productCount,
    List<CollectionRuleResponse> rules,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {}
```

```java
// AdminCollectionSummaryResponse.java
package io.k2dv.garden.collection.dto.response;

import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import java.time.Instant;
import java.util.UUID;

public record AdminCollectionSummaryResponse(
    UUID id,
    String title,
    String handle,
    CollectionType collectionType,
    CollectionStatus status,
    long productCount,
    Instant createdAt
) {}
```

```java
// CollectionSummaryResponse.java
package io.k2dv.garden.collection.dto.response;

import java.util.UUID;

public record CollectionSummaryResponse(
    UUID id,
    String title,
    String handle
) {}
```

```java
// CollectionDetailResponse.java
package io.k2dv.garden.collection.dto.response;

import java.util.UUID;

public record CollectionDetailResponse(
    UUID id,
    String title,
    String handle,
    String description,
    UUID featuredImageId
) {}
```

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/k2dv/garden/collection/dto/
git commit -m "feat(collection): add request and response DTOs"
```

---

## Task 5: CollectionSpecification

**Files:**
- Create: `src/main/java/io/k2dv/garden/collection/specification/CollectionSpecification.java`

- [ ] **Step 1: Write specification**

```java
package io.k2dv.garden.collection.specification;

import io.k2dv.garden.collection.dto.request.CollectionFilterRequest;
import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CollectionSpecification {

    private CollectionSpecification() {}

    public static Specification<Collection> toSpec(CollectionFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (f != null) {
                if (f.collectionType() != null) {
                    predicates.add(cb.equal(root.get("collectionType"), f.collectionType()));
                }
                if (f.status() != null) {
                    predicates.add(cb.equal(root.get("status"), f.status()));
                }
                if (f.titleContains() != null && !f.titleContains().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("title")),
                            "%" + f.titleContains().toLowerCase() + "%"));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Collection> storefrontSpec() {
        return (root, query, cb) -> cb.and(
            cb.isNull(root.get("deletedAt")),
            cb.equal(root.get("status"), CollectionStatus.ACTIVE)
        );
    }
}
```

- [ ] **Step 2: Compile**

```bash
./mvnw compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/k2dv/garden/collection/specification/
git commit -m "feat(collection): add CollectionSpecification"
```

---

## Task 6: CollectionMembershipService — Unit Tests First

**Files:**
- Create: `src/test/java/io/k2dv/garden/collection/service/CollectionMembershipServiceTest.java`
- Create: `src/main/java/io/k2dv/garden/collection/service/CollectionMembershipService.java`

This is the core rule-evaluation logic. Write unit tests first (no DB needed) using plain Java objects.

- [ ] **Step 1: Write unit tests**

```java
package io.k2dv.garden.collection.service;

import io.k2dv.garden.collection.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionMembershipServiceTest {

    // Helper: build a CollectionRule
    private CollectionRule rule(CollectionRuleField field, CollectionRuleOperator op, String value) {
        CollectionRule r = new CollectionRule();
        r.setField(field);
        r.setOperator(op);
        r.setValue(value);
        return r;
    }

    @Test
    void equalsRule_matchesExactTagCaseInsensitive() {
        var tags = Set.of("SALE", "new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void equalsRule_noMatch_returnsFalse() {
        var tags = Set.of("new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void notEqualsRule_tagAbsent_returnsTrue() {
        var tags = Set.of("new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.NOT_EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void notEqualsRule_tagPresent_returnsFalse() {
        var tags = Set.of("sale");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.NOT_EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void containsRule_substringMatch_returnsTrue() {
        var tags = Set.of("summer-sale");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.CONTAINS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void containsRule_caseInsensitive() {
        var tags = Set.of("SUMMER-SALE");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.CONTAINS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void containsRule_noSubstringMatch_returnsFalse() {
        var tags = Set.of("new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.CONTAINS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void andLogic_allRulesMustMatch() {
        var tags = Set.of("sale", "new");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void andLogic_oneRuleFails_returnsFalse() {
        var tags = Set.of("sale");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void orLogic_oneRuleMatches_returnsTrue() {
        var tags = Set.of("sale");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, true)).isTrue();
    }

    @Test
    void orLogic_noRuleMatches_returnsFalse() {
        var tags = Set.of("other");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, true)).isFalse();
    }

    @Test
    void emptyRules_returnsFalse() {
        var tags = Set.of("sale");
        assertThat(CollectionMembershipService.evaluate(tags, List.of(), false)).isFalse();
    }

    @Test
    void emptyTags_noRuleMatches() {
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(Set.of(), rules, false)).isFalse();
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure (class does not exist yet)**

```bash
./mvnw test -pl . -Dtest=CollectionMembershipServiceTest -q 2>&1 | tail -20
```

Expected: compilation error — `CollectionMembershipService` not found.

- [ ] **Step 3: Write CollectionMembershipService with the static `evaluate` method and the sync logic**

```java
package io.k2dv.garden.collection.service;

import io.k2dv.garden.collection.model.*;
import io.k2dv.garden.collection.repository.*;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.model.ProductTag;
import io.k2dv.garden.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionMembershipService {

    private final CollectionRepository collectionRepo;
    private final CollectionRuleRepository ruleRepo;
    private final CollectionProductRepository cpRepo;
    private final ProductRepository productRepo;

    /**
     * Pure evaluation — no DB access. package-private for unit tests.
     * @param tagNames lowercased tag names of the product
     * @param rules    rules to evaluate
     * @param disjunctive true = OR logic, false = AND logic
     */
    static boolean evaluate(Set<String> tagNames, List<CollectionRule> rules, boolean disjunctive) {
        if (rules.isEmpty()) return false;
        Set<String> lowerTags = tagNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
        if (disjunctive) {
            return rules.stream().anyMatch(r -> matchesRule(lowerTags, r));
        } else {
            return rules.stream().allMatch(r -> matchesRule(lowerTags, r));
        }
    }

    private static boolean matchesRule(Set<String> lowerTags, CollectionRule rule) {
        String lowerValue = rule.getValue().toLowerCase();
        return switch (rule.getOperator()) {
            case EQUALS     -> lowerTags.contains(lowerValue);
            case NOT_EQUALS -> !lowerTags.contains(lowerValue);
            case CONTAINS   -> lowerTags.stream().anyMatch(t -> t.contains(lowerValue));
        };
    }

    /**
     * Full sync for one automated collection: recompute all qualifying products.
     * Called when a collection's rules change.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void syncCollectionMembership(UUID collectionId) {
        Collection collection = collectionRepo.findById(collectionId).orElseThrow();
        List<CollectionRule> rules = ruleRepo.findByCollectionIdOrderByCreatedAtAsc(collectionId);

        if (rules.isEmpty()) {
            cpRepo.deleteByCollectionId(collectionId);
            return;
        }

        List<Product> allProducts = productRepo.findAllByStatusAndDeletedAtIsNull(ProductStatus.ACTIVE);

        Set<UUID> qualifyingIds = allProducts.stream()
            .filter(p -> {
                Set<String> tagNames = p.getTags().stream()
                    .map(ProductTag::getName).collect(Collectors.toSet());
                return evaluate(tagNames, rules, collection.isDisjunctive());
            })
            .map(Product::getId)
            .collect(Collectors.toSet());

        List<CollectionProduct> existing = cpRepo.findByCollectionId(collectionId);
        Set<UUID> existingIds = existing.stream()
            .map(CollectionProduct::getProductId).collect(Collectors.toSet());

        Set<UUID> toRemove = new HashSet<>(existingIds);
        toRemove.removeAll(qualifyingIds);
        Set<UUID> toAdd = new HashSet<>(qualifyingIds);
        toAdd.removeAll(existingIds);

        if (!toRemove.isEmpty()) {
            cpRepo.deleteByCollectionIdAndProductIdIn(collectionId, toRemove);
        }

        if (!toAdd.isEmpty()) {
            Integer maxPos = cpRepo.findMaxPositionByCollectionId(collectionId);
            int nextPos = (maxPos != null ? maxPos : 0) + 1;

            List<Product> toAddSorted = productRepo.findAllById(toAdd).stream()
                .sorted(Comparator.comparing(Product::getCreatedAt))
                .toList();

            for (Product p : toAddSorted) {
                CollectionProduct cp = new CollectionProduct();
                cp.setCollectionId(collectionId);
                cp.setProductId(p.getId());
                cp.setPosition(nextPos++);
                cpRepo.save(cp);
            }
        }
    }

    /**
     * Re-evaluate one product across all AUTOMATED collections.
     * Called by ProductService when a product's tags change.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void syncCollectionsForProduct(UUID productId, Set<String> newTagNames) {
        List<Collection> automated = collectionRepo.findAllByCollectionTypeAndDeletedAtIsNull(CollectionType.AUTOMATED);
        for (Collection collection : automated) {
            List<CollectionRule> rules = ruleRepo.findByCollectionIdOrderByCreatedAtAsc(collection.getId());
            boolean qualifies = evaluate(newTagNames, rules, collection.isDisjunctive());
            boolean isMember  = cpRepo.existsByCollectionIdAndProductId(collection.getId(), productId);

            if (qualifies && !isMember) {
                Integer maxPos = cpRepo.findMaxPositionByCollectionId(collection.getId());
                CollectionProduct cp = new CollectionProduct();
                cp.setCollectionId(collection.getId());
                cp.setProductId(productId);
                cp.setPosition((maxPos != null ? maxPos : 0) + 1);
                cpRepo.save(cp);
            } else if (!qualifies && isMember) {
                cpRepo.deleteByCollectionIdAndProductId(collection.getId(), productId);
            }
        }
    }

    /**
     * Remove a product from ALL collections (manual + automated).
     * Called when a product is soft-deleted or set to ARCHIVED.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void removeProductFromAllCollections(UUID productId) {
        cpRepo.deleteByProductId(productId);
    }
}
```

- [ ] **Step 4: Run unit tests — expect PASS**

```bash
./mvnw test -pl . -Dtest=CollectionMembershipServiceTest -q 2>&1 | tail -10
```

Expected: `Tests run: 13, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/collection/service/CollectionMembershipService.java \
        src/test/java/io/k2dv/garden/collection/service/CollectionMembershipServiceTest.java
git commit -m "feat(collection): add CollectionMembershipService with unit tests"
```

---

## Task 7: CollectionService

**Files:**
- Create: `src/main/java/io/k2dv/garden/collection/service/CollectionService.java`

This service requires the DB to be running. Tests in Task 9.

- [ ] **Step 1: Write CollectionService**

```java
package io.k2dv.garden.collection.service;

import io.k2dv.garden.collection.dto.request.*;
import io.k2dv.garden.collection.dto.response.*;
import io.k2dv.garden.collection.model.*;
import io.k2dv.garden.collection.repository.*;
import io.k2dv.garden.collection.specification.CollectionSpecification;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRepository collectionRepo;
    private final CollectionRuleRepository ruleRepo;
    private final CollectionProductRepository cpRepo;
    private final ProductRepository productRepo;
    private final CollectionMembershipService membershipService;

    // --- Collection CRUD ---

    @Transactional
    public AdminCollectionResponse create(CreateCollectionRequest req) {
        String handle = req.handle() != null ? req.handle() : slugify(req.title());
        checkHandleUnique(handle, null);

        Collection c = new Collection();
        c.setTitle(req.title());
        c.setHandle(handle);
        c.setDescription(req.description());
        c.setCollectionType(req.collectionType());
        c.setDisjunctive(req.disjunctive());
        c.setFeaturedImageId(req.featuredImageId());
        c.setStatus(CollectionStatus.DRAFT);
        Collection saved = collectionRepo.save(c);
        return toAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public AdminCollectionResponse getAdmin(UUID id) {
        return toAdminResponse(findActiveOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminCollectionSummaryResponse> listAdmin(CollectionFilterRequest filter, Pageable pageable) {
        Page<Collection> page = collectionRepo.findAll(CollectionSpecification.toSpec(filter), pageable);
        return PagedResult.of(page, c -> toAdminSummary(c));
    }

    @Transactional
    public AdminCollectionResponse update(UUID id, UpdateCollectionRequest req) {
        Collection c = findActiveOrThrow(id);
        if (req.title() != null)            c.setTitle(req.title());
        if (req.handle() != null) {
            checkHandleUnique(req.handle(), id);
            c.setHandle(req.handle());
        }
        if (req.description() != null)      c.setDescription(req.description());
        if (req.disjunctive() != null)      c.setDisjunctive(req.disjunctive());
        if (req.featuredImageId() != null)  c.setFeaturedImageId(req.featuredImageId());
        return toAdminResponse(collectionRepo.save(c));
    }

    @Transactional
    public AdminCollectionResponse changeStatus(UUID id, CollectionStatusRequest req) {
        Collection c = findActiveOrThrow(id);
        c.setStatus(req.status());
        return toAdminResponse(collectionRepo.save(c));
    }

    @Transactional
    public void softDelete(UUID id) {
        Collection c = findActiveOrThrow(id);
        cpRepo.deleteByCollectionId(id);
        ruleRepo.deleteByCollectionId(id);
        c.setDeletedAt(Instant.now());
        collectionRepo.save(c);
    }

    // --- Rules ---

    @Transactional(readOnly = true)
    public List<CollectionRuleResponse> listRules(UUID collectionId) {
        findActiveOrThrow(collectionId);
        return ruleRepo.findByCollectionIdOrderByCreatedAtAsc(collectionId).stream()
            .map(this::toRuleResponse).toList();
    }

    @Transactional
    public CollectionRuleResponse addRule(UUID collectionId, CreateCollectionRuleRequest req) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() != CollectionType.AUTOMATED) {
            throw new ValidationException("RULES_NOT_SUPPORTED", "Rules are only supported on AUTOMATED collections");
        }
        CollectionRule rule = new CollectionRule();
        rule.setCollectionId(collectionId);
        rule.setField(req.field());
        rule.setOperator(req.operator());
        rule.setValue(req.value());
        CollectionRule saved = ruleRepo.save(rule);
        membershipService.syncCollectionMembership(collectionId);
        return toRuleResponse(saved);
    }

    @Transactional
    public void deleteRule(UUID collectionId, UUID ruleId) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() != CollectionType.AUTOMATED) {
            throw new ValidationException("RULES_NOT_SUPPORTED", "Rules are only supported on AUTOMATED collections");
        }
        CollectionRule rule = ruleRepo.findById(ruleId)
            .filter(r -> r.getCollectionId().equals(collectionId))
            .orElseThrow(() -> new NotFoundException("RULE_NOT_FOUND", "Rule not found"));
        ruleRepo.delete(rule);
        membershipService.syncCollectionMembership(collectionId);
    }

    // --- Manual product membership ---

    @Transactional(readOnly = true)
    public PagedResult<CollectionProductResponse> listProducts(UUID collectionId, Pageable pageable) {
        findActiveOrThrow(collectionId);
        Page<CollectionProduct> page = cpRepo.findByCollectionIdOrderByPositionAscCreatedAtAsc(collectionId, pageable);
        return PagedResult.of(page, cp -> toProductResponse(cp));
    }

    @Transactional
    public CollectionProductResponse addProduct(UUID collectionId, AddCollectionProductRequest req) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() == CollectionType.AUTOMATED) {
            throw new ValidationException("AUTOMATED_MEMBERSHIP", "Cannot manually add products to an AUTOMATED collection");
        }
        if (cpRepo.existsByCollectionIdAndProductId(collectionId, req.productId())) {
            throw new ConflictException("PRODUCT_ALREADY_IN_COLLECTION", "Product is already in this collection");
        }
        Product product = productRepo.findByIdAndDeletedAtIsNull(req.productId())
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        Integer maxPos = cpRepo.findMaxPositionByCollectionId(collectionId);
        CollectionProduct cp = new CollectionProduct();
        cp.setCollectionId(collectionId);
        cp.setProductId(req.productId());
        cp.setPosition((maxPos != null ? maxPos : 0) + 1);
        CollectionProduct saved = cpRepo.save(cp);
        return toProductResponse(saved, product);
    }

    @Transactional
    public void removeProduct(UUID collectionId, UUID productId) {
        Collection c = findActiveOrThrow(collectionId);
        if (c.getCollectionType() == CollectionType.AUTOMATED) {
            throw new ValidationException("AUTOMATED_MEMBERSHIP", "Cannot manually remove products from an AUTOMATED collection");
        }
        if (!cpRepo.existsByCollectionIdAndProductId(collectionId, productId)) {
            throw new NotFoundException("PRODUCT_NOT_IN_COLLECTION", "Product is not in this collection");
        }
        cpRepo.deleteByCollectionIdAndProductId(collectionId, productId);
    }

    @Transactional
    public CollectionProductResponse updateProductPosition(UUID collectionId, UUID productId,
                                                           UpdateCollectionProductPositionRequest req) {
        findActiveOrThrow(collectionId);
        CollectionProduct cp = cpRepo.findByCollectionIdAndProductId(collectionId, productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_IN_COLLECTION", "Product is not in this collection"));
        cp.setPosition(req.position());
        CollectionProduct saved = cpRepo.save(cp);
        return toProductResponse(saved);
    }

    // --- Storefront ---

    @Transactional(readOnly = true)
    public PagedResult<CollectionSummaryResponse> listStorefront(Pageable pageable) {
        Page<Collection> page = collectionRepo.findAll(CollectionSpecification.storefrontSpec(), pageable);
        return PagedResult.of(page, c -> new CollectionSummaryResponse(c.getId(), c.getTitle(), c.getHandle()));
    }

    @Transactional(readOnly = true)
    public CollectionDetailResponse getByHandle(String handle) {
        Collection c = collectionRepo.findByHandleAndDeletedAtIsNullAndStatus(handle, CollectionStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("COLLECTION_NOT_FOUND", "Collection not found"));
        return new CollectionDetailResponse(c.getId(), c.getTitle(), c.getHandle(),
            c.getDescription(), c.getFeaturedImageId());
    }

    @Transactional(readOnly = true)
    public PagedResult<CollectionProductResponse> listProductsStorefront(String handle, Pageable pageable) {
        Collection c = collectionRepo.findByHandleAndDeletedAtIsNullAndStatus(handle, CollectionStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("COLLECTION_NOT_FOUND", "Collection not found"));
        Page<CollectionProduct> page = cpRepo.findByCollectionIdOrderByPositionAscCreatedAtAsc(c.getId(), pageable);
        return PagedResult.of(page, cp -> {
            Product p = productRepo.findByIdAndDeletedAtIsNull(cp.getProductId())
                .filter(prod -> prod.getStatus() == io.k2dv.garden.product.model.ProductStatus.ACTIVE)
                .orElse(null);
            if (p == null) return null;
            return toProductResponse(cp, p);
        });
    }

    // --- Helpers ---

    private Collection findActiveOrThrow(UUID id) {
        return collectionRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("COLLECTION_NOT_FOUND", "Collection not found"));
    }

    private void checkHandleUnique(String handle, UUID excludeId) {
        boolean conflict = excludeId == null
            ? collectionRepo.existsByHandleAndDeletedAtIsNull(handle)
            : collectionRepo.existsByHandleAndDeletedAtIsNullAndIdNot(handle, excludeId);
        if (conflict) {
            throw new ConflictException("HANDLE_CONFLICT", "A collection with this handle already exists");
        }
    }

    static String slugify(String title) {
        String slug = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "collection" : slug;
    }

    private AdminCollectionResponse toAdminResponse(Collection c) {
        long productCount = cpRepo.findByCollectionId(c.getId()).size();
        List<CollectionRuleResponse> rules = ruleRepo.findByCollectionIdOrderByCreatedAtAsc(c.getId())
            .stream().map(this::toRuleResponse).toList();
        return new AdminCollectionResponse(c.getId(), c.getTitle(), c.getHandle(), c.getDescription(),
            c.getCollectionType(), c.getStatus(), c.getFeaturedImageId(), c.isDisjunctive(),
            productCount, rules, c.getCreatedAt(), c.getUpdatedAt(), c.getDeletedAt());
    }

    private AdminCollectionSummaryResponse toAdminSummary(Collection c) {
        long productCount = cpRepo.countByCollectionId(c.getId());
        return new AdminCollectionSummaryResponse(c.getId(), c.getTitle(), c.getHandle(),
            c.getCollectionType(), c.getStatus(), productCount, c.getCreatedAt());
    }

    private CollectionRuleResponse toRuleResponse(CollectionRule r) {
        return new CollectionRuleResponse(r.getId(), r.getField(), r.getOperator(), r.getValue(), r.getCreatedAt());
    }

    private CollectionProductResponse toProductResponse(CollectionProduct cp) {
        Product p = productRepo.findById(cp.getProductId()).orElseThrow();
        return new CollectionProductResponse(cp.getId(), cp.getProductId(), p.getTitle(), p.getHandle(), cp.getPosition());
    }

    private CollectionProductResponse toProductResponse(CollectionProduct cp, Product p) {
        return new CollectionProductResponse(cp.getId(), cp.getProductId(), p.getTitle(), p.getHandle(), cp.getPosition());
    }
}
```

Note: `toAdminSummary` calls `cpRepo.countByCollectionId` — add this method to `CollectionProductRepository`:

```java
long countByCollectionId(UUID collectionId);
```

- [ ] **Step 2: Add `countByCollectionId` to CollectionProductRepository**

Open `src/main/java/io/k2dv/garden/collection/repository/CollectionProductRepository.java` and add:

```java
long countByCollectionId(UUID collectionId);
```

- [ ] **Step 3: Compile**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/k2dv/garden/collection/service/CollectionService.java \
        src/main/java/io/k2dv/garden/collection/repository/CollectionProductRepository.java
git commit -m "feat(collection): add CollectionService"
```

---

## Task 8: Controllers

**Files:**
- Create: `src/main/java/io/k2dv/garden/collection/controller/AdminCollectionController.java`
- Create: `src/main/java/io/k2dv/garden/collection/controller/StorefrontCollectionController.java`

- [ ] **Step 1: Write AdminCollectionController**

```java
package io.k2dv.garden.collection.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.collection.dto.request.*;
import io.k2dv.garden.collection.dto.response.*;
import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import io.k2dv.garden.collection.service.CollectionService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/collections")
@RequiredArgsConstructor
public class AdminCollectionController {

    private final CollectionService collectionService;

    @PostMapping
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> create(
            @Valid @RequestBody CreateCollectionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(collectionService.create(req)));
    }

    @GetMapping
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<PagedResult<AdminCollectionSummaryResponse>>> list(
            @RequestParam(required = false) CollectionType collectionType,
            @RequestParam(required = false) CollectionStatus status,
            @RequestParam(required = false) String titleContains,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        var filter = new CollectionFilterRequest(collectionType, status, titleContains);
        return ResponseEntity.ok(ApiResponse.of(
            collectionService.listAdmin(filter, PageRequest.of(page, clampedSize, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.getAdmin(id)));
    }

    @PatchMapping("/{id}")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> update(
            @PathVariable UUID id, @RequestBody UpdateCollectionRequest req) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.update(id, req)));
    }

    @PatchMapping("/{id}/status")
    @HasPermission("collection:publish")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody CollectionStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.changeStatus(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("collection:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        collectionService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/products")
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<PagedResult<CollectionProductResponse>>> listProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
            collectionService.listProducts(id, PageRequest.of(page, clampedSize))));
    }

    @PostMapping("/{id}/products")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<CollectionProductResponse>> addProduct(
            @PathVariable UUID id, @Valid @RequestBody AddCollectionProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(collectionService.addProduct(id, req)));
    }

    @DeleteMapping("/{id}/products/{productId}")
    @HasPermission("collection:write")
    public ResponseEntity<Void> removeProduct(@PathVariable UUID id, @PathVariable UUID productId) {
        collectionService.removeProduct(id, productId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/products/{productId}/position")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<CollectionProductResponse>> updateProductPosition(
            @PathVariable UUID id, @PathVariable UUID productId,
            @Valid @RequestBody UpdateCollectionProductPositionRequest req) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.updateProductPosition(id, productId, req)));
    }

    @GetMapping("/{id}/rules")
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<List<CollectionRuleResponse>>> listRules(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.listRules(id)));
    }

    @PostMapping("/{id}/rules")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<CollectionRuleResponse>> addRule(
            @PathVariable UUID id, @Valid @RequestBody CreateCollectionRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(collectionService.addRule(id, req)));
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    @HasPermission("collection:write")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id, @PathVariable UUID ruleId) {
        collectionService.deleteRule(id, ruleId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write StorefrontCollectionController**

```java
package io.k2dv.garden.collection.controller;

import io.k2dv.garden.collection.dto.response.*;
import io.k2dv.garden.collection.service.CollectionService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
public class StorefrontCollectionController {

    private final CollectionService collectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<CollectionSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
            collectionService.listStorefront(PageRequest.of(page, clampedSize, Sort.by("createdAt").ascending()))));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.getByHandle(handle)));
    }

    @GetMapping("/{handle}/products")
    public ResponseEntity<ApiResponse<PagedResult<CollectionProductResponse>>> listProducts(
            @PathVariable String handle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
            collectionService.listProductsStorefront(handle, PageRequest.of(page, clampedSize))));
    }
}
```

- [ ] **Step 3: Compile**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/k2dv/garden/collection/controller/
git commit -m "feat(collection): add AdminCollectionController and StorefrontCollectionController"
```

---

## Task 9: Integrate with ProductService

**Files:**
- Modify: `src/main/java/io/k2dv/garden/product/service/ProductService.java`

`ProductService` must call `CollectionMembershipService` when:
1. Product tags are updated (in `update()`)
2. Product status changes to `ARCHIVED` (in `changeStatus()`)
3. Product is soft-deleted (in `softDelete()`)

- [ ] **Step 1: Inject CollectionMembershipService into ProductService**

Add `CollectionMembershipService` to the field list. Because `CollectionMembershipService` depends on `ProductRepository` (not `ProductService`), there is no circular dependency.

Edit `ProductService.java`:
- Add to the field declarations: `private final CollectionMembershipService collectionMembershipService;`

- [ ] **Step 2: Call sync on tag update**

In the `update()` method, after the `if (req.tags() != null)` block, add:

```java
if (req.tags() != null) {
    Set<String> newTagNames = p.getTags().stream()
        .map(io.k2dv.garden.product.model.ProductTag::getName)
        .collect(java.util.stream.Collectors.toSet());
    collectionMembershipService.syncCollectionsForProduct(p.getId(), newTagNames);
}
```

- [ ] **Step 3: Call removal on ARCHIVED status**

In `changeStatus()`, **after** `p.setStatus(req.status())` and **before** `productRepo.save(p)`, add the following block. The guard is required — without it, every status change (including DRAFT → ACTIVE) would incorrectly remove the product from all collections:

```java
if (req.status() == ProductStatus.ARCHIVED) {
    collectionMembershipService.removeProductFromAllCollections(p.getId());
}
```

- [ ] **Step 4: Call removal on soft-delete**

In `softDelete()`, before setting `deletedAt`, add:

```java
collectionMembershipService.removeProductFromAllCollections(p.getId());
```

- [ ] **Step 5: Compile and run existing product tests**

```bash
./mvnw test -pl . -Dtest=ProductServiceIT -q 2>&1 | tail -10
```

Expected: All existing product tests still pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/k2dv/garden/product/service/ProductService.java
git commit -m "feat(collection): integrate CollectionMembershipService into ProductService"
```

---

## Task 10: Integration Tests

**Files:**
- Create: `src/test/java/io/k2dv/garden/collection/service/CollectionServiceIT.java`

- [ ] **Step 1: Write integration tests**

```java
package io.k2dv.garden.collection.service;

import io.k2dv.garden.collection.dto.request.*;
import io.k2dv.garden.collection.model.*;
import io.k2dv.garden.collection.repository.CollectionProductRepository;
import io.k2dv.garden.collection.repository.CollectionRuleRepository;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionServiceIT extends AbstractIntegrationTest {

    @Autowired CollectionService collectionService;
    @Autowired ProductService productService;
    @Autowired CollectionProductRepository cpRepo;
    @Autowired CollectionRuleRepository ruleRepo;

    // ---- helpers ----

    private var createManual(String title) {
        return collectionService.create(new CreateCollectionRequest(
            title, null, null, CollectionType.MANUAL, false, null));
    }

    private var createAutomated(String title) {
        return collectionService.create(new CreateCollectionRequest(
            title, null, null, CollectionType.AUTOMATED, false, null));
    }

    private var createProduct(String title, String... tags) {
        return productService.create(new CreateProductRequest(title, null, null, null, null, List.of(tags)));
    }

    // ---- CRUD ----

    @Test
    void create_persistsWithDraftStatus() {
        var resp = createManual("Summer Sale");
        assertThat(resp.title()).isEqualTo("Summer Sale");
        assertThat(resp.handle()).isEqualTo("summer-sale");
        assertThat(resp.status()).isEqualTo(CollectionStatus.DRAFT);
        assertThat(resp.collectionType()).isEqualTo(CollectionType.MANUAL);
    }

    @Test
    void create_duplicateHandle_throwsConflict() {
        createManual("Shirts");
        assertThatThrownBy(() -> createManual("Shirts")).isInstanceOf(ConflictException.class);
    }

    @Test
    void update_changesTitle() {
        var c = createManual("Old Title");
        var updated = collectionService.update(c.id(), new UpdateCollectionRequest("New Title", null, null, null, null));
        assertThat(updated.title()).isEqualTo("New Title");
    }

    @Test
    void changeStatus_activatesCollection() {
        var c = createManual("Promo");
        collectionService.changeStatus(c.id(), new CollectionStatusRequest(CollectionStatus.ACTIVE));
        assertThat(collectionService.getAdmin(c.id()).status()).isEqualTo(CollectionStatus.ACTIVE);
    }

    @Test
    void softDelete_removesFromAdminGet() {
        var c = createManual("Gone");
        collectionService.softDelete(c.id());
        assertThatThrownBy(() -> collectionService.getAdmin(c.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void softDelete_removesCollectionProductsAndRules() {
        var c = createAutomated("Auto");
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
            CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        var p = createProduct("Widget", "sale");
        collectionService.softDelete(c.id());
        assertThat(ruleRepo.findByCollectionIdOrderByCreatedAtAsc(c.id())).isEmpty();
        assertThat(cpRepo.findByCollectionId(c.id())).isEmpty();
    }

    @Test
    void handleUniqueness_duplicateHandleReturnsConflict() {
        collectionService.create(new CreateCollectionRequest("Hats", "hats", null, CollectionType.MANUAL, false, null));
        assertThatThrownBy(() ->
            collectionService.create(new CreateCollectionRequest("Other", "hats", null, CollectionType.MANUAL, false, null))
        ).isInstanceOf(ConflictException.class);
    }

    // ---- Manual product membership ----

    @Test
    void addProduct_appendsWithPosition() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        var cp = collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        assertThat(cp.productId()).isEqualTo(p.id());
        assertThat(cp.position()).isEqualTo(1);
    }

    @Test
    void addProduct_secondProduct_positionIncremented() {
        var c = createManual("Tops");
        var p1 = createProduct("T-Shirt");
        var p2 = createProduct("Tank Top");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p1.id()));
        var cp2 = collectionService.addProduct(c.id(), new AddCollectionProductRequest(p2.id()));
        assertThat(cp2.position()).isEqualTo(2);
    }

    @Test
    void addProduct_duplicate_throwsConflict() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        assertThatThrownBy(() ->
            collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()))
        ).isInstanceOf(ConflictException.class);
    }

    @Test
    void removeProduct_removesMembership() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        collectionService.removeProduct(c.id(), p.id());
        assertThat(cpRepo.findByCollectionIdAndProductId(c.id(), p.id())).isEmpty();
    }

    @Test
    void removeProduct_notMember_throwsNotFound() {
        var c = createManual("Tops");
        assertThatThrownBy(() -> collectionService.removeProduct(c.id(), UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePosition_setsVerbatim() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        var updated = collectionService.updateProductPosition(c.id(), p.id(),
            new UpdateCollectionProductPositionRequest(99));
        assertThat(updated.position()).isEqualTo(99);
    }

    @Test
    void addProduct_toAutomatedCollection_throwsValidation() {
        var c = createAutomated("Auto");
        var p = createProduct("Widget");
        assertThatThrownBy(() -> collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id())))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void removeProduct_fromAutomatedCollection_throwsValidation() {
        var c = createAutomated("Auto");
        var p = createProduct("Widget");
        assertThatThrownBy(() -> collectionService.removeProduct(c.id(), p.id()))
            .isInstanceOf(ValidationException.class);
    }

    // ---- Rules and automated membership ----

    @Test
    void addRule_triggersSync_addsQualifyingActiveProducts() {
        var c = createAutomated("Sale Items");
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));

        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
            CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));

        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();
    }

    @Test
    void addRule_doesNotSync_draftProducts() {
        var c = createAutomated("Sale Items");
        createProduct("Draft Widget", "sale"); // DRAFT — not synced

        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
            CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));

        assertThat(cpRepo.findByCollectionId(c.id())).isEmpty();
    }

    @Test
    void deleteRule_triggersSync_removesNonQualifyingProducts() {
        var c = createAutomated("Sale Items");
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        var rule = collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
            CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));

        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();

        collectionService.deleteRule(c.id(), rule.id());
        // No rules → no products
        assertThat(cpRepo.findByCollectionId(c.id())).isEmpty();
    }

    @Test
    void productTagUpdate_triggersMembershipSync() {
        var c = createAutomated("Sale Items");
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
            CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));

        var p = createProduct("Widget"); // no tags
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();

        // Add tag — should trigger sync
        productService.update(p.id(), new UpdateProductRequest(null, null, null, null, null, null, List.of("sale")));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();
    }

    @Test
    void productSoftDelete_removesFromManualCollection() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));

        productService.softDelete(p.id());

        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void productSoftDelete_removesFromAutomatedCollection() {
        var c = createAutomated("Sale Items");
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
            CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        // force membership by syncing
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();

        productService.softDelete(p.id());

        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void productArchived_removesFromManualCollection() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));

        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ARCHIVED));

        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void productArchived_removesFromAutomatedCollection() {
        var c = createAutomated("Sale Items");
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
            CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();

        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ARCHIVED));

        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void disjunctiveTrue_storedWithoutError() {
        var req = new CreateCollectionRequest("OR Collection", null, null, CollectionType.AUTOMATED, true, null);
        var resp = collectionService.create(req);
        assertThat(resp.disjunctive()).isTrue();
    }

    // ---- Storefront ----

    @Test
    void storefrontList_returnsOnlyActiveCollections() {
        createManual("Draft Collection"); // stays DRAFT
        var active = createManual("Active Collection");
        collectionService.changeStatus(active.id(), new CollectionStatusRequest(CollectionStatus.ACTIVE));

        var result = collectionService.listStorefront(PageRequest.of(0, 20));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).handle()).isEqualTo("active-collection");
    }

    @Test
    void storefrontByHandle_draftCollection_throwsNotFound() {
        var c = createManual("Draft");
        assertThatThrownBy(() -> collectionService.getByHandle(c.handle()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void storefrontProducts_returnsOnlyActiveProducts() {
        var c = createManual("Tops");
        collectionService.changeStatus(c.id(), new CollectionStatusRequest(CollectionStatus.ACTIVE));

        var draft = createProduct("Draft Tee");
        var active = createProduct("Active Tee");
        productService.changeStatus(active.id(), new ProductStatusRequest(ProductStatus.ACTIVE));

        collectionService.addProduct(c.id(), new AddCollectionProductRequest(draft.id()));
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(active.id()));

        var result = collectionService.listProductsStorefront(c.handle(), PageRequest.of(0, 20));
        assertThat(result.getContent().stream().filter(p -> p != null).toList()).hasSize(1);
        assertThat(result.getContent().stream().filter(p -> p != null).findFirst().get().title()).isEqualTo("Active Tee");
    }
}
```

- [ ] **Step 2: Run all integration tests**

```bash
./mvnw test -pl . -Dtest=CollectionServiceIT -q 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 3: Run full test suite to confirm no regressions**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/io/k2dv/garden/collection/service/CollectionServiceIT.java
git commit -m "feat(collection): add CollectionService integration tests"
```

---

## Task 11: Rules list endpoint fix and final validation

The storefront `listProductsStorefront` filters nulls inline — this works but a cleaner approach is to filter in the query. However, matching the existing project style (application-layer filtering), this is acceptable.

- [ ] **Step 1: Run the complete test suite one final time**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS, no failures.

- [ ] **Step 2: Quick smoke test of application startup**

```bash
./mvnw spring-boot:run -q 2>&1 | grep -E "(Started|ERROR)" | head -5
```

Expected: `Started GardenApplication`

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat: implement collections feature (manual + automated, admin + storefront APIs)"
```
