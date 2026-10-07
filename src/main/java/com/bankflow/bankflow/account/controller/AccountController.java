package com.bankflow.bankflow.account.controller;

import com.bankflow.bankflow.account.dto.AccountCreateRequest;
import com.bankflow.bankflow.account.dto.AccountResponse;
import com.bankflow.bankflow.account.dto.DepositRequest;
import com.bankflow.bankflow.account.dto.TransferRequest;
import com.bankflow.bankflow.account.dto.WithdrawRequest;
import com.bankflow.bankflow.account.service.AccountCommandFacade;
import com.bankflow.bankflow.account.service.AccountService;
import com.bankflow.bankflow.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final AccountService accountService;
    private final AccountCommandFacade accountCommandFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @Valid @RequestBody AccountCreateRequest request
    ) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(@PathVariable String accountNumber) {
        return accountService.getAccount(accountNumber);
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> getTransactions(@PathVariable String accountNumber) {
        return accountService.getTransactions(accountNumber);
    }

    @PostMapping("/{accountNumber}/deposit")
    public AccountResponse deposit(
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey
    ) {
        return accountCommandFacade.deposit(
                accountNumber,
                request.amount(),
                idempotencyKey
        );
    }

    @PostMapping("/{accountNumber}/withdraw")
    public AccountResponse withdraw(
            @PathVariable String accountNumber,
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey
    ) {
        return accountCommandFacade.withdraw(
                accountNumber,
                request.amount(),
                idempotencyKey
        );
    }

    @PostMapping("/transfer")
    public AccountResponse transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey
    ) {
        return accountCommandFacade.transfer(request, idempotencyKey);
    }
}
