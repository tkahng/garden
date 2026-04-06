# Inventory Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a multi-location inventory domain that tracks stock levels with an immutable ledger, and surfaces fulfillment intent (IN_STOCK / PRE_ORDER / MADE_TO_ORDER) on product variants for storefront display.

**Architecture:** Shopify-style model — `FulfillmentType`, `InventoryPolicy`, and `leadTimeDays` live on `ProductVariant`; `InventoryItem` (1:1 with variant, already exists in product domain) is the physical-tracking bridge; `InventoryLevel` tracks quantity per `(inventoryItem, location)` pair; `InventoryTransaction` is the immutable audit ledger. Package-by-feature under `io.k2dv.garden.inventory`.

**Tech Stack:** Spring Boot 4.x, Spring Data JPA, Hibernate 6, Flyway, Lombok, PostgreSQL, Testcontainers (AbstractIntegrationTest), MockMvc (@WebMvcTest slice tests).

---

## File Map

**Create (shared):**
- `src/main/java/io/k2dv/garden/shared/model/ImmutableBaseEntity.java` — base class for immutable ledger entities (id + createdAt only, no updatedAt)

**Create (inventory domain):**
- `src/main/resources/db/migration/V12__create_inventory.sql`
- `src/main/java/io/k2dv/garden/inventory/model/FulfillmentType.java`
- `src/main/java/io/k2dv/garden/inventory/model/InventoryPolicy.java`
- `src/main/java/io/k2dv/garden/inventory/model/InventoryTransactionReason.java`
- `src/main/java/io/k2dv/garden/inventory/model/Location.java`
- `src/main/java/io/k2dv/garden/inventory/model/InventoryLevel.java`
- `src/main/java/io/k2dv/garden/inventory/model/InventoryTransaction.java`
- `src/main/java/io/k2dv/garden/inventory/repository/LocationRepository.java`
- `src/main/java/io/k2dv/garden/inventory/repository/InventoryLevelRepository.java`
- `src/main/java/io/k2dv/garden/inventory/repository/InventoryTransactionRepository.java`
- `src/main/java/io/k2dv/garden/inventory/dto/CreateLocationRequest.java`
- `src/main/java/io/k2dv/garden/inventory/dto/UpdateLocationRequest.java`
- `src/main/java/io/k2dv/garden/inventory/dto/LocationResponse.java`
- `src/main/java/io/k2dv/garden/inventory/dto/ReceiveStockRequest.java`
- `src/main/java/io/k2dv/garden/inventory/dto/AdjustStockRequest.java`
- `src/main/java/io/k2dv/garden/inventory/dto/UpdateVariantFulfillmentRequest.java`
- `src/main/java/io/k2dv/garden/inventory/dto/InventoryLevelResponse.java`
- `src/main/java/io/k2dv/garden/inventory/dto/InventoryTransactionResponse.java`
- `src/main/java/io/k2dv/garden/inventory/service/LocationService.java`
- `src/main/java/io/k2dv/garden/inventory/service/InventoryService.java`
- `src/main/java/io/k2dv/garden/inventory/controller/AdminLocationController.java`
- `src/main/java/io/k2dv/garden/inventory/controller/AdminInventoryController.java`
- `src/test/java/io/k2dv/garden/inventory/service/LocationServiceIT.java`
- `src/test/java/io/k2dv/garden/inventory/service/InventoryServiceIT.java`
- `src/test/java/io/k2dv/garden/inventory/controller/AdminLocationControllerTest.java`
- `src/test/java/io/k2dv/garden/inventory/controller/AdminInventoryControllerTest.java`

**Modify (product domain):**
- `src/main/java/io/k2dv/garden/product/model/ProductVariant.java` — add `fulfillmentType`, `inventoryPolicy`, `leadTimeDays`
- `src/main/java/io/k2dv/garden/product/dto/AdminVariantResponse.java` — add 3 new fields
- `src/main/java/io/k2dv/garden/product/dto/ProductVariantResponse.java` — add 3 new fields
- `src/main/java/io/k2dv/garden/product/service/VariantService.java` — update `toResponse()`
- `src/main/java/io/k2dv/garden/product/service/ProductService.java` — update storefront variant mapping

---

## Task 1: Flyway Migration V12 + ImmutableBaseEntity

**Files:**
- Create: `src/main/resources/db/migration/V12__create_inventory.sql`
- Create: `src/main/java/io/k2dv/garden/shared/model/ImmutableBaseEntity.java`

**Context:** The latest migration is `V11__add_pages_handle_unique_index.sql`. Next is V12. `BaseEntity` (at `src/main/java/io/k2dv/garden/shared/model/BaseEntity.java`) includes both `createdAt` and `updatedAt` via `@Generated`. `InventoryTransaction` has no `updated_at` column, so it cannot extend `BaseEntity` — it needs a separate `ImmutableBaseEntity` with only `id` and `createdAt`.

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V12__create_inventory.sql`:

```sql
-- src/main/resources/db/migration/V12__create_inventory.sql

-- New permissions for location management
INSERT INTO permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000025', 'location:read',  'location', 'read',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000026', 'location:write', 'location', 'write', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- STAFF, MANAGER, OWNER receive both location permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('STAFF', 'MANAGER', 'OWNER')
  AND p.name IN ('location:read', 'location:write')
ON CONFLICT DO NOTHING;

-- Extend product_variants with fulfillment fields
ALTER TABLE product_variants
    ADD COLUMN fulfillment_type TEXT NOT NULL DEFAULT 'IN_STOCK',
    ADD COLUMN inventory_policy TEXT NOT NULL DEFAULT 'DENY',
    ADD COLUMN lead_time_days   INT  NOT NULL DEFAULT 0;

