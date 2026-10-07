package com.bankflow.bankflow.global.idempotency;

import java.util.function.Supplier;

/**
 * 같은 Idempotency-Key로 다시 들어온 요청은 처리하지 않고 처음 응답을 돌려준다.
 */
public interface Idempotency {

    <T> T execute(String scope, String key, Class<T> responseType, Supplier<T> action);
}
