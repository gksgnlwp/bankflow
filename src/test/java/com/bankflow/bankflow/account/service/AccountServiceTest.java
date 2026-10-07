package com.bankflow.bankflow.account.service;

import com.bankflow.bankflow.account.dto.AccountCreateRequest;
import com.bankflow.bankflow.account.dto.AccountResponse;
import com.bankflow.bankflow.account.dto.TransferRequest;
import com.bankflow.bankflow.global.exception.InvalidRequestException;
import com.bankflow.bankflow.global.exception.NotFoundException;
import com.bankflow.bankflow.transaction.domain.TransactionType;
import com.bankflow.bankflow.transaction.dto.TransactionResponse;
import com.bankflow.bankflow.user.dto.UserCreateRequest;
import com.bankflow.bankflow.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserService userService;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userService.createUser(
                new UserCreateRequest(UUID.randomUUID() + "@test.com", "홍길동")
        ).id();
    }

    @Test
    void 계좌를_개설하면_잔액은_0원이다() {
        AccountResponse account = accountService.createAccount(new AccountCreateRequest(userId));

        assertThat(account.balance()).isZero();
        assertThat(account.accountNumber()).matches("\\d{3}-\\d{3}-\\d{6}");
        assertThat(accountService.getAccountsByUser(userId)).hasSize(1);
    }

    @Test
    void 입금과_출금이_잔액과_거래내역에_반영된다() {
        String number = accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();

        accountService.deposit(number, 10_000L);
        AccountResponse result = accountService.withdraw(number, 3_000L);

        assertThat(result.balance()).isEqualTo(7_000L);

        List<TransactionResponse> transactions = accountService.getTransactions(number);
        assertThat(transactions).extracting(TransactionResponse::type)
                .containsExactly(TransactionType.WITHDRAW, TransactionType.DEPOSIT);
        assertThat(transactions.get(0).balanceAfter()).isEqualTo(7_000L);
    }

    @Test
    void 잔액보다_많이_출금하면_실패한다() {
        String number = accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();
        accountService.deposit(number, 1_000L);

        assertThatThrownBy(() -> accountService.withdraw(number, 1_001L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("잔액이 부족합니다.");
    }

    @Test
    void 이체하면_양쪽_계좌에_반영된다() {
        String from = accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();
        String to = accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();
        accountService.deposit(from, 50_000L);

        accountService.transfer(new TransferRequest(from, to, 20_000L));

        assertThat(accountService.getAccount(from).balance()).isEqualTo(30_000L);
        assertThat(accountService.getAccount(to).balance()).isEqualTo(20_000L);

        TransactionResponse incoming = accountService.getTransactions(to).get(0);
        assertThat(incoming.type()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(incoming.counterpartyAccountNumber()).isEqualTo(from);
    }

    @Test
    void 같은_계좌로는_이체할_수_없다() {
        String number = accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();
        accountService.deposit(number, 1_000L);

        assertThatThrownBy(() -> accountService.transfer(new TransferRequest(number, number, 500L)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 없는_계좌로는_이체할_수_없다() {
        String number = accountService.createAccount(new AccountCreateRequest(userId)).accountNumber();
        accountService.deposit(number, 1_000L);

        assertThatThrownBy(() -> accountService.transfer(new TransferRequest(number, "000-000-000000", 500L)))
                .isInstanceOf(NotFoundException.class);
    }
}
