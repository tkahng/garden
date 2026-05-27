/**
 * Service layer for the IAM module, providing permission resolution with caching and
 * role assignment operations. The primary consumer is the authentication token pipeline;
 * the admin IAM service in {@code admin/iam} handles catalog management of roles and permissions.
 */
package io.k2dv.garden.iam.service;
