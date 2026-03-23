package it.lrkwz.idempotencyprovider;

import java.util.Optional;

public interface IdempotencyKeyExtractor {
    Optional<String> extractKey(Idempotent annotation);
}
