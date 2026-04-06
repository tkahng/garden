# Custom PostgreSQL Schemas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize all database tables from the default `public` schema into domain-specific custom schemas (`shared`, `auth`, `storage`, `catalog`, `inventory`, `content`) by rewriting all Flyway migrations in-place and adding `schema` attributes to every JPA `@Table` and `@JoinTable` annotation.

**Architecture:** All migration SQL files are rewritten in-place (no new migration added) so table definitions are schema-qualified from the start. Every `CREATE TABLE`, `REFERENCES`, `INSERT INTO`, `ALTER TABLE`, `CREATE TRIGGER`, and `CREATE INDEX` uses fully qualified `schema.table` names with no reliance on `search_path`. JPA entities declare `schema = "..."` on `@Table` so Hibernate also generates fully qualified SQL. The local dev database must be dropped and recreated; test databases are ephemeral (Testcontainers) and pick up the rewritten migrations automatically.

**Tech Stack:** PostgreSQL 17, Flyway 11, Spring Data JPA / Hibernate 6, Java 25, Spring Boot 4

---

## Schema Layout

| Schema | Tables |
|--------|--------|
| `shared` | *(no tables)* — hosts `set_updated_at()` trigger function and test `probe` table |
| `auth` | `users`, `addresses`, `identities`, `tokens`, `roles`, `permissions`, `user_roles`, `role_permissions` |
| `storage` | `blob_objects` |
| `catalog` | `products`, `product_images`, `product_options`, `product_option_values`, `product_variants`, `variant_option_values`, `product_tags`, `product_product_tags` |
| `inventory` | `inventory_items`, `locations`, `inventory_levels`, `inventory_transactions` |
| `content` | `pages`, `blogs`, `articles`, `article_images`, `content_tags`, `article_content_tags` |

## File Map

**Modify (rewrite):**
- `src/main/resources/db/migration/V1__create_utility_functions.sql`
- `src/main/resources/db/migration/V2__create_users.sql`
- `src/main/resources/db/migration/V3__create_identities.sql`
- `src/main/resources/db/migration/V4__create_tokens.sql`
- `src/main/resources/db/migration/V5__create_iam.sql`
- `src/main/resources/db/migration/V6__seed_iam_data.sql`
- `src/main/resources/db/migration/V7__add_updated_at_to_tokens.sql`
- `src/main/resources/db/migration/V8__create_blob_objects.sql`
- `src/main/resources/db/migration/V9__create_products.sql`
- `src/main/resources/db/migration/V10__create_content.sql`
- `src/main/resources/db/migration/V11__add_pages_handle_unique_index.sql`
- `src/main/resources/db/migration/V12__create_inventory.sql`
- `src/test/resources/db/testmigration/V9999__test_probe.sql`

**Modify (add schema attribute):**
- `src/main/java/io/k2dv/garden/user/model/User.java` — `@Table` + `@JoinTable`
- `src/main/java/io/k2dv/garden/user/model/Address.java` — `@Table`
- `src/main/java/io/k2dv/garden/auth/model/Identity.java` — `@Table`
- `src/main/java/io/k2dv/garden/auth/model/Token.java` — `@Table`
- `src/main/java/io/k2dv/garden/iam/model/Role.java` — `@Table` + `@JoinTable`
- `src/main/java/io/k2dv/garden/iam/model/Permission.java` — `@Table`
- `src/main/java/io/k2dv/garden/blob/model/BlobObject.java` — `@Table`
- `src/main/java/io/k2dv/garden/product/model/Product.java` — `@Table` + `@JoinTable`
- `src/main/java/io/k2dv/garden/product/model/ProductImage.java` — `@Table`
- `src/main/java/io/k2dv/garden/product/model/ProductOption.java` — `@Table`
- `src/main/java/io/k2dv/garden/product/model/ProductOptionValue.java` — `@Table`
- `src/main/java/io/k2dv/garden/product/model/ProductVariant.java` — `@Table` + `@JoinTable`
- `src/main/java/io/k2dv/garden/product/model/ProductTag.java` — `@Table`
- `src/main/java/io/k2dv/garden/product/model/InventoryItem.java` — `@Table` (moves to `inventory` schema)
- `src/main/java/io/k2dv/garden/inventory/model/Location.java` — `@Table`
- `src/main/java/io/k2dv/garden/inventory/model/InventoryLevel.java` — `@Table`
- `src/main/java/io/k2dv/garden/inventory/model/InventoryTransaction.java` — `@Table`
- `src/main/java/io/k2dv/garden/content/model/SitePage.java` — `@Table`
- `src/main/java/io/k2dv/garden/content/model/Blog.java` — `@Table`
- `src/main/java/io/k2dv/garden/content/model/Article.java` — `@Table` + `@JoinTable`
- `src/main/java/io/k2dv/garden/content/model/ArticleImage.java` — `@Table`
- `src/main/java/io/k2dv/garden/content/model/ContentTag.java` — `@Table`
- `src/test/java/io/k2dv/garden/shared/model/ProbeEntity.java` — `@Table`

