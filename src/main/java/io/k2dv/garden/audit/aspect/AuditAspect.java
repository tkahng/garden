package io.k2dv.garden.audit.aspect;

import io.k2dv.garden.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ExpressionParser spel = new SpelExpressionParser();

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed();

        try {
            UUID actorId = null;
            String actorEmail = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                String sub = jwt.getSubject();
                if (sub != null) {
                    try { actorId = UUID.fromString(sub); } catch (IllegalArgumentException ignored) {}
                }
                actorEmail = jwt.getClaimAsString("email");
            }

            String entityId = resolveEntityId(audited.entityId(), pjp);
            String action = ((MethodSignature) pjp.getSignature()).getMethod().getName();

            auditLogService.record(actorId, actorEmail, action,
                audited.entityType(), entityId, null, null);
        } catch (Exception e) {
            log.warn("Audit logging failed for {}: {}", pjp.getSignature().getName(), e.getMessage());
        }

        return result;
    }

    private String resolveEntityId(String expression, ProceedingJoinPoint pjp) {
        if (expression == null || expression.isBlank()) return null;
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            Parameter[] params = sig.getMethod().getParameters();
            Object[] args = pjp.getArgs();
            // SimpleEvaluationContext allows only property/field/method access on the
            // provided root/variables — it intentionally blocks class instantiation,
            // static access, and reflection, preventing SpEL injection via user-supplied
            // annotation expressions.
            SimpleEvaluationContext ctx = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .build();
            for (int i = 0; i < params.length; i++) {
                ctx.setVariable(params[i].getName(), args[i]);
            }
            Object val = spel.parseExpression(expression).getValue(ctx);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.debug("Failed to resolve entityId expression '{}': {}", expression, e.getMessage());
            return null;
        }
    }
}
