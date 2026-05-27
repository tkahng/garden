/**
 * JPA entities for the user module. The central {@code User} entity holds core identity
 * fields (email, name, phone, email-verified timestamp, status) shared across the platform.
 * The {@code Address} entity represents a user's saved shipping or billing address.
 * The {@code UserStatus} enum captures the three-state lifecycle of a user account.
 */
package io.k2dv.garden.user.model;
