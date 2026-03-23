package it.lrkwz.idempotencyprovider;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

class HeaderIdempotencyKeyExtractor implements IdempotencyKeyExtractor {

    @Override
    public Optional<String> extractKey(Idempotent annotation) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return Optional.empty();

        HttpServletRequest request = attributes.getRequest();
        String key = request.getHeader(annotation.headerName());
        return (key == null || key.isBlank()) ? Optional.empty() : Optional.of(key);
    }
}
