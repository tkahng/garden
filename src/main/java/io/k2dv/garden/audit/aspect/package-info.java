/**
 * AOP aspect and annotation that form the declarative audit instrumentation layer.
 * Methods annotated with {@code @Audited} are intercepted by {@code AuditAspect},
 * which resolves the entity ID via SpEL and delegates to {@code AuditLogService}.
 */
package io.k2dv.garden.audit.aspect;
