package it.lrkwz.idempotencyprovider;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryIdempotencyStore implements IdempotencyStore {
    private final Map<String, ExpirableValue> cache = new ConcurrentHashMap<>();

    private record ExpirableValue(String value, long expiryTime) {}

    @Override
    public String get(String key) {
        ExpirableValue item = cache.get(key);
        if (item != null && System.currentTimeMillis() < item.expiryTime) {
            return item.value;
        }
        cache.remove(key); // Clean up expired
        return null;
    }

    @Override
    public synchronized boolean setIfAbsent(String key, String value, Duration duration) {
        if (get(key) != null) return false;
        set(key, value, duration);
        return true;
    }

    @Override
    public void set(String key, String value, Duration duration) {
        long expiry = System.currentTimeMillis() + duration.toMillis();
        cache.put(key, new ExpirableValue(value, expiry));
    }

    @Override
    public void remove(String key) {
        cache.remove(key);
    }

    // Optional: Add a @Scheduled task here to clean up the map every hour
}
