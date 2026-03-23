package it.lrkwz.idempotencyprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Aspect
class IdempotencyAspect {

    static final String LOCK_VALUE = "IN_PROGRESS";

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final IdempotencyKeyExtractor keyExtractor;

    IdempotencyAspect(IdempotencyStore idempotencyStore,
                      ObjectMapper objectMapper,
                      IdempotencyKeyExtractor keyExtractor) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.keyExtractor = keyExtractor;
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Optional<String> key = keyExtractor.extractKey(idempotent);
        if (key.isEmpty()) return joinPoint.proceed();

        String methodPrefix = joinPoint.getSignature().toShortString();
        String argHash = Integer.toHexString(Arrays.deepHashCode(joinPoint.getArgs()));
        String storageKey = "idempotency:" + key.get() + ":" + methodPrefix + ":" + argHash;

        String cachedValue = idempotencyStore.get(storageKey);
        if (cachedValue != null) {
            if (LOCK_VALUE.equals(cachedValue)) {
                throw new ResponseStatusException(HttpStatus.TOO_EARLY, "Request processing");
            }
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
