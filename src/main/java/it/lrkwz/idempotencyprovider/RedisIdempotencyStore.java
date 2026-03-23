package it.lrkwz.idempotencyprovider;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redisTemplate;

    // Explicit constructor to fix the Configuration compilation error
    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration duration) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, duration));
    }

    @Override
    public void set(String key, String value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    @Override
    public void remove(String key) {
        redisTemplate.delete(key);
    }
}
