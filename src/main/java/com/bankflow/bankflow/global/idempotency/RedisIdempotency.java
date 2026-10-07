package com.bankflow.bankflow.global.idempotency;

import com.bankflow.bankflow.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 네트워크 재시도나 중복 클릭으로 같은 입금·출금·이체가 두 번 처리되는 것을 막는다.
 * 키 상태: 없음 → PROCESSING(처리 중) → 응답 JSON(완료). 실패하면 키를 지워 다시 시도할 수 있게 한다.
 */
@Component
@ConditionalOnProperty(name = "bankflow.idempotency.enabled", havingValue = "true")
public class RedisIdempotency implements Idempotency {

    private static final String KEY_PREFIX = "idempotency:";
    private static final String PROCESSING = "PROCESSING";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    public RedisIdempotency(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            @Value("${bankflow.idempotency.ttl:24h}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.ttl = ttl;
    }

    @Override
    public <T> T execute(String scope, String key, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }

        String redisKey = KEY_PREFIX + scope + ":" + key;
        boolean first = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING, ttl)
        );

        if (!first) {
            return replay(redisKey, responseType);
        }

        try {
            T response = action.get();
            redisTemplate.opsForValue().set(redisKey, jsonMapper.writeValueAsString(response), ttl);
            return response;
        } catch (RuntimeException e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    private <T> T replay(String redisKey, Class<T> responseType) {
        String saved = redisTemplate.opsForValue().get(redisKey);

        if (saved == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "이전 요청이 실패했습니다. 다시 시도해 주세요.");
        }
        if (PROCESSING.equals(saved)) {
            throw new BusinessException(HttpStatus.CONFLICT, "같은 요청을 처리하고 있습니다.");
        }
        return jsonMapper.readValue(saved, responseType);
    }
}
