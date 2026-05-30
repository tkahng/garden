# ADR-0003: Outbox Table + Scheduled Poller for Outbound Webhooks

**Status:** Accepted

## Context

Garden allows merchants to register outbound webhook endpoints that receive real-time notifications for order, fulfillment, and invoice events. The simplest approach is to fire an HTTP POST synchronously when an event occurs. However, this creates a dual-write problem: if the database commits but the HTTP call fails (or vice versa), the subscriber either misses an event or receives a duplicate.

An alternative is to use a message broker (Kafka, RabbitMQ), but that introduces significant operational overhead for a system that does not otherwise require a broker.

## Decision

Use a `webhook_deliveries` table as an outbox. When an event occurs, `OutboundWebhookService.scheduleDelivery()` inserts a `WebhookDelivery` row within the same database transaction as the business operation. A separate `WebhookDispatchService` polls for `PENDING` deliveries on a 30-second schedule (protected by ShedLock to prevent duplicate dispatch across instances) and executes the HTTP POST.

Delivery records store: endpoint URL, HMAC-SHA256 signature, event type, payload, attempt count, last HTTP status, and next retry time. Failed deliveries are retried with exponential backoff (1 min → 1 hour → 1 day) up to 5 attempts.

Response headers include `X-Garden-Signature`, `X-Garden-Event`, and `X-Garden-Delivery` to allow subscribers to verify authenticity and deduplicate replays.

## Consequences

**Positive:**
- Delivery is atomic with the business operation — no dual-write risk.
- Retries with backoff survive transient subscriber downtime.
- Full delivery history is queryable from the admin panel.
- No external broker required.

**Negative:**
- At-least-once delivery: subscribers must be idempotent on `X-Garden-Delivery` ID.
- Up to 30-second dispatch latency (polling interval). Real-time use cases requiring sub-second delivery would need a different approach.
- The `webhook_deliveries` table grows indefinitely without a pruning job.
