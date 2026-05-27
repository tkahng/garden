/**
 * JPA entities for the quote module. {@code QuoteRequest} is the top-level negotiation
 * record whose {@code QuoteStatus} transitions from PENDING through SENT to ACCEPTED or
 * REJECTED; {@code QuoteItem} holds individual negotiated line items; {@code QuoteCart} and
 * {@code QuoteCartItem} represent the pre-submission staging area.
 */
package io.k2dv.garden.quote.model;
