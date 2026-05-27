/**
 * IAM (Identity and Access Management) module: defines roles, permissions, and their
 * assignments to users for role-based access control across the platform. Permission
 * strings (e.g. "order:read", "product:write") are resolved by the IAM service and
 * embedded in JWT claims at token-mint time by the authentication layer.
 */
package io.k2dv.garden.iam;
