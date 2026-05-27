/**
 * Service layer for the cart module. Contains {@code CartService}, which is the single entry point
 * for all cart mutations and also exposes an internal API consumed by {@code PaymentService} during
 * checkout to atomically lock and transition the cart.
 */
package io.k2dv.garden.cart.service;