-- Locations table
CREATE TABLE locations (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL,
    address    TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON locations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Inventory levels (qty per variant × location)
CREATE TABLE inventory_levels (
    id                UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    inventory_item_id UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    location_id       UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    quantity_on_hand  INT  NOT NULL DEFAULT 0,
    quantity_committed INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (inventory_item_id, location_id)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory_levels
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Inventory transactions (immutable ledger — no updated_at)
CREATE TABLE inventory_transactions (
    id                UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    inventory_item_id UUID NOT NULL REFERENCES inventory_items(id),
    location_id       UUID NOT NULL REFERENCES locations(id),
    quantity          INT  NOT NULL,
    reason            TEXT NOT NULL,
    note              TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
```

- [ ] **Step 2: Create ImmutableBaseEntity**

Create `src/main/java/io/k2dv/garden/shared/model/ImmutableBaseEntity.java`:

```java
package io.k2dv.garden.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity for immutable ledger rows (no updated_at).
 * Use this instead of BaseEntity when the table has no updated_at column.
 */
@MappedSuperclass
@Getter
public abstract class ImmutableBaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
```

- [ ] **Step 3: Verify the app starts (migration runs)**

```bash
./mvnw test -pl . -Dtest=SmokeIT -q
```

Expected: BUILD SUCCESS — Flyway applies V12 without errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V12__create_inventory.sql \
        src/main/java/io/k2dv/garden/shared/model/ImmutableBaseEntity.java
git commit -m "feat(inventory): add V12 migration and ImmutableBaseEntity"
```

---

## Task 2: Inventory Enums, Entities, and Repositories

**Files:**
- Create: `src/main/java/io/k2dv/garden/inventory/model/FulfillmentType.java`
- Create: `src/main/java/io/k2dv/garden/inventory/model/InventoryPolicy.java`
- Create: `src/main/java/io/k2dv/garden/inventory/model/InventoryTransactionReason.java`
- Create: `src/main/java/io/k2dv/garden/inventory/model/Location.java`
- Create: `src/main/java/io/k2dv/garden/inventory/model/InventoryLevel.java`
- Create: `src/main/java/io/k2dv/garden/inventory/model/InventoryTransaction.java`
- Create: `src/main/java/io/k2dv/garden/inventory/repository/LocationRepository.java`
- Create: `src/main/java/io/k2dv/garden/inventory/repository/InventoryLevelRepository.java`
- Create: `src/main/java/io/k2dv/garden/inventory/repository/InventoryTransactionRepository.java`
- Modify: `src/main/java/io/k2dv/garden/product/model/ProductVariant.java`

**Context:** `BaseEntity` is at `io.k2dv.garden.shared.model.BaseEntity` and includes `id`, `createdAt`, `updatedAt`. `ImmutableBaseEntity` was created in Task 1. `InventoryItem` is at `io.k2dv.garden.product.model.InventoryItem` — this is the bridge entity. `InventoryItemRepository` is at `io.k2dv.garden.product.repository.InventoryItemRepository` and has `findByVariantId(UUID)`.

- [ ] **Step 1: Create enums**

`src/main/java/io/k2dv/garden/inventory/model/FulfillmentType.java`:
```java
package io.k2dv.garden.inventory.model;

public enum FulfillmentType {
    IN_STOCK,
    PRE_ORDER,
    MADE_TO_ORDER
}
```

`src/main/java/io/k2dv/garden/inventory/model/InventoryPolicy.java`:
```java
package io.k2dv.garden.inventory.model;

public enum InventoryPolicy {
    DENY,
    CONTINUE
}
```

`src/main/java/io/k2dv/garden/inventory/model/InventoryTransactionReason.java`:
```java
package io.k2dv.garden.inventory.model;

public enum InventoryTransactionReason {
    RECEIVED,
    SOLD,
    ADJUSTED,
    RETURNED,
    DAMAGED
}
```

- [ ] **Step 2: Create Location entity**

`src/main/java/io/k2dv/garden/inventory/model/Location.java`:
```java
package io.k2dv.garden.inventory.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "locations")
@Getter
@Setter
public class Location extends BaseEntity {
    @Column(nullable = false)
    private String name;
    private String address;
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
```

- [ ] **Step 3: Create InventoryLevel entity**

`src/main/java/io/k2dv/garden/inventory/model/InventoryLevel.java`:
```java
package io.k2dv.garden.inventory.model;

import io.k2dv.garden.product.model.InventoryItem;
import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventory_levels")
@Getter
@Setter
public class InventoryLevel extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand = 0;

    @Column(name = "quantity_committed", nullable = false)
    private int quantityCommitted = 0;
}
```

- [ ] **Step 4: Create InventoryTransaction entity**

`src/main/java/io/k2dv/garden/inventory/model/InventoryTransaction.java`:
```java
package io.k2dv.garden.inventory.model;

import io.k2dv.garden.product.model.InventoryItem;
import io.k2dv.garden.shared.model.ImmutableBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
public class InventoryTransaction extends ImmutableBaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryTransactionReason reason;

    private String note;
}
```

- [ ] **Step 5: Add fulfillment fields to ProductVariant**

Open `src/main/java/io/k2dv/garden/product/model/ProductVariant.java` and add after the existing `barcode` field (add imports at top too):

```java
// Add to imports:
import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;

// Add fields (after barcode field):
@Enumerated(EnumType.STRING)
@Column(name = "fulfillment_type", nullable = false)
private FulfillmentType fulfillmentType = FulfillmentType.IN_STOCK;

@Enumerated(EnumType.STRING)
@Column(name = "inventory_policy", nullable = false)
private InventoryPolicy inventoryPolicy = InventoryPolicy.DENY;

@Column(name = "lead_time_days", nullable = false)
private int leadTimeDays = 0;
```

- [ ] **Step 6: Create repositories**

`src/main/java/io/k2dv/garden/inventory/repository/LocationRepository.java`:
```java
package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
}
```

`src/main/java/io/k2dv/garden/inventory/repository/InventoryLevelRepository.java`:
```java
package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.InventoryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {
    List<InventoryLevel> findByInventoryItemId(UUID inventoryItemId);
    Optional<InventoryLevel> findByInventoryItemIdAndLocationId(UUID inventoryItemId, UUID locationId);
}
```

`src/main/java/io/k2dv/garden/inventory/repository/InventoryTransactionRepository.java`:
```java
package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
    Page<InventoryTransaction> findByInventoryItemIdAndLocationId(UUID inventoryItemId, UUID locationId, Pageable pageable);
    Page<InventoryTransaction> findByInventoryItemId(UUID inventoryItemId, Pageable pageable);
}
```

- [ ] **Step 7: Compile check**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/k2dv/garden/inventory/ \
        src/main/java/io/k2dv/garden/product/model/ProductVariant.java
git commit -m "feat(inventory): add enums, entities, repositories, and ProductVariant fulfillment fields"
```

---

## Task 3: DTOs — Inventory + Updated Product Variant Responses

**Files:**
- Create: `src/main/java/io/k2dv/garden/inventory/dto/CreateLocationRequest.java`
- Create: `src/main/java/io/k2dv/garden/inventory/dto/UpdateLocationRequest.java`
- Create: `src/main/java/io/k2dv/garden/inventory/dto/LocationResponse.java`
- Create: `src/main/java/io/k2dv/garden/inventory/dto/ReceiveStockRequest.java`
- Create: `src/main/java/io/k2dv/garden/inventory/dto/AdjustStockRequest.java`
- Create: `src/main/java/io/k2dv/garden/inventory/dto/UpdateVariantFulfillmentRequest.java`
- Create: `src/main/java/io/k2dv/garden/inventory/dto/InventoryLevelResponse.java`
- Create: `src/main/java/io/k2dv/garden/inventory/dto/InventoryTransactionResponse.java`
- Modify: `src/main/java/io/k2dv/garden/product/dto/AdminVariantResponse.java`
- Modify: `src/main/java/io/k2dv/garden/product/dto/ProductVariantResponse.java`
- Modify: `src/main/java/io/k2dv/garden/product/service/VariantService.java`
- Modify: `src/main/java/io/k2dv/garden/product/service/ProductService.java`

**Context:** `AdminVariantResponse` currently ends with `Instant deletedAt`. `ProductVariantResponse` currently has 6 fields. Both need the three new fulfillment fields. `VariantService.toResponse()` builds `AdminVariantResponse`. `ProductService` builds `ProductVariantResponse` inline at line 224.

- [ ] **Step 1: Create inventory DTOs**

`src/main/java/io/k2dv/garden/inventory/dto/CreateLocationRequest.java`:
```java
package io.k2dv.garden.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLocationRequest(
    @NotBlank String name,
    String address
) {}
```

`src/main/java/io/k2dv/garden/inventory/dto/UpdateLocationRequest.java`:
```java
package io.k2dv.garden.inventory.dto;

public record UpdateLocationRequest(
    String name,
    String address
) {}
```

`src/main/java/io/k2dv/garden/inventory/dto/LocationResponse.java`:
```java
package io.k2dv.garden.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
    UUID id,
    String name,
    String address,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
```

`src/main/java/io/k2dv/garden/inventory/dto/ReceiveStockRequest.java`:
```java
package io.k2dv.garden.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReceiveStockRequest(
    @NotNull UUID locationId,
    @Min(1) int quantity,
    String note
) {}
```

`src/main/java/io/k2dv/garden/inventory/dto/AdjustStockRequest.java`:
```java
package io.k2dv.garden.inventory.dto;

import io.k2dv.garden.inventory.model.InventoryTransactionReason;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdjustStockRequest(
    @NotNull UUID locationId,
    int delta,
    @NotNull InventoryTransactionReason reason,
    String note
) {}
```

`src/main/java/io/k2dv/garden/inventory/dto/UpdateVariantFulfillmentRequest.java`:
```java
package io.k2dv.garden.inventory.dto;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateVariantFulfillmentRequest(
    @NotNull FulfillmentType fulfillmentType,
    @NotNull InventoryPolicy inventoryPolicy,
    @Min(0) int leadTimeDays
) {}
```

`src/main/java/io/k2dv/garden/inventory/dto/InventoryLevelResponse.java`:
```java
package io.k2dv.garden.inventory.dto;

import java.util.UUID;

public record InventoryLevelResponse(
    UUID id,
    UUID inventoryItemId,
    UUID locationId,
    String locationName,
    int quantityOnHand,
    int quantityCommitted
) {}
```

`src/main/java/io/k2dv/garden/inventory/dto/InventoryTransactionResponse.java`:
```java
package io.k2dv.garden.inventory.dto;

import io.k2dv.garden.inventory.model.InventoryTransactionReason;

import java.time.Instant;
import java.util.UUID;

public record InventoryTransactionResponse(
    UUID id,
    UUID inventoryItemId,
    UUID locationId,
    String locationName,
    int quantity,
    InventoryTransactionReason reason,
    String note,
    Instant createdAt
) {}
```

- [ ] **Step 2: Update AdminVariantResponse**

Replace the entire content of `src/main/java/io/k2dv/garden/product/dto/AdminVariantResponse.java`:

```java
package io.k2dv.garden.product.dto;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;

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
    FulfillmentType fulfillmentType,
    InventoryPolicy inventoryPolicy,
    int leadTimeDays,
    Instant deletedAt
) {}
```

- [ ] **Step 3: Update ProductVariantResponse**

Replace the entire content of `src/main/java/io/k2dv/garden/product/dto/ProductVariantResponse.java`:

```java
package io.k2dv.garden.product.dto;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductVariantResponse(
    UUID id,
    String title,
    String sku,
    BigDecimal price,
    BigDecimal compareAtPrice,
    List<OptionValueLabel> optionValues,
    FulfillmentType fulfillmentType,
    InventoryPolicy inventoryPolicy,
    int leadTimeDays
) {}
```

- [ ] **Step 4: Update VariantService.toResponse()**

In `src/main/java/io/k2dv/garden/product/service/VariantService.java`, the `toResponse` method currently returns:

```java
return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
    v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
    labels, v.getDeletedAt());
```

Update it to:

```java
return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
    v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
    labels, v.getFulfillmentType(), v.getInventoryPolicy(), v.getLeadTimeDays(), v.getDeletedAt());
