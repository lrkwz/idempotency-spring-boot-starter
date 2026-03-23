package it.lrkwz.idempotencyprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;

@Aspect
@Component
public class IdempotencyAspect {

    private static final String LOCK_VALUE = "IN_PROGRESS";

    private final IdempotencyStore idempotencyStore;

    private final ObjectMapper objectMapper;

    public IdempotencyAspect(final IdempotencyStore idempotencyStore, final ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return joinPoint.proceed();

        HttpServletRequest request = attributes.getRequest();
        String key = request.getHeader(idempotent.headerName());

        if (key == null || key.isBlank()) {
            return joinPoint.proceed();
        }

        String methodPrefix = joinPoint.getSignature().toShortString();
        String argHash = Integer.toHexString(Arrays.deepHashCode(joinPoint.getArgs()));
        String storageKey = "idempotency:" + key + ":" + methodPrefix + ":" + argHash;

        String cachedValue = idempotencyStore.get(storageKey);
        if (cachedValue != null) {
            if (LOCK_VALUE.equals(cachedValue)) {
                throw new ResponseStatusException(HttpStatus.TOO_EARLY, "Request processing");
            }
            // Fix: Cast to org.aspectj.lang.reflect.MethodSignature
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return objectMapper.readValue(cachedValue, signature.getReturnType());
        }

        boolean locked = idempotencyStore.setIfAbsent(storageKey, LOCK_VALUE, Duration.ofMinutes(5));
        if (!locked) {
            throw new ResponseStatusException(HttpStatus.TOO_EARLY, "Concurrent request");
        }

        try {
            Object result = joinPoint.proceed();
            String json = objectMapper.writeValueAsString(result);
            idempotencyStore.set(storageKey, json, Duration.ofHours(idempotent.ttlInHours()));
            return result;
        } catch (Exception e) {
            idempotencyStore.remove(storageKey);
            throw e;
        }
    }
}
