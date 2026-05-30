# ADR-0002: Spring Domain Events for Post-Commit Side Effects

**Status:** Accepted

## Context

Several order and fulfillment transitions trigger side effects: confirmation emails, cancellation emails, shipping notifications, and auto-tagging. The naive approach is to call these services directly inside the `@Transactional` method (e.g., `emailService.send(...)` at the end of `confirmPayment()`).

The problem: if the email call is slow or throws, it either blocks the transaction or, if caught, silently swallows a real failure. Worse, the email fires even if the database transaction is later rolled back, resulting in a confirmation email for an order that was never committed.

## Decision

Use Spring's `ApplicationEventPublisher` to publish lightweight record events (e.g., `OrderConfirmedEvent`, `FulfillmentShippedEvent`) from within the transactional business logic. Listeners are annotated with `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("emailExecutor")`.

This guarantees:
1. The side effect only fires if the database transaction commits successfully.
2. Email sending happens on a dedicated thread pool, not the HTTP request thread.
3. Services like `OrderService` and `FulfillmentService` depend only on `ApplicationEventPublisher`, not on `EmailService` — a clean inversion.

Listener failures are caught and logged at ERROR level so alerting can pick them up without propagating back to callers.

## Consequences

**Positive:**
- No spurious emails on rollback.
- Email latency is off the hot path.
- Each concern (business logic, email, webhooks) is independently testable.

**Negative:**
- Delivery is best-effort: if the process crashes between commit and listener execution, the email is lost. Acceptable for transactional email; not acceptable for financial events (see ADR-0003 for the webhook approach).
- The async executor must be configured with an appropriate queue size to avoid silent task drops under load.
