package com.bankflow.bankflow.global.lock;

import com.bankflow.bankflow.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Redis SET NX 기반 계좌 잠금.
 * 잠금을 기다리는 요청이 DB 커넥션 대신 Redis에서 대기하므로, 한 계좌에 요청이 몰려도
 * 다른 계좌의 요청이 커넥션을 얻지 못해 함께 느려지는 일을 막는다.
 * DB 비관적 락은 그대로 두어, 잠금이 만료되는 예외 상황에서도 잔액 정합성은 DB가 지킨다.
 *
 * 같은 서버 안의 요청은 먼저 공정한 로컬 락에 순서대로 줄을 세우고, 맨 앞 요청만 Redis 잠금을 시도한다.
 * 모든 요청이 Redis를 반복 조회하면 해제 직후 빈틈이 생기고 순서도 뒤섞여 처리량이 떨어지기 때문이다.
 */
@Component
@ConditionalOnProperty(name = "bankflow.lock.strategy", havingValue = "redis")
public class RedisAccountLock implements AccountLock {

    private static final String KEY_PREFIX = "lock:account:";
    private static final int LOCAL_STRIPES = 1024;

    // 내가 건 잠금일 때만 해제 (만료 후 다른 요청이 잡은 잠금을 지우지 않도록)
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final Duration leaseTime;
    private final Duration waitTime;
    private final ReentrantLock[] localLocks = new ReentrantLock[LOCAL_STRIPES];

    public RedisAccountLock(
            StringRedisTemplate redisTemplate,
            @Value("${bankflow.lock.lease-time:3s}") Duration leaseTime,
            @Value("${bankflow.lock.wait-time:5s}") Duration waitTime
    ) {
        this.redisTemplate = redisTemplate;
        this.leaseTime = leaseTime;
        this.waitTime = waitTime;
        for (int i = 0; i < LOCAL_STRIPES; i++) {
            localLocks[i] = new ReentrantLock(true);
        }
    }

    @Override
    public <T> T executeWithLock(Collection<String> accountNumbers, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        // 이체처럼 여러 계좌를 잠글 때 교착 상태를 피하기 위해 항상 정렬된 순서로 잠근다
        List<String> keys = accountNumbers.stream()
                .distinct()
                .sorted()
                .map(number -> KEY_PREFIX + number)
                .toList();

        // 로컬 락도 항상 같은 순서(스트라이프 번호 순)로 잡아 서버 안에서의 교착을 막는다
        List<ReentrantLock> stripes = keys.stream()
                .map(key -> Math.floorMod(key.hashCode(), LOCAL_STRIPES))
                .distinct()
                .sorted()
                .map(index -> localLocks[index])
                .toList();

        long deadline = System.nanoTime() + waitTime.toNanos();
        Deque<ReentrantLock> lockedStripes = new ArrayDeque<>();
        Deque<String> acquired = new ArrayDeque<>();
        try {
            for (ReentrantLock stripe : stripes) {
                acquireLocal(stripe, deadline);
                lockedStripes.push(stripe);
            }
            for (String key : keys) {
                acquire(key, token, deadline);
                acquired.push(key);
            }
            return action.get();
        } finally {
            while (!acquired.isEmpty()) {
                redisTemplate.execute(UNLOCK_SCRIPT, List.of(acquired.pop()), token);
            }
            while (!lockedStripes.isEmpty()) {
                lockedStripes.pop().unlock();
            }
        }
    }

    private void acquireLocal(ReentrantLock stripe, long deadline) {
        try {
            if (!stripe.tryLock(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
                throw lockTimeout();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 대기 중 인터럽트되었습니다.", e);
        }
    }

    private void acquire(String key, String token, long deadline) {
        while (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, leaseTime))) {
            if (System.nanoTime() > deadline) {
                throw lockTimeout();
            }
            sleepBriefly();
        }
    }

    private BusinessException lockTimeout() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
        );
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 대기 중 인터럽트되었습니다.", e);
        }
    }
}
