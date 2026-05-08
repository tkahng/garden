package io.k2dv.garden.audit.aspect;

import java.lang.annotation.*;

/**
 * Marks an admin service method for audit logging.
 * The interceptor captures the caller's identity and the method name as the action.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    /** Logical entity type (e.g. "product", "discount", "user"). */
    String entityType();
    /** SpEL expression to extract the entity ID from the method arguments (e.g. "#id"). */
    String entityId() default "";
}
