/**
 * Service layer for the discount module. Contains {@code DiscountService}, which handles
 * coupon creation, eligibility validation, and atomic redemption at checkout to prevent
 * over-use under concurrent traffic.
 */
package io.k2dv.garden.discount.service;
