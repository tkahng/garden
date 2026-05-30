# Architecture Decision Records

This directory captures significant design decisions made during the development of Garden, a B2B-focused e-commerce platform. Each record follows the lightweight [Nygard format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions): context, decision, consequences.

| # | Title | Status |
|---|-------|--------|
| [0001](0001-jwt-stateless-auth.md) | JWT over server-side sessions | Accepted |
| [0002](0002-transactional-event-listeners.md) | Spring domain events for post-commit side effects | Accepted |
| [0003](0003-webhook-outbox-pattern.md) | Outbox table + scheduled poller for outbound webhooks | Accepted |
| [0004](0004-flyway-versioned-migrations.md) | Flyway for schema evolution | Accepted |
| [0005](0005-stripe-checkout-and-tax.md) | Stripe Checkout + Stripe Tax | Accepted |
| [0006](0006-b2b-quote-workflow.md) | Quote → review → PDF → accept → order flow | Accepted |
