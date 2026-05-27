/**
 * Service layer for the webhook module, containing {@code OutboundWebhookService} for
 * endpoint CRUD and delivery scheduling, and {@code WebhookDispatchService} for the
 * scheduled HTTP dispatch loop with HMAC-SHA256 payload signing and retry logic.
 */
package io.k2dv.garden.webhook.service;
