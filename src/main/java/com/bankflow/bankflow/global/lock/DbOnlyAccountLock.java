package com.bankflow.bankflow.global.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 별도 잠금 없이 실행한다. 동시성은 DB 비관적 락(SELECT ... FOR UPDATE)만으로 보장한다.
 */
@Component
@ConditionalOnProperty(name = "bankflow.lock.strategy", havingValue = "db", matchIfMissing = true)
public class DbOnlyAccountLock implements AccountLock {

    @Override
    public <T> T executeWithLock(Collection<String> accountNumbers, Supplier<T> action) {
        return action.get();
    }
}
