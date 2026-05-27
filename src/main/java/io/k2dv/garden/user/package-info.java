/**
 * The user module defines the canonical {@code User} entity and its supporting value types.
 * It is the shared foundation that other modules (auth, account, IAM, orders) build upon
 * to reference an authenticated principal. User lifecycle state is expressed through the
 * {@code UserStatus} enum: {@code UNVERIFIED} on registration, {@code ACTIVE} after email
 * confirmation, and {@code SUSPENDED} when administrative action has restricted access.
 */
package io.k2dv.garden.user;
