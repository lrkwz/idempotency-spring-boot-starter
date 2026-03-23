package it.lrkwz.idempotencyprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyKeyExtractor.class)
    IdempotencyKeyExtractor headerIdempotencyKeyExtractor() {
        return new HeaderIdempotencyKeyExtractor();
    }

    @Bean
    IdempotencyAspect idempotencyAspect(IdempotencyStore idempotencyStore,
                                        ObjectMapper objectMapper,
                                        IdempotencyKeyExtractor keyExtractor) {
        return new IdempotencyAspect(idempotencyStore, objectMapper, keyExtractor);
    }
}