---

## Task 1: Rewrite Flyway Migration Files

**Files:**
- Modify: all 12 files under `src/main/resources/db/migration/`
- Modify: `src/test/resources/db/testmigration/V9999__test_probe.sql`

> There is no test to write for this task — migration SQL is verified by running the test suite in Task 3 (Testcontainers starts a fresh DB for each test run). Do not run the test suite yet; just rewrite the files and commit.

- [ ] **Step 1: Rewrite V1__create_utility_functions.sql**

Replace the entire file with:

```sql
CREATE SCHEMA IF NOT EXISTS shared;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS storage;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS content;

-- Trigger function: sets updated_at to clock_timestamp() on every row update.
-- clock_timestamp() returns the actual wall-clock time at the moment of the call,
-- unlike now() which returns the transaction start time (same for all rows in a
-- transaction). This is critical for @Transactional integration tests where all
-- inserts share one transaction — clock_timestamp() preserves ordering.
CREATE OR REPLACE FUNCTION shared.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 2: Rewrite V2__create_users.sql**

```sql
CREATE TABLE auth.users (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email             TEXT        NOT NULL UNIQUE,
    first_name        TEXT        NOT NULL,
    last_name         TEXT        NOT NULL,
    phone             TEXT,
    status            TEXT        NOT NULL DEFAULT 'UNVERIFIED',
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE auth.addresses (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    first_name  TEXT        NOT NULL,
    last_name   TEXT        NOT NULL,
    company     TEXT,
    address1    TEXT        NOT NULL,
    address2    TEXT,
    city        TEXT        NOT NULL,
    province    TEXT,
    zip         TEXT        NOT NULL,
    country     TEXT        NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.addresses
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

- [ ] **Step 3: Rewrite V3__create_identities.sql**

```sql
CREATE TABLE auth.identities (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider      TEXT        NOT NULL,
    account_id    TEXT        NOT NULL,
    password_hash TEXT,
    access_token  TEXT,
    refresh_token TEXT,
    id_token      TEXT,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (provider, account_id)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.identities
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

- [ ] **Step 4: Rewrite V4__create_tokens.sql**

```sql
-- Stateful tokens: refresh, email verification, password reset.
-- token_hash is SHA-256 of the opaque token string.
-- Hard-deleted on use (rotation = delete old, insert new).
-- Note: expired tokens that are never used accumulate; add a cleanup job in Phase 2.
CREATE TABLE auth.tokens (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type       TEXT        NOT NULL,
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_tokens_user_type ON auth.tokens(user_id, type);
```

- [ ] **Step 5: Rewrite V5__create_iam.sql**

```sql
CREATE TABLE auth.roles (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        TEXT        NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.roles
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE auth.permissions (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        TEXT        NOT NULL UNIQUE,
    resource    TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.permissions
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE auth.user_roles (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES auth.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE auth.role_permissions (
    role_id       UUID NOT NULL REFERENCES auth.roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES auth.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
```

- [ ] **Step 6: Rewrite V6__seed_iam_data.sql**

```sql
-- Roles
INSERT INTO auth.roles (id, name, description, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000001', 'CUSTOMER', 'Storefront customer', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000002', 'STAFF', 'Admin staff — manage products, inventory, content', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000003', 'MANAGER', 'Admin manager — staff + orders, discounts, staff accounts', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000004', 'OWNER', 'Full access including settings, roles, permissions', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- Permissions
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000011', 'product:read',    'product',   'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000012', 'product:write',   'product',   'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000013', 'product:publish', 'product',   'publish', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000014', 'product:delete',  'product',   'delete',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000015', 'inventory:read',  'inventory', 'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000016', 'inventory:write', 'inventory', 'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000017', 'content:read',    'content',   'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000018', 'content:write',   'content',   'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000019', 'content:publish', 'content',   'publish', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000020', 'content:delete',  'content',   'delete',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000021', 'user:read',       'user',      'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000022', 'user:write',      'user',      'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000023', 'staff:manage',    'staff',     'manage',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000024', 'iam:manage',      'iam',       'manage',  clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- CUSTOMER: storefront read only
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'CUSTOMER' AND p.name IN ('product:read', 'content:read')
ON CONFLICT DO NOTHING;

-- STAFF: products + inventory + content (no delete), user:read
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'STAFF'
  AND p.name IN ('product:read', 'product:write', 'product:publish',
                 'inventory:read', 'inventory:write',
                 'content:read', 'content:write', 'content:publish',
                 'user:read')
ON CONFLICT DO NOTHING;

-- MANAGER: staff + deletes + user:write + staff:manage
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('product:read', 'product:write', 'product:publish', 'product:delete',
                 'inventory:read', 'inventory:write',
                 'content:read', 'content:write', 'content:publish', 'content:delete',
                 'user:read', 'user:write', 'staff:manage')
ON CONFLICT DO NOTHING;

-- OWNER: all permissions
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'OWNER'
ON CONFLICT DO NOTHING;
```

- [ ] **Step 7: Rewrite V7__add_updated_at_to_tokens.sql**

```sql
-- tokens table was created without updated_at; add it to satisfy BaseEntity mapping.
ALTER TABLE auth.tokens
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.tokens
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

- [ ] **Step 8: Rewrite V8__create_blob_objects.sql**

```sql
CREATE TABLE storage.blob_objects (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    key          TEXT        NOT NULL UNIQUE,
    filename     TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    size         BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON storage.blob_objects
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000025', 'blob:upload', 'blob', 'upload', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000026', 'blob:delete', 'blob', 'delete', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r, auth.permissions p
WHERE r.name IN ('OWNER', 'MANAGER')
  AND p.name IN ('blob:upload', 'blob:delete')
ON CONFLICT DO NOTHING;
```

- [ ] **Step 9: Rewrite V9__create_products.sql**

Note: `inventory_items` moves to the `inventory` schema here.

```sql
CREATE TABLE catalog.products (
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
    BEFORE UPDATE ON catalog.products
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_images (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES catalog.products(id),
    blob_id    UUID        NOT NULL REFERENCES storage.blob_objects(id),
    alt_text   TEXT,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_images
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_options (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES catalog.products(id),
    name       TEXT        NOT NULL,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_options
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_option_values (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    option_id UUID        NOT NULL REFERENCES catalog.product_options(id),
    label     TEXT        NOT NULL,
    position  INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_option_values
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_variants (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id       UUID          NOT NULL REFERENCES catalog.products(id),
    title            TEXT          NOT NULL,
    sku              TEXT          UNIQUE,
    barcode          TEXT,
    price            NUMERIC(19,4) NOT NULL,
    compare_at_price NUMERIC(19,4),
    weight           NUMERIC(10,4),
    weight_unit      TEXT,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_variants
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.variant_option_values (
    variant_id      UUID NOT NULL REFERENCES catalog.product_variants(id),
    option_value_id UUID NOT NULL REFERENCES catalog.product_option_values(id),
    PRIMARY KEY (variant_id, option_value_id)
);

CREATE TABLE catalog.product_tags (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_tags
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_product_tags (
    product_id UUID NOT NULL REFERENCES catalog.products(id),
    tag_id     UUID NOT NULL REFERENCES catalog.product_tags(id),
    PRIMARY KEY (product_id, tag_id)
);

CREATE TABLE inventory.inventory_items (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    variant_id        UUID        NOT NULL UNIQUE REFERENCES catalog.product_variants(id),
    requires_shipping BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory.inventory_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

- [ ] **Step 10: Rewrite V10__create_content.sql**

```sql
CREATE TABLE content.pages (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title            TEXT        NOT NULL,
    handle           TEXT        NOT NULL,
    body             TEXT,
    status           TEXT        NOT NULL DEFAULT 'DRAFT',
    meta_title       TEXT,
    meta_description TEXT,
    published_at     TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.pages
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.blogs (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title      TEXT        NOT NULL,
    handle     TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.blogs
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.articles (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    blog_id           UUID        NOT NULL REFERENCES content.blogs(id) ON DELETE CASCADE,
    title             TEXT        NOT NULL,
    handle            TEXT        NOT NULL,
    body              TEXT,
    excerpt           TEXT,
    author_id         UUID        REFERENCES auth.users(id) ON DELETE SET NULL,
    author_name       TEXT,
    status            TEXT        NOT NULL DEFAULT 'DRAFT',
    featured_image_id UUID,
    meta_title        TEXT,
    meta_description  TEXT,
    published_at      TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.articles
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.article_images (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    article_id UUID        NOT NULL REFERENCES content.articles(id) ON DELETE CASCADE,
    blob_id    UUID        NOT NULL REFERENCES storage.blob_objects(id),
    alt_text   TEXT,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.article_images
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.content_tags (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.content_tags
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.article_content_tags (
    article_id     UUID NOT NULL REFERENCES content.articles(id),
    content_tag_id UUID NOT NULL REFERENCES content.content_tags(id),
    PRIMARY KEY (article_id, content_tag_id)
);
```

- [ ] **Step 11: Rewrite V11__add_pages_handle_unique_index.sql**

```sql
-- Partial unique index: no two live (non-deleted) pages may share the same handle.
-- Soft-deleted pages (deleted_at IS NOT NULL) are excluded so their handles can be reused.
CREATE UNIQUE INDEX pages_handle_unique ON content.pages(handle) WHERE deleted_at IS NULL;
```

- [ ] **Step 12: Rewrite V12__create_inventory.sql**

```sql
-- New permissions for location management
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000027', 'location:read',  'location', 'read',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000028', 'location:write', 'location', 'write', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- STAFF, MANAGER, OWNER receive both location permissions
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name IN ('STAFF', 'MANAGER', 'OWNER')
  AND p.name IN ('location:read', 'location:write')
ON CONFLICT DO NOTHING;

-- Extend product_variants with fulfillment fields
ALTER TABLE catalog.product_variants
    ADD COLUMN fulfillment_type TEXT NOT NULL DEFAULT 'IN_STOCK',
    ADD COLUMN inventory_policy TEXT NOT NULL DEFAULT 'DENY',
    ADD COLUMN lead_time_days   INT  NOT NULL DEFAULT 0;

-- Locations table
CREATE TABLE inventory.locations (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL,
    address    TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory.locations
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Inventory levels (qty per inventory item × location)
CREATE TABLE inventory.inventory_levels (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    inventory_item_id  UUID        NOT NULL REFERENCES inventory.inventory_items(id) ON DELETE CASCADE,
    location_id        UUID        NOT NULL REFERENCES inventory.locations(id) ON DELETE CASCADE,
    quantity_on_hand   INT         NOT NULL DEFAULT 0,
    quantity_committed INT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (inventory_item_id, location_id)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory.inventory_levels
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Inventory transactions (immutable ledger — no updated_at)
CREATE TABLE inventory.inventory_transactions (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    inventory_item_id UUID        NOT NULL REFERENCES inventory.inventory_items(id) ON DELETE RESTRICT,
    location_id       UUID        NOT NULL REFERENCES inventory.locations(id) ON DELETE RESTRICT,
    quantity          INT         NOT NULL,
    reason            TEXT        NOT NULL,
    note              TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
```

- [ ] **Step 13: Rewrite V9999__test_probe.sql**

```sql
-- Test-only table for verifying BaseEntity behaviour.
-- Dropped automatically when the Testcontainer is destroyed.
CREATE TABLE shared.probe (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    label      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON shared.probe
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

- [ ] **Step 14: Commit**

```bash
git add src/main/resources/db/migration/ src/test/resources/db/testmigration/
git commit -m "refactor(db): rewrite migrations with fully qualified schema-prefixed table names"
```

---

## Task 2: Add Schema Attributes to JPA Entities

**Files:** 23 Java files listed in the File Map above.

> No test to write — Hibernate's `ddl-auto=validate` will reject a startup with mismatched schema names, which is caught by Task 3. Commit after all entity changes are done.

- [ ] **Step 1: Update auth schema entities**

`src/main/java/io/k2dv/garden/user/model/User.java`:
- `@Table(name = "users")` → `@Table(schema = "auth", name = "users")`
- `@JoinTable(name = "user_roles", ...)` → `@JoinTable(schema = "auth", name = "user_roles", ...)`

`src/main/java/io/k2dv/garden/user/model/Address.java`:
- `@Table(name = "addresses")` → `@Table(schema = "auth", name = "addresses")`

`src/main/java/io/k2dv/garden/auth/model/Identity.java`:
- `@Table(name = "identities", uniqueConstraints = {...})` → `@Table(schema = "auth", name = "identities", uniqueConstraints = {...})`

`src/main/java/io/k2dv/garden/auth/model/Token.java`:
- `@Table(name = "tokens")` → `@Table(schema = "auth", name = "tokens")`

`src/main/java/io/k2dv/garden/iam/model/Role.java`:
- `@Table(name = "roles")` → `@Table(schema = "auth", name = "roles")`
- `@JoinTable(name = "role_permissions", ...)` → `@JoinTable(schema = "auth", name = "role_permissions", ...)`

`src/main/java/io/k2dv/garden/iam/model/Permission.java`:
- `@Table(name = "permissions")` → `@Table(schema = "auth", name = "permissions")`

- [ ] **Step 2: Update storage schema entity**

`src/main/java/io/k2dv/garden/blob/model/BlobObject.java`:
- `@Table(name = "blob_objects")` → `@Table(schema = "storage", name = "blob_objects")`

- [ ] **Step 3: Update catalog schema entities**

`src/main/java/io/k2dv/garden/product/model/Product.java`:
- `@Table(name = "products")` → `@Table(schema = "catalog", name = "products")`
- `@JoinTable(name = "product_product_tags", ...)` → `@JoinTable(schema = "catalog", name = "product_product_tags", ...)`

`src/main/java/io/k2dv/garden/product/model/ProductImage.java`:
- `@Table(name = "product_images")` → `@Table(schema = "catalog", name = "product_images")`

`src/main/java/io/k2dv/garden/product/model/ProductOption.java`:
- `@Table(name = "product_options")` → `@Table(schema = "catalog", name = "product_options")`

`src/main/java/io/k2dv/garden/product/model/ProductOptionValue.java`:
- `@Table(name = "product_option_values")` → `@Table(schema = "catalog", name = "product_option_values")`

`src/main/java/io/k2dv/garden/product/model/ProductVariant.java`:
- `@Table(name = "product_variants")` → `@Table(schema = "catalog", name = "product_variants")`
- `@JoinTable(name = "variant_option_values", ...)` → `@JoinTable(schema = "catalog", name = "variant_option_values", ...)`

`src/main/java/io/k2dv/garden/product/model/ProductTag.java`:
- `@Table(name = "product_tags")` → `@Table(schema = "catalog", name = "product_tags")`

- [ ] **Step 4: Update inventory schema entities**

`src/main/java/io/k2dv/garden/product/model/InventoryItem.java` *(Java package stays in `product`, DB schema moves to `inventory`)*:
- `@Table(name = "inventory_items")` → `@Table(schema = "inventory", name = "inventory_items")`

`src/main/java/io/k2dv/garden/inventory/model/Location.java`:
- `@Table(name = "locations")` → `@Table(schema = "inventory", name = "locations")`

`src/main/java/io/k2dv/garden/inventory/model/InventoryLevel.java`:
- `@Table(name = "inventory_levels", uniqueConstraints = {...})` → `@Table(schema = "inventory", name = "inventory_levels", uniqueConstraints = {...})`

`src/main/java/io/k2dv/garden/inventory/model/InventoryTransaction.java`:
- `@Table(name = "inventory_transactions")` → `@Table(schema = "inventory", name = "inventory_transactions")`

- [ ] **Step 5: Update content schema entities**

`src/main/java/io/k2dv/garden/content/model/SitePage.java`:
- `@Table(name = "pages")` → `@Table(schema = "content", name = "pages")`

`src/main/java/io/k2dv/garden/content/model/Blog.java`:
- `@Table(name = "blogs")` → `@Table(schema = "content", name = "blogs")`

`src/main/java/io/k2dv/garden/content/model/Article.java`:
- `@Table(name = "articles")` → `@Table(schema = "content", name = "articles")`
- `@JoinTable(name = "article_content_tags", ...)` → `@JoinTable(schema = "content", name = "article_content_tags", ...)`

`src/main/java/io/k2dv/garden/content/model/ArticleImage.java`:
- `@Table(name = "article_images")` → `@Table(schema = "content", name = "article_images")`

`src/main/java/io/k2dv/garden/content/model/ContentTag.java`:
- `@Table(name = "content_tags")` → `@Table(schema = "content", name = "content_tags")`

- [ ] **Step 6: Update test ProbeEntity**

`src/test/java/io/k2dv/garden/shared/model/ProbeEntity.java`:
- `@Table(name = "probe")` → `@Table(schema = "shared", name = "probe")`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ src/test/java/
git commit -m "refactor(db): add schema attributes to all JPA @Table and @JoinTable annotations"
```

---

## Task 3: Reset Local Database and Verify Tests Pass

**Files:** None (no code changes — this is a verification task)

> This task resets the local dev database so it is recreated from the rewritten migrations, then runs the full test suite to confirm Testcontainers picks up the new schema layout correctly.

- [ ] **Step 1: Drop and recreate the local dev database**

The local database still has tables in `public` from before the rewrite. Flyway's checksum validation will reject the changed migration files unless we start fresh.

Connect to the local Postgres instance and run:

```sql
DROP DATABASE IF EXISTS garden;
CREATE DATABASE garden;
```

Using psql:
```bash
psql -h localhost -p 5432 -U garden -d postgres -c "DROP DATABASE IF EXISTS garden; CREATE DATABASE garden OWNER garden;"
```

If using Docker Compose, alternatively:
```bash
docker compose down -v && docker compose up -d
```

- [ ] **Step 2: Run the full test suite**

Testcontainers starts a fresh PostgreSQL container per test run, so it will apply the rewritten migrations from scratch. No manual DB reset is needed for tests.

```bash
./mvnw clean test
```

Expected: `Tests run: 141, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`

If Hibernate validation fails (e.g., `Table 'auth.users' not found`), the likely cause is a mismatch between a migration table name and the entity's `@Table(schema, name)`. Check the error message, fix the mismatch, and re-run.

- [ ] **Step 3: Smoke-test local dev startup**

Start the application against the freshly recreated local DB to confirm Flyway runs cleanly and the app starts without validation errors:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Expected: application starts on port 8080 with no errors in the Flyway or Hibernate logs.

- [ ] **Step 4: Commit (if any fixes were made during verification)**

If no fixes were needed, there is nothing to commit here.

```bash
git add -p
git commit -m "fix(db): correct schema/table name mismatch found during verification"
```
