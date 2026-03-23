package it.lrkwz.idempotencyprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyAspectTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private HttpServletRequest request;

    @Mock
    private Idempotent idempotentAnnotation;

    @Spy
    private IdempotencyKeyExtractor keyExtractor = new HeaderIdempotencyKeyExtractor();

    @InjectMocks
    private IdempotencyAspect aspect;

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(idempotentAnnotation.headerName()).thenReturn("Idempotency-Key");
        when(idempotentAnnotation.ttlInHours()).thenReturn(24);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.toShortString()).thenReturn("MyController.createOrder");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldProceedWhenHeaderIsMissing() throws Throwable {
        when(request.getHeader("Idempotency-Key")).thenReturn(null);

        aspect.handleIdempotency(joinPoint, idempotentAnnotation);

        verify(joinPoint).proceed();
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void shouldReturnCachedValueOnHit() throws Throwable {
        String key = "test-key";
        String cachedJson = "\"SuccessPayload\"";

        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(idempotencyStore.get(anyString())).thenReturn(cachedJson);
        doReturn(String.class).when(methodSignature).getReturnType();

        Object result = aspect.handleIdempotency(joinPoint, idempotentAnnotation);

        assertEquals("SuccessPayload", result);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldHandleMapReturnTypes() throws Throwable {
        String key = "test-key-map";
        String cachedJson = "{\"status\":\"ok\"}";

        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(idempotencyStore.get(anyString())).thenReturn(cachedJson);
        doReturn(Map.class).when(methodSignature).getReturnType();

        Object result = aspect.handleIdempotency(joinPoint, idempotentAnnotation);

        assertInstanceOf(Map.class, result);
        assertEquals("ok", ((Map<?, ?>) result).get("status"));
    }

    @Test
    void shouldThrowExceptionWhenProcessIsInProgress() {
        when(request.getHeader("Idempotency-Key")).thenReturn("key");
        when(idempotencyStore.get(anyString())).thenReturn(IdempotencyAspect.LOCK_VALUE);

        assertThrows(ResponseStatusException.class, () ->
                aspect.handleIdempotency(joinPoint, idempotentAnnotation)
        );
    }

    @Test
    void shouldExecuteAndCacheOnMiss() throws Throwable {
        String key = "new-key";
        String resultBody = "Created";

        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(idempotencyStore.get(anyString())).thenReturn(null);
        when(idempotencyStore.setIfAbsent(anyString(), eq(IdempotencyAspect.LOCK_VALUE), any())).thenReturn(true);
        when(joinPoint.proceed()).thenReturn(resultBody);

        Object result = aspect.handleIdempotency(joinPoint, idempotentAnnotation);

        assertEquals(resultBody, result);
        verify(idempotencyStore).set(anyString(), contains("Created"), any());
    }
}
