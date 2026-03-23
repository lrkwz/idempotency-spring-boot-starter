package it.lrkwz.idempotencyprovider;

import java.time.Duration;

public interface IdempotencyStore {
    String get(String key);
    boolean setIfAbsent(String key, String value, Duration duration);
    void set(String key, String value, Duration duration);
    void remove(String key);
}
