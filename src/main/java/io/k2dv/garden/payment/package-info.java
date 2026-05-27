/**
 * Payment module: orchestrates Stripe Checkout session creation, inbound webhook processing, and
 * refund issuance. Also handles gift-card redemption, automatic discount application, and the
 * net-terms (credit account) payment path for B2B customers.
 */
package io.k2dv.garden.payment;
