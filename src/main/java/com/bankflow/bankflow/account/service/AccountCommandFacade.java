package com.bankflow.bankflow.account.service;

import com.bankflow.bankflow.account.dto.AccountResponse;
import com.bankflow.bankflow.account.dto.TransferRequest;
import com.bankflow.bankflow.global.idempotency.Idempotency;
import com.bankflow.bankflow.global.lock.AccountLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 잔액을 바꾸는 요청의 진입점. 중복 요청 확인 → 계좌 잠금 → 트랜잭션 순으로 감싼다.
 * 잠금이 트랜잭션 바깥에 있어야 하므로 이 클래스에는 @Transactional을 붙이지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AccountCommandFacade {

    private final AccountService accountService;
    private final AccountLock accountLock;
    private final Idempotency idempotency;

    public AccountResponse deposit(String accountNumber, Long amount, String idempotencyKey) {
        return idempotency.execute("deposit:" + accountNumber, idempotencyKey, AccountResponse.class, () ->
                accountLock.executeWithLock(List.of(accountNumber), () ->
                        accountService.deposit(accountNumber, amount)
                )
        );
    }

    public AccountResponse withdraw(String accountNumber, Long amount, String idempotencyKey) {
        return idempotency.execute("withdraw:" + accountNumber, idempotencyKey, AccountResponse.class, () ->
                accountLock.executeWithLock(List.of(accountNumber), () ->
                        accountService.withdraw(accountNumber, amount)
                )
        );
    }

    public AccountResponse transfer(TransferRequest request, String idempotencyKey) {
        List<String> accounts = List.of(request.fromAccountNumber(), request.toAccountNumber());

        return idempotency.execute("transfer:" + request.fromAccountNumber(), idempotencyKey, AccountResponse.class, () ->
                accountLock.executeWithLock(accounts, () ->
                        accountService.transfer(request)
                )
        );
    }
}
