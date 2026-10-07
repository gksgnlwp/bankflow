package com.bankflow.bankflow.account.service;

import com.bankflow.bankflow.account.dto.AccountCreateRequest;
import com.bankflow.bankflow.account.dto.AccountResponse;
import com.bankflow.bankflow.account.repository.AccountRepository;
import com.bankflow.bankflow.global.exception.BusinessException;
import com.bankflow.bankflow.global.exception.InvalidRequestException;
import com.bankflow.bankflow.global.lock.AccountLock;
import com.bankflow.bankflow.global.lock.RedisAccountLock;
import com.bankflow.bankflow.transaction.repository.TransactionRepository;
import com.bankflow.bankflow.user.dto.UserCreateRequest;
import com.bankflow.bankflow.user.repository.UserRepository;
import com.bankflow.bankflow.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "bankflow.lock.strategy=redis",
        "bankflow.lock.wait-time=500ms",
        "bankflow.idempotency.enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
class RedisAccountCommandTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    private AccountCommandFacade facade;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountLock accountLock;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    private String accountNumber;

    @BeforeEach
    void setUp() {
        Long userId = userService.createUser(
                new UserCreateRequest(UUID.randomUUID() + "@test.com", "레디스")
        ).id();
        accountNumber = accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void redis_전략이면_Redis_잠금이_사용된다() {
        assertThat(accountLock).isInstanceOf(RedisAccountLock.class);
    }

    @Test
    void Redis_잠금으로_동시에_100번_출금해도_잔액이_정확하다() throws InterruptedException {
        facade.deposit(accountNumber, 100_000L, null);

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch done = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    facade.withdraw(accountNumber, 1_000L, null);
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(accountService.getAccount(accountNumber).balance()).isZero();
    }

    @Test
    void 잠금을_기다리다_시간이_지나면_실패한다() {
        accountLock.executeWithLock(List.of(accountNumber), () -> {
            assertThatThrownBy(() -> facade.deposit(accountNumber, 1_000L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("잠시 후 다시 시도");
            return null;
        });
    }

    @Test
    void 같은_멱등키로_두_번_입금하면_한_번만_처리되고_같은_응답을_돌려준다() {
        String key = UUID.randomUUID().toString();

        AccountResponse first = facade.deposit(accountNumber, 5_000L, key);
        AccountResponse second = facade.deposit(accountNumber, 5_000L, key);

        assertThat(second).isEqualTo(first);
        assertThat(accountService.getAccount(accountNumber).balance()).isEqualTo(5_000L);
        assertThat(accountService.getTransactions(accountNumber)).hasSize(1);
    }

    @Test
    void 다른_멱등키면_각각_처리된다() {
        facade.deposit(accountNumber, 5_000L, UUID.randomUUID().toString());
        facade.deposit(accountNumber, 5_000L, UUID.randomUUID().toString());

        assertThat(accountService.getAccount(accountNumber).balance()).isEqualTo(10_000L);
    }

    @Test
    void 실패한_요청은_같은_키로_다시_시도할_수_있다() {
        String key = UUID.randomUUID().toString();

        assertThatThrownBy(() -> facade.withdraw(accountNumber, 1_000L, key))
                .isInstanceOf(InvalidRequestException.class);

        facade.deposit(accountNumber, 3_000L, null);
        AccountResponse retried = facade.withdraw(accountNumber, 1_000L, key);

        assertThat(retried.balance()).isEqualTo(2_000L);
    }
}
