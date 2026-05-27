/**
 * Order module: covers the full order lifecycle from placement through payment, fulfillment, and
 * refund. Handles both Stripe-based and net-terms (invoice) payment flows, B2B spend-limit approval
 * gating, draft order management, and publishes domain events for transactional email notifications.
 */
package io.k2dv.garden.order;
