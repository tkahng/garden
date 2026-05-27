/**
 * Gift card module: handles issuance, redemption, and per-card balance tracking. Gift cards
 * can be applied at checkout to reduce the order total; partial redemption is supported, and
 * every balance change is recorded as an immutable transaction for auditability.
 */
package io.k2dv.garden.giftcard;
