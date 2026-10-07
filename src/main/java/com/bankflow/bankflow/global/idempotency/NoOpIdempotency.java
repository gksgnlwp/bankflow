package com.bankflow.bankflow.global.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "bankflow.idempotency.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpIdempotency implements Idempotency {

    @Override
    public <T> T execute(String scope, String key, Class<T> responseType, Supplier<T> action) {
        return action.get();
    }
}
