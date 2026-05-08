# Garden — Backend API

Spring Boot REST API for the Garden e-commerce platform. Designed as a Shopify alternative with a strong B2B focus.

## Stack

- **Java 26** · Spring Boot 4
- **PostgreSQL** with Flyway migrations (58 versioned migrations)
- **Spring Security** with JWT auth and permission-based RBAC
- **Stripe** for payments and tax (Stripe Tax)
- **S3-compatible** object storage for images and PDFs
- **Thymeleaf + OpenHTMLtoPDF** for quote and invoice PDF generation
- **Testcontainers** for integration tests

## What's in it

**Core commerce**
- Products, variants, collections, inventory management across locations
- Cart (authenticated and guest), checkout via Stripe, order lifecycle

**B2B**
- Companies with multi-user membership (Owner / Manager / Member)
- Per-company product catalogs and price lists with quantity-break pricing and percentage-off / markup rules
- Quote workflow: cart → quote → review → PDF → accept → order
- Net terms / invoicing: credit accounts, payment terms, invoice PDF generation
- Spending limits with manager-level approval routing
- Company shipping address book

**Post-order**
- Fulfillment with item-level tracking, shipping emails, delivery confirmation
- Return requests (RMA): submit, approve, reject, complete
- Refunds via Stripe

**Other**
- Discounts (fixed, percentage, automatic promotions)
- Gift cards with balance tracking
- Order templates for repeat ordering
- Email notification preferences per user
- Outbound webhooks with delivery history
- Wishlists, product reviews, recommendations
- Analytics/stats endpoints

## Running locally

```bash
# Requires PostgreSQL and Java 26
./mvnw spring-boot:run
```

The API runs on port **8080**. OpenAPI docs are at `/swagger-ui.html`.

## Tests

```bash
./mvnw test
```

845 tests — unit (MockMvc web-layer) and integration (Testcontainers Postgres).

## Environment

Copy `application.properties` and set:

```
spring.datasource.url=jdbc:postgresql://localhost:5432/garden
spring.datasource.username=...
spring.datasource.password=...
stripe.secret-key=...
stripe.webhook-secret=...
app.frontend-url=http://localhost:3000
```
