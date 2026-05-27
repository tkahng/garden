# Garden — System Overview

**Status:** Current  
**Last updated:** 2026-05-26  
**Package root:** `io.k2dv.garden`  
**Stack:** Spring Boot 4.0.4 · Java 26 · PostgreSQL · Flyway · Spring Security (OAuth2 + JWT RS256) · Stripe · AWS S3 · ShedLock

---

## What it is

Garden is a Shopify-like e-commerce backend for a landscape products vendor (planters, green roof trays, edging). It supports both B2C and B2B purchase flows.

---

## Modules

| Module | Package | Description |
|---|---|---|
| Auth | `auth` | JWT auth, OAuth2 (Google), token rotation, impersonation |
| IAM | `iam`, `admin.iam` | Roles, permissions, admin RBAC |
| Account | `account` | Profile, saved addresses |
| User | `user` | User entity, status, tags, metadata |
| Product | `product` | Catalog — products, variants, options, images, tags |
| Collection | `collection` | Manual and automated collections |
| Cart | `cart` | Storefront and guest carts |
| Checkout / Payment | `payment` | Stripe checkout, webhook reconciliation |
| Order | `order` | Order lifecycle, events, returns |
| Fulfillment | `fulfillment` | Shipping, tracking, line-item fulfillment |
| Discount | `discount` | Codes and automatic discounts |
| Gift Card | `giftcard` | Issuance and redemption |
| B2B | `b2b`, `quote` | Companies, price lists, quotes, invoices, credit accounts |
| Inventory | `inventory` | Inventory items, levels, locations, transactions |
| Shipping | `shipping` | Zones, rates, carrier rules |
| Review | `review` | Product reviews with verification |
| Wishlist | `wishlist` | Per-user saved variants |
| Notification | `notification` | Per-user notification preferences |
| Content | `content` | Blogs, articles, pages |
| Search | `search` | Full-text across products, collections, articles, pages |
| Recommendation | `recommendation` | Tag-overlap related products |
| Newsletter | `newsletter` | Email opt-in / opt-out |
| Blob | `blob` | File upload (S3-backed), signed URLs |
| Webhook | `webhook` | Outbound webhooks with retry |
| Stats | `stats` | Admin revenue and customer analytics |
| Audit | `audit` | Admin audit log via `@Audited` AOP |
| Scheduler | `scheduler` | ShedLock-managed background jobs |
| Automation | `automation` | Auto-tagging users based on order behavior |

---

## Database Schemas

| Postgres Schema | Modules |
|---|---|
| `auth` | users, addresses, identities, refresh_tokens, tokens, impersonation_tokens, roles, permissions, notification_preferences |
| `catalog` | products, product_variants, product_options, product_option_values, product_images, product_tags, collections, collection_rules, product_reviews, wishlists |
| `checkout` | carts, cart_items, orders, order_items, order_events, fulfillments, fulfillment_items, discounts, gift_cards, gift_card_transactions, order_templates |
| `b2b` | companies, company_memberships, company_invitations, price_lists, price_list_entries, credit_accounts, invoices, invoice_payments |
| `quote` | quote_carts, quote_cart_items, quote_requests, quote_items |
| `inventory` | inventory_items, inventory_levels, inventory_transactions, locations |
| `shipping` | shipping_zones, shipping_rates |
| `content` | blogs, articles, article_images, pages, content_tags |
| `payment` | processed_stripe_events |
| `storage` | blob_objects |
| `webhook` | endpoints, deliveries |
| `marketing` | newsletter_subscribers |
| `shared` | audit_log |

---

## Security Model

- **JWT RS256** access tokens issued by Garden; short-lived
- **Refresh tokens** — opaque UUID, SHA-256 hashed at rest; rotation with replay detection (compromised token revokes entire family)
- **OAuth2** — Google provider via Spring Security OAuth2 client; `OAuth2SuccessHandler` upserts user and identity records
- **`@Authenticated`** — method-level annotation requiring a valid JWT
- **`@HasPermission("resource:action")`** — permission check against claims loaded from IAM
- **Impersonation** — admin mints a one-use 30-minute token; cannot impersonate STAFF/MANAGER/OWNER

---

## Shared Infrastructure

- **`BaseEntity`** — UUID v7 PK, `createdAt`, `updatedAt` (all mutable entities)
- **`ImmutableBaseEntity`** — UUID v7 PK, `createdAt` only (payments, transactions, audit records)
- **`ApiResponse<T>`** — standard response wrapper
- **`PagedResult<T>`** — paginated list response
- **`GlobalExceptionHandler`** — maps domain exceptions to HTTP status codes
- **`ApiRateLimitFilter`** — per-IP rate limiting; login endpoint has additional `LoginRateLimiter` (1/min per email)
- **`MdcLoggingFilter`** — injects `requestId`, `userId` into MDC per request
- **`CurrentUserArgumentResolver`** — resolves `@CurrentUser` parameter in controllers
