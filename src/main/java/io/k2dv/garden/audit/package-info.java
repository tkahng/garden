/**
 * Audit logging module that records admin mutations via the {@code @Audited} AOP aspect.
 * Entries are written to an append-only audit log table in an independent transaction,
 * ensuring durability even when the triggering business transaction is rolled back.
 */
package io.k2dv.garden.audit;
