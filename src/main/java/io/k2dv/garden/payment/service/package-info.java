/**
 * Service layer for the payment module. {@code PaymentService} is the primary entry point for
 * checkout initiation and webhook handling, delegating to {@code OrderService} for order state
 * transitions and to the {@code StripeGateway} for all Stripe API calls.
 */
package io.k2dv.garden.payment.service;
