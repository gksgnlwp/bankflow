package com.bankflow.bankflow.global.lock;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 계좌 단위 잠금. 트랜잭션 밖에서 호출해야 잠금을 기다리는 동안 DB 커넥션을 쥐고 있지 않는다.
 */
public interface AccountLock {

    <T> T executeWithLock(Collection<String> accountNumbers, Supplier<T> action);
}
