/**
 * JPA entity types for the IAM module: {@code Role} (a named bundle of permissions)
 * and {@code Permission} (a single capability string such as "order:read"). Roles are
 * assigned to users via a many-to-many join managed by the User entity in the user module.
 */
package io.k2dv.garden.iam.model;
