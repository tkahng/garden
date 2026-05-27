/**
 * Spring Data JPA repositories for the user module. {@code UserRepository} provides
 * core user lookups (by ID, by email, existence checks) consumed by most other modules.
 * {@code AddressRepository} handles address-book persistence, including queries for
 * ownership verification and default-address management.
 */
package io.k2dv.garden.user.repository;
