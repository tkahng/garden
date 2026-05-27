# Garden — Backend Documentation

Garden is a Shopify-like e-commerce backend for a landscape products vendor. It serves both B2C (storefront) and B2B (companies, quotes, net-terms invoicing) purchase flows.

---

## Documentation Index

| Doc | Contents |
|---|---|
| [Getting Started](getting-started.md) | Prerequisites, local setup, running the app |
| [Architecture](architecture.md) | System design, module map, technology choices |
| [API Conventions](api-conventions.md) | REST patterns, auth headers, pagination, error format |
| [Security](security.md) | JWT, OAuth2, roles, permissions, impersonation |
| [Database](database.md) | Schema map, Flyway migrations, entity conventions |
| [Testing](testing.md) | Test harness, running tests, integration test setup |
| [Deployment](deployment.md) | Docker, CI/CD, environment variables reference |

## Specs Index

Detailed per-module specs live in [`specs/`](specs/):

| Spec | Covers |
|---|---|
| [specs/00-overview.md](specs/00-overview.md) | Module table, DB schema map, security model |
| [specs/01-auth.md](specs/01-auth.md) | Auth, IAM, admin user management |
| [specs/02-catalog.md](specs/02-catalog.md) | Products, collections, reviews |
| [specs/03-cart-checkout.md](specs/03-cart-checkout.md) | Cart, payment, discounts, gift cards, shipping |
| [specs/04-order.md](specs/04-order.md) | Orders, fulfillment, returns |
| [specs/05-b2b.md](specs/05-b2b.md) | B2B companies, quotes, invoices, credit accounts |
| [specs/06-inventory.md](specs/06-inventory.md) | Inventory items, levels, locations, transactions |
| [specs/07-account.md](specs/07-account.md) | Profile, addresses, wishlist, notification prefs |
| [specs/08-content.md](specs/08-content.md) | Blog, articles, pages, search, recommendations |
| [specs/09-infra.md](specs/09-infra.md) | Blob storage, webhooks, stats, audit, schedulers |
