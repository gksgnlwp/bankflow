package com.bankflow.bankflow.account.service;

import com.bankflow.bankflow.account.dto.AccountCreateRequest;
import com.bankflow.bankflow.account.dto.TransferRequest;
import com.bankflow.bankflow.account.repository.AccountRepository;
import com.bankflow.bankflow.transaction.repository.TransactionRepository;
import com.bankflow.bankflow.user.dto.UserCreateRequest;
import com.bankflow.bankflow.user.repository.UserRepository;
import com.bankflow.bankflow.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 여러 스레드가 서로의 커밋 결과를 봐야 하므로 @Transactional 없이 실행
@SpringBootTest
class AccountConcurrencyTest {

    private static final int THREADS = 32;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userService.createUser(
                new UserCreateRequest(UUID.randomUUID() + "@test.com", "동시성")
        ).id();
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 동시에_100번_출금해도_잔액이_정확하다() throws InterruptedException {
        String number = openAccount();
        accountService.deposit(number, 100_000L);

        runConcurrently(100, i -> accountService.withdraw(number, 1_000L));

        assertThat(accountService.getAccount(number).balance()).isZero();
        assertThat(accountService.getTransactions(number)).hasSize(101);
    }

    @Test
    void 잔액보다_많은_동시_출금은_잔액만큼만_성공한다() throws InterruptedException {
        String number = openAccount();
        accountService.deposit(number, 10_000L);

        int succeeded = runConcurrently(50, i -> accountService.withdraw(number, 1_000L));

        assertThat(succeeded).isEqualTo(10);
        assertThat(accountService.getAccount(number).balance()).isZero();
    }

    @Test
    void 두_계좌가_서로_동시에_이체해도_교착되지_않고_총액이_보존된다() throws InterruptedException {
        String a = openAccount();
        String b = openAccount();
        accountService.deposit(a, 100_000L);
        accountService.deposit(b, 100_000L);

        // 짝수는 A→B, 홀수는 B→A: 잠금 순서가 고정되지 않으면 교착 상태가 생기는 패턴
        int succeeded = runConcurrently(100, i -> accountService.transfer(
                i % 2 == 0
                        ? new TransferRequest(a, b, 1_000L)
                        : new TransferRequest(b, a, 1_000L)
        ));

        assertThat(succeeded).isEqualTo(100);
        assertThat(accountService.getAccount(a).balance() + accountService.getAccount(b).balance())
                .isEqualTo(200_000L);
        assertThat(accountService.getAccount(a).balance()).isEqualTo(100_000L);
    }

    private String openAccount() {
        return accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();
    }

    /** task를 count번 동시에 실행하고 성공한 횟수를 반환한다. */
    private int runConcurrently(int count, IntConsumerTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < count; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    task.run(index);
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {
                    // 잔액 부족 등 실패는 성공 횟수로 검증
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        return succeeded.get();
    }

    @FunctionalInterface
    private interface IntConsumerTask {
        void run(int index);
    }
}