```

- [ ] **Step 5: Update ProductService storefront variant mapping**

In `src/main/java/io/k2dv/garden/product/service/ProductService.java`, search for the line that creates `ProductVariantResponse` (search for `new ProductVariantResponse(`). It currently reads:

```java
return new ProductVariantResponse(v.getId(), v.getTitle(), v.getSku(),
    v.getPrice(), v.getCompareAtPrice(), labels);
```

Update it to:

```java
return new ProductVariantResponse(v.getId(), v.getTitle(), v.getSku(),
    v.getPrice(), v.getCompareAtPrice(), labels,
    v.getFulfillmentType(), v.getInventoryPolicy(), v.getLeadTimeDays());
```

- [ ] **Step 6: Compile check**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Run existing tests to confirm no regressions**

```bash
./mvnw test -q
```

Expected: All previously passing tests still pass. (Test count will include existing product tests.)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/k2dv/garden/inventory/dto/ \
        src/main/java/io/k2dv/garden/product/dto/AdminVariantResponse.java \
        src/main/java/io/k2dv/garden/product/dto/ProductVariantResponse.java \
        src/main/java/io/k2dv/garden/product/service/VariantService.java \
        src/main/java/io/k2dv/garden/product/service/ProductService.java
git commit -m "feat(inventory): add inventory DTOs and update variant response DTOs with fulfillment fields"
```

---

## Task 4: LocationService + LocationServiceIT

**Files:**
- Create: `src/main/java/io/k2dv/garden/inventory/service/LocationService.java`
- Create: `src/test/java/io/k2dv/garden/inventory/service/LocationServiceIT.java`

**Context:** `AbstractIntegrationTest` is at `io.k2dv.garden.shared.AbstractIntegrationTest` — annotated with `@SpringBootTest`, `@Transactional`, `@Rollback`. Each test runs in its own transaction that is rolled back. Do NOT call `locationRepo.save()` and then expect it to be found by a service method that opens a new transaction — everything is in the same transaction context. `NotFoundException` is at `io.k2dv.garden.shared.exception.NotFoundException`. The `Location` entity has `isActive` defaulting to `true`.

- [ ] **Step 1: Write failing tests**

Create `src/test/java/io/k2dv/garden/inventory/service/LocationServiceIT.java`:

```java
package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.UpdateLocationRequest;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationServiceIT extends AbstractIntegrationTest {

    @Autowired LocationService locationService;

    @Test
    void createLocation_returnsResponse() {
        var req = new CreateLocationRequest("Main Warehouse", "123 Garden St");
        var resp = locationService.create(req);
        assertThat(resp.id()).isNotNull();
        assertThat(resp.name()).isEqualTo("Main Warehouse");
        assertThat(resp.address()).isEqualTo("123 Garden St");
        assertThat(resp.isActive()).isTrue();
    }

    @Test
    void updateLocation_changesFields() {
        var created = locationService.create(new CreateLocationRequest("Old Name", null));
        var updated = locationService.update(created.id(), new UpdateLocationRequest("New Name", "456 Ave"));
        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.address()).isEqualTo("456 Ave");
    }

    @Test
    void deactivateLocation_setsIsActiveFalse() {
        var created = locationService.create(new CreateLocationRequest("Warehouse", null));
        locationService.deactivate(created.id());
        var fetched = locationService.get(created.id());
        assertThat(fetched.isActive()).isFalse();
    }

    @Test
    void getLocation_notFound_throwsNotFoundException() {
        assertThatThrownBy(() -> locationService.get(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest=LocationServiceIT -q 2>&1 | tail -5
```

Expected: FAIL — `LocationService` does not exist yet.

- [ ] **Step 3: Implement LocationService**

Create `src/main/java/io/k2dv/garden/inventory/service/LocationService.java`:

```java
package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.LocationResponse;
import io.k2dv.garden.inventory.dto.UpdateLocationRequest;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepo;

    @Transactional
    public LocationResponse create(CreateLocationRequest req) {
        Location loc = new Location();
        loc.setName(req.name());
        loc.setAddress(req.address());
        return toResponse(locationRepo.save(loc));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> list() {
        return locationRepo.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public LocationResponse update(UUID id, UpdateLocationRequest req) {
        Location loc = findOrThrow(id);
        if (req.name() != null) loc.setName(req.name());
        if (req.address() != null) loc.setAddress(req.address());
        return toResponse(locationRepo.save(loc));
    }

    @Transactional
    public void deactivate(UUID id) {
        Location loc = findOrThrow(id);
        loc.setActive(false);
        locationRepo.save(loc);
    }

    @Transactional
    public LocationResponse reactivate(UUID id) {
        Location loc = findOrThrow(id);
        loc.setActive(true);
        return toResponse(locationRepo.save(loc));
    }

    private Location findOrThrow(UUID id) {
        return locationRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("LOCATION_NOT_FOUND", "Location not found"));
    }

    LocationResponse toResponse(Location loc) {
        return new LocationResponse(loc.getId(), loc.getName(), loc.getAddress(),
            loc.isActive(), loc.getCreatedAt(), loc.getUpdatedAt());
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=LocationServiceIT -q
```

Expected: BUILD SUCCESS — 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/inventory/service/LocationService.java \
        src/test/java/io/k2dv/garden/inventory/service/LocationServiceIT.java
git commit -m "feat(inventory): add LocationService with integration tests"
```

---

## Task 5: InventoryService + InventoryServiceIT

**Files:**
- Create: `src/main/java/io/k2dv/garden/inventory/service/InventoryService.java`
- Create: `src/test/java/io/k2dv/garden/inventory/service/InventoryServiceIT.java`

**Context:** `InventoryItemRepository` is at `io.k2dv.garden.product.repository.InventoryItemRepository` with `findByVariantId(UUID)`. `InventoryLevelRepository` has `findByInventoryItemId(UUID)` and `findByInventoryItemIdAndLocationId(UUID, UUID)`. `InventoryTransactionRepository` has `findByInventoryItemId(UUID, Pageable)` and `findByInventoryItemIdAndLocationId(UUID, UUID, Pageable)`. `PagedResult.of(Page<T>)` converts a Spring Data `Page` to `PagedResult<T>`. There is no `BadRequestException` — use `ValidationException` (at `io.k2dv.garden.shared.exception.ValidationException`) which maps to HTTP 400. In integration tests, you need a real `Product` + `ProductVariant` + `InventoryItem` — the `product_variant.product_id` FK is non-deferrable so you must create a real `Product` row first. Use `ProductRepository` to save the Product, then use `VariantService.create()` to create the variant (which auto-creates the InventoryItem). `InventoryPolicy.DENY` means adjusting below 0 throws; `InventoryPolicy.CONTINUE` allows it. Timestamps: `LocationResponse` and `InventoryTransactionResponse` use `Instant` (not `OffsetDateTime` as the spec text says) to match the project convention established by `BaseEntity`.

- [ ] **Step 1: Write failing tests**

Create `src/test/java/io/k2dv/garden/inventory/service/InventoryServiceIT.java`:

```java
package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.model.*;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceIT extends AbstractIntegrationTest {

    @Autowired InventoryService inventoryService;
    @Autowired LocationRepository locationRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ProductVariantRepository variantRepo;
    @Autowired VariantService variantService;

    private ProductVariant variant;
    private Location warehouse;
    private Location manufacturer;

    @BeforeEach
    void setup() {
        // product_variants.product_id has a non-deferrable FK to products(id),
        // so we must create a real Product first.
        Product p = new Product();
        p.setTitle("Test Product");
        p.setHandle("test-product-" + UUID.randomUUID().toString().substring(0, 8));
        p = productRepo.save(p);

        // VariantService.create() auto-creates the InventoryItem 1:1 with the variant.
        var variantResp = variantService.create(p.getId(),
            new CreateVariantRequest(BigDecimal.TEN, null, null, null, null, null, List.of()));
        variant = variantRepo.findById(variantResp.id()).orElseThrow();

        Location wh = new Location();
        wh.setName("Warehouse");
        warehouse = locationRepo.save(wh);

        Location mfg = new Location();
        mfg.setName("Manufacturer");
        manufacturer = locationRepo.save(mfg);
    }

    @Test
    void receiveStock_newLevel_createsLevelAndTransaction() {
        var req = new ReceiveStockRequest(warehouse.getId(), 10, "Initial stock");
        var resp = inventoryService.receiveStock(variant.getId(), req);

        assertThat(resp.quantityOnHand()).isEqualTo(10);
        assertThat(resp.locationId()).isEqualTo(warehouse.getId());

        var txns = inventoryService.listTransactions(variant.getId(), warehouse.getId(),
            PageRequest.of(0, 10));
        assertThat(txns.getContent()).hasSize(1);
        assertThat(txns.getContent().get(0).reason()).isEqualTo(InventoryTransactionReason.RECEIVED);
        assertThat(txns.getContent().get(0).quantity()).isEqualTo(10);
    }

    @Test
    void receiveStock_existingLevel_incrementsQty() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 5, null));
        var resp = inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 3, null));
        assertThat(resp.quantityOnHand()).isEqualTo(8);
    }

    @Test
    void adjustStock_negativeAllowed_whenPolicyContinue() {
        // Set inventory policy to CONTINUE
        variant.setInventoryPolicy(InventoryPolicy.CONTINUE);
        variantRepo.save(variant);

        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 2, null));
        var req = new AdjustStockRequest(warehouse.getId(), -5, InventoryTransactionReason.ADJUSTED, "oversell");
        var resp = inventoryService.adjustStock(variant.getId(), req);
        assertThat(resp.quantityOnHand()).isEqualTo(-3);
    }

    @Test
    void adjustStock_belowZero_deny_throwsBadRequest() {
        // Default inventoryPolicy is DENY
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 2, null));
        var req = new AdjustStockRequest(warehouse.getId(), -5, InventoryTransactionReason.ADJUSTED, "too many");
        assertThatThrownBy(() -> inventoryService.adjustStock(variant.getId(), req))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void getLevels_returnsAllLocations() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 10, null));
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(manufacturer.getId(), 3, null));
        var levels = inventoryService.getLevels(variant.getId());
        assertThat(levels).hasSize(2);
    }

    @Test
    void listTransactions_filterByLocation_returnsPaged() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 5, null));
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(manufacturer.getId(), 3, null));

        var page = inventoryService.listTransactions(variant.getId(), warehouse.getId(),
            PageRequest.of(0, 10, Sort.by("createdAt")));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).locationId()).isEqualTo(warehouse.getId());
    }

    @Test
    void listTransactions_allLocations_whenLocationIdNull() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 5, null));
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(manufacturer.getId(), 3, null));

        var page = inventoryService.listTransactions(variant.getId(), null,
            PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void updateVariantFulfillment_persistsAllThreeFields() {
        var req = new UpdateVariantFulfillmentRequest(
            FulfillmentType.MADE_TO_ORDER, InventoryPolicy.CONTINUE, 14);
        var resp = inventoryService.updateVariantFulfillment(variant.getId(), req);
        assertThat(resp.fulfillmentType()).isEqualTo(FulfillmentType.MADE_TO_ORDER);
        assertThat(resp.inventoryPolicy()).isEqualTo(InventoryPolicy.CONTINUE);
        assertThat(resp.leadTimeDays()).isEqualTo(14);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest=InventoryServiceIT -q 2>&1 | tail -5
```

Expected: FAIL — `InventoryService` does not exist yet.

- [ ] **Step 3: Implement InventoryService**

Create `src/main/java/io/k2dv/garden/inventory/service/InventoryService.java`:

```java
package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.model.*;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.InventoryTransactionRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.OptionValueLabel;
import io.k2dv.garden.product.model.InventoryItem;
import io.k2dv.garden.product.model.ProductOption;
import io.k2dv.garden.product.model.ProductOptionValue;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.InventoryItemRepository;
import io.k2dv.garden.product.repository.ProductOptionRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepo;
    private final InventoryLevelRepository levelRepo;
    private final InventoryTransactionRepository txnRepo;
    private final LocationRepository locationRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductOptionRepository optionRepo;

    @Transactional(readOnly = true)
    public List<InventoryLevelResponse> getLevels(UUID variantId) {
        InventoryItem item = findItemByVariant(variantId);
        return levelRepo.findByInventoryItemId(item.getId()).stream()
            .map(this::toLevelResponse)
            .toList();
    }

    @Transactional
    public InventoryLevelResponse receiveStock(UUID variantId, ReceiveStockRequest req) {
        InventoryItem item = findItemByVariant(variantId);
        Location location = findLocation(req.locationId());

        InventoryLevel level = levelRepo
            .findByInventoryItemIdAndLocationId(item.getId(), location.getId())
            .orElseGet(() -> {
                InventoryLevel l = new InventoryLevel();
                l.setInventoryItem(item);
                l.setLocation(location);
                return l;
            });
        level.setQuantityOnHand(level.getQuantityOnHand() + req.quantity());
        level = levelRepo.save(level);

        InventoryTransaction txn = new InventoryTransaction();
        txn.setInventoryItem(item);
        txn.setLocation(location);
        txn.setQuantity(req.quantity());
        txn.setReason(InventoryTransactionReason.RECEIVED);
        txn.setNote(req.note());
        txnRepo.save(txn);

        return toLevelResponse(level);
    }

    @Transactional
    public InventoryLevelResponse adjustStock(UUID variantId, AdjustStockRequest req) {
        if (req.reason() == InventoryTransactionReason.RECEIVED
                || req.reason() == InventoryTransactionReason.SOLD) {
            throw new ValidationException("INVALID_REASON",
                "Use the receive endpoint for RECEIVED; SOLD is system-managed");
        }

        InventoryItem item = findItemByVariant(variantId);
        ProductVariant variant = variantRepo.findById(variantId)
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        Location location = findLocation(req.locationId());

        InventoryLevel level = levelRepo
            .findByInventoryItemIdAndLocationId(item.getId(), location.getId())
            .orElseGet(() -> {
                InventoryLevel l = new InventoryLevel();
                l.setInventoryItem(item);
                l.setLocation(location);
                return l;
            });

        int newQty = level.getQuantityOnHand() + req.delta();
        if (newQty < 0 && variant.getInventoryPolicy() == InventoryPolicy.DENY) {
            throw new ValidationException("INSUFFICIENT_STOCK",
                "Cannot adjust below 0 when inventory policy is DENY");
        }
        level.setQuantityOnHand(newQty);
        level = levelRepo.save(level);

        InventoryTransaction txn = new InventoryTransaction();
        txn.setInventoryItem(item);
        txn.setLocation(location);
        txn.setQuantity(req.delta());
        txn.setReason(req.reason());
        txn.setNote(req.note());
        txnRepo.save(txn);

        return toLevelResponse(level);
    }

    @Transactional(readOnly = true)
    public PagedResult<InventoryTransactionResponse> listTransactions(
            UUID variantId, UUID locationId, Pageable pageable) {
        InventoryItem item = findItemByVariant(variantId);
        Page<InventoryTransaction> page = locationId != null
            ? txnRepo.findByInventoryItemIdAndLocationId(item.getId(), locationId, pageable)
            : txnRepo.findByInventoryItemId(item.getId(), pageable);
        return PagedResult.of(page.map(this::toTxnResponse));
    }

    @Transactional
    public AdminVariantResponse updateVariantFulfillment(UUID variantId, UpdateVariantFulfillmentRequest req) {
        ProductVariant variant = variantRepo.findById(variantId)
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        variant.setFulfillmentType(req.fulfillmentType());
        variant.setInventoryPolicy(req.inventoryPolicy());
        variant.setLeadTimeDays(req.leadTimeDays());
        variantRepo.save(variant);
        return toVariantResponse(variant);
    }

    private InventoryItem findItemByVariant(UUID variantId) {
        return inventoryItemRepo.findByVariantId(variantId)
            .orElseThrow(() -> new NotFoundException("INVENTORY_ITEM_NOT_FOUND",
                "No inventory item found for variant " + variantId));
    }

    private Location findLocation(UUID locationId) {
        return locationRepo.findById(locationId)
            .orElseThrow(() -> new NotFoundException("LOCATION_NOT_FOUND", "Location not found"));
    }

    private InventoryLevelResponse toLevelResponse(InventoryLevel level) {
        return new InventoryLevelResponse(
            level.getId(),
            level.getInventoryItem().getId(),
            level.getLocation().getId(),
            level.getLocation().getName(),
            level.getQuantityOnHand(),
            level.getQuantityCommitted()
        );
    }

    private InventoryTransactionResponse toTxnResponse(InventoryTransaction txn) {
        return new InventoryTransactionResponse(
            txn.getId(),
            txn.getInventoryItem().getId(),
            txn.getLocation().getId(),
            txn.getLocation().getName(),
            txn.getQuantity(),
            txn.getReason(),
            txn.getNote(),
            txn.getCreatedAt()
        );
    }

    private AdminVariantResponse toVariantResponse(ProductVariant v) {
        Set<UUID> optionIds = v.getOptionValues().stream()
            .map(ProductOptionValue::getOptionId).collect(Collectors.toSet());
        Map<UUID, String> optionNamesById = optionRepo.findAllById(optionIds).stream()
            .collect(Collectors.toMap(ProductOption::getId, ProductOption::getName));
        List<OptionValueLabel> labels = v.getOptionValues().stream()
            .map(ov -> new OptionValueLabel(optionNamesById.getOrDefault(ov.getOptionId(), ""), ov.getLabel()))
            .toList();
        return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
            v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
            labels, v.getFulfillmentType(), v.getInventoryPolicy(), v.getLeadTimeDays(), v.getDeletedAt());
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=InventoryServiceIT -q
```

Expected: BUILD SUCCESS — 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/inventory/service/InventoryService.java \
        src/test/java/io/k2dv/garden/inventory/service/InventoryServiceIT.java
git commit -m "feat(inventory): add InventoryService with integration tests"
```

---

## Task 6: Controllers + Controller Slice Tests

**Files:**
- Create: `src/main/java/io/k2dv/garden/inventory/controller/AdminLocationController.java`
- Create: `src/main/java/io/k2dv/garden/inventory/controller/AdminInventoryController.java`
- Create: `src/test/java/io/k2dv/garden/inventory/controller/AdminLocationControllerTest.java`
- Create: `src/test/java/io/k2dv/garden/inventory/controller/AdminInventoryControllerTest.java`

**Context:** Use `@WebMvcTest(controllers = AdminLocationController.class)` from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`. Import `TestSecurityConfig` and `GlobalExceptionHandler`. Use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean`. Permission strings match what's in V12: `location:read`, `location:write`, `inventory:read`, `inventory:write`. Check `AdminBlogControllerTest` for the exact slice test pattern.

- [ ] **Step 1: Write controller slice tests**

Create `src/test/java/io/k2dv/garden/inventory/controller/AdminLocationControllerTest.java`:

```java
package io.k2dv.garden.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.LocationResponse;
import io.k2dv.garden.inventory.service.LocationService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminLocationController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminLocationControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean LocationService locationService;

    private LocationResponse stubLocation() {
        return new LocationResponse(UUID.randomUUID(), "Warehouse", "123 St", true,
            Instant.now(), Instant.now());
    }

    @Test
    void createLocation_validRequest_returns201() throws Exception {
        when(locationService.create(any())).thenReturn(stubLocation());

        mvc.perform(post("/api/v1/admin/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateLocationRequest("Warehouse", null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.name").value("Warehouse"));
    }

    @Test
    void createLocation_missingName_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getLocation_notFound_returns404() throws Exception {
        when(locationService.get(any()))
            .thenThrow(new NotFoundException("LOCATION_NOT_FOUND", "Location not found"));

        mvc.perform(get("/api/v1/admin/locations/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("LOCATION_NOT_FOUND"));
    }

    @Test
    void deactivateLocation_returns204() throws Exception {
        doNothing().when(locationService).deactivate(any());

        mvc.perform(delete("/api/v1/admin/locations/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void reactivateLocation_returns200() throws Exception {
        when(locationService.reactivate(any())).thenReturn(stubLocation());

        mvc.perform(post("/api/v1/admin/locations/{id}/reactivate", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Warehouse"));
    }
}
```

Create `src/test/java/io/k2dv/garden/inventory/controller/AdminInventoryControllerTest.java`:

```java
package io.k2dv.garden.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.model.*;
import io.k2dv.garden.inventory.service.InventoryService;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminInventoryController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminInventoryControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean InventoryService inventoryService;

    private InventoryLevelResponse stubLevel() {
        return new InventoryLevelResponse(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "Warehouse", 10, 0);
    }

    @Test
    void receiveStock_validRequest_returns200() throws Exception {
        when(inventoryService.receiveStock(any(), any())).thenReturn(stubLevel());

        mvc.perform(post("/api/v1/admin/inventory/variants/{variantId}/receive", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new ReceiveStockRequest(UUID.randomUUID(), 10, "Initial"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.quantityOnHand").value(10));
    }

    @Test
    void adjustStock_validRequest_returns200() throws Exception {
        when(inventoryService.adjustStock(any(), any())).thenReturn(stubLevel());

        mvc.perform(post("/api/v1/admin/inventory/variants/{variantId}/adjust", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AdjustStockRequest(UUID.randomUUID(), -2, InventoryTransactionReason.DAMAGED, null))))
            .andExpect(status().isOk());
    }

    @Test
    void adjustStock_invalidReason_returns400() throws Exception {
        when(inventoryService.adjustStock(any(), any()))
            .thenThrow(new io.k2dv.garden.shared.exception.ValidationException(
                "INVALID_REASON", "Use the receive endpoint for RECEIVED; SOLD is system-managed"));

        mvc.perform(post("/api/v1/admin/inventory/variants/{variantId}/adjust", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AdjustStockRequest(UUID.randomUUID(), 5, InventoryTransactionReason.RECEIVED, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_REASON"));
    }

    @Test
    void getLevels_returns200() throws Exception {
        when(inventoryService.getLevels(any())).thenReturn(List.of(stubLevel()));

        mvc.perform(get("/api/v1/admin/inventory/variants/{variantId}/levels", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listTransactions_returns200WithPage() throws Exception {
        var result = new PagedResult<>(List.<InventoryTransactionResponse>of(),
            PageMeta.builder().page(0).pageSize(20).total(0L).build());
        when(inventoryService.listTransactions(any(), any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/inventory/variants/{variantId}/transactions", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void updateFulfillment_validRequest_returns200() throws Exception {
        var variantResp = new AdminVariantResponse(UUID.randomUUID(), "Default Title", null, null,
            BigDecimal.TEN, null, null, null, List.of(),
            FulfillmentType.MADE_TO_ORDER, InventoryPolicy.CONTINUE, 14, null);
        when(inventoryService.updateVariantFulfillment(any(), any())).thenReturn(variantResp);

        mvc.perform(patch("/api/v1/admin/inventory/variants/{variantId}/fulfillment", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new UpdateVariantFulfillmentRequest(FulfillmentType.MADE_TO_ORDER, InventoryPolicy.CONTINUE, 14))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fulfillmentType").value("MADE_TO_ORDER"))
            .andExpect(jsonPath("$.data.leadTimeDays").value(14));
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest="AdminLocationControllerTest,AdminInventoryControllerTest" -q 2>&1 | tail -5
```

Expected: FAIL — controllers do not exist yet.

- [ ] **Step 3: Implement AdminLocationController**

Create `src/main/java/io/k2dv/garden/inventory/controller/AdminLocationController.java`:

```java
package io.k2dv.garden.inventory.controller;

import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.LocationResponse;
import io.k2dv.garden.inventory.dto.UpdateLocationRequest;
import io.k2dv.garden.inventory.service.LocationService;
import io.k2dv.garden.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/locations")
@RequiredArgsConstructor
public class AdminLocationController {

    private final LocationService locationService;

    @PostMapping
    @PreAuthorize("hasAuthority('location:write')")
    public ResponseEntity<ApiResponse<LocationResponse>> create(
            @RequestBody @Valid CreateLocationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.of(locationService.create(req)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('location:read')")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(locationService.list()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('location:read')")
    public ResponseEntity<ApiResponse<LocationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(locationService.get(id)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('location:write')")
    public ResponseEntity<ApiResponse<LocationResponse>> update(
            @PathVariable UUID id, @RequestBody UpdateLocationRequest req) {
        return ResponseEntity.ok(ApiResponse.of(locationService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('location:write')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        locationService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('location:write')")
    public ResponseEntity<ApiResponse<LocationResponse>> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(locationService.reactivate(id)));
    }
}
```

- [ ] **Step 4: Implement AdminInventoryController**

Create `src/main/java/io/k2dv/garden/inventory/controller/AdminInventoryController.java`:

```java
package io.k2dv.garden.inventory.controller;

import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.service.InventoryService;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/variants/{variantId}/levels")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ResponseEntity<ApiResponse<List<InventoryLevelResponse>>> getLevels(
            @PathVariable UUID variantId) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.getLevels(variantId)));
    }

    @PostMapping("/variants/{variantId}/receive")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<ApiResponse<InventoryLevelResponse>> receiveStock(
            @PathVariable UUID variantId,
            @RequestBody @Valid ReceiveStockRequest req) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.receiveStock(variantId, req)));
    }

    @PostMapping("/variants/{variantId}/adjust")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<ApiResponse<InventoryLevelResponse>> adjustStock(
            @PathVariable UUID variantId,
            @RequestBody @Valid AdjustStockRequest req) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.adjustStock(variantId, req)));
    }

    @GetMapping("/variants/{variantId}/transactions")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ResponseEntity<ApiResponse<PagedResult<InventoryTransactionResponse>>> listTransactions(
            @PathVariable UUID variantId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.listTransactions(variantId, locationId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @PatchMapping("/variants/{variantId}/fulfillment")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<ApiResponse<AdminVariantResponse>> updateFulfillment(
            @PathVariable UUID variantId,
            @RequestBody @Valid UpdateVariantFulfillmentRequest req) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.updateVariantFulfillment(variantId, req)));
    }
}
```

- [ ] **Step 5: Run controller tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest="AdminLocationControllerTest,AdminInventoryControllerTest" -q
```

Expected: BUILD SUCCESS — 11 tests pass (5 location + 6 inventory).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/k2dv/garden/inventory/controller/ \
        src/test/java/io/k2dv/garden/inventory/controller/
git commit -m "feat(inventory): add AdminLocationController and AdminInventoryController with slice tests"
```

---

## Task 7: Final Verification

**Files:** None created. Run full suite, fix any issues.

- [ ] **Step 1: Run the full test suite**

```bash
./mvnw test -q
```

Expected: BUILD SUCCESS. Count should include all previous tests plus the new inventory tests (4 LocationServiceIT + 8 InventoryServiceIT + 5 AdminLocationControllerTest + 6 AdminInventoryControllerTest = 23 new tests).

- [ ] **Step 2: If tests fail, fix and re-run**

Common issues to check:
- `Location.isActive` Lombok: `@Getter` on a boolean field named `isActive` generates `isActive()` not `getIsActive()`. Lombok's `@Getter` for boolean fields generates `is` prefix. Setter will be `setActive(boolean)` not `setIsActive(boolean)`. In `LocationService.deactivate`, use `loc.setActive(false)` not `loc.setIsActive(false)`.
- `ProductVariant` FK: `InventoryServiceIT.setup()` creates a real `Product` via `ProductRepository.save()` before calling `VariantService.create()`. This satisfies the non-deferrable `product_variants.product_id` FK constraint within the same rolled-back transaction.
- `InventoryTransaction` sort: `findByInventoryItemId(UUID, Pageable)` with `Sort.by("createdAt")` — ensure the field name matches the entity field `createdAt` (not `created_at`).

- [ ] **Step 3: Commit any fixes**

```bash
git add -p  # stage only the fix files
git commit -m "fix(inventory): correct any issues found in final verification"
```
