/**
 * JPA entities for the cart module. A {@code Cart} is owned by either a registered user (userId)
 * or an anonymous session (sessionId) and may carry a B2B company reference; {@code CartItem}
 * holds the variant, quantity, and resolved unit price for each line in the cart.
 */
package io.k2dv.garden.cart.model;
