# ADR-0005: Stripe Checkout + Stripe Tax

**Status:** Accepted

## Context

Garden needs to collect payment from B2C shoppers and calculate sales tax across multiple jurisdictions. Options range from building a custom payment form (full PCI scope) to delegating to a hosted checkout (minimal PCI scope). Tax calculation could be handled by a third-party service (TaxJar, Avalara) or by Stripe's built-in tax product.

## Decision

Use **Stripe Checkout** (hosted payment page) for the standard checkout path. The backend creates a Checkout Session with line items and redirects the browser to Stripe's hosted page. Payment confirmation arrives via Stripe webhook (`checkout.session.completed`), which drives the order to `PAID`.

A manual reconciliation endpoint (`POST /orders/{id}/sync-payment`) polls Stripe for session status to handle webhook delivery failures.

Use **Stripe Tax** (automatic tax calculation mode enabled on the Checkout Session) rather than integrating a separate tax service. Stripe Tax infers the customer's tax jurisdiction from the shipping address and applies the correct rate at checkout time. The resulting tax amount is stored on the order after payment confirmation.

## Consequences

**Positive:**
- PCI scope is minimal — card data never touches Garden's servers.
- Stripe Tax eliminates a separate vendor integration and keeps tax rates in sync automatically.
- Stripe's SDK handles idempotency, retries, and webhook signature verification.
- `stripePaymentIntentId` enables direct refunds via the Stripe API without storing payment method details.

**Negative:**
- Stripe's hosted page has limited UI customisation compared to a custom form.
- Stripe Tax coverage varies by country; markets not covered require a separate solution.
- Webhook delivery is not guaranteed: the sync-payment fallback adds complexity that must be tested and monitored.
- Stripe fees apply to every transaction; not suitable if the merchant routes payments through a different processor.
