package com.bankflow.bankflow.account.service;

import com.bankflow.bankflow.account.domain.Account;
import com.bankflow.bankflow.account.dto.AccountCreateRequest;
import com.bankflow.bankflow.account.dto.AccountResponse;
import com.bankflow.bankflow.account.dto.TransferRequest;
import com.bankflow.bankflow.account.repository.AccountRepository;
import com.bankflow.bankflow.global.exception.InvalidRequestException;
import com.bankflow.bankflow.global.exception.NotFoundException;
import com.bankflow.bankflow.transaction.domain.Transaction;
import com.bankflow.bankflow.transaction.domain.TransactionType;
import com.bankflow.bankflow.transaction.dto.TransactionResponse;
import com.bankflow.bankflow.transaction.repository.TransactionRepository;
import com.bankflow.bankflow.user.domain.User;
import com.bankflow.bankflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;

    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {

        User user = userService.findUser(request.userId());

        Account account = new Account(
                generateAccountNumber(),
                user
        );

        return AccountResponse.from(accountRepository.save(account));
    }

    public AccountResponse getAccount(String accountNumber) {
        return AccountResponse.from(findAccount(accountNumber));
    }

    public List<AccountResponse> getAccountsByUser(Long userId) {
        userService.findUser(userId);

        return accountRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    public List<TransactionResponse> getTransactions(String accountNumber) {
        Account account = findAccount(accountNumber);

        return transactionRepository.findByAccountIdOrderByIdDesc(account.getId()).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional
    public AccountResponse deposit(String accountNumber, Long amount) {

        Account account = findAccountForUpdate(accountNumber);

        account.deposit(amount);
        transactionRepository.save(
                new Transaction(account, TransactionType.DEPOSIT, amount, null)
        );

        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse withdraw(String accountNumber, Long amount) {

        Account account = findAccountForUpdate(accountNumber);

        account.withdraw(amount);
        transactionRepository.save(
                new Transaction(account, TransactionType.WITHDRAW, amount, null)
        );

        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse transfer(TransferRequest request) {

        String fromNumber = request.fromAccountNumber();
        String toNumber = request.toAccountNumber();

        if (fromNumber.equals(toNumber)) {
            throw new InvalidRequestException("같은 계좌로는 이체할 수 없습니다.");
        }

        // 교착 상태를 피하기 위해 항상 계좌번호 순서대로 잠금
        boolean fromFirst = fromNumber.compareTo(toNumber) < 0;
        Account first = findAccountForUpdate(fromFirst ? fromNumber : toNumber);
        Account second = findAccountForUpdate(fromFirst ? toNumber : fromNumber);
        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;

        from.withdraw(request.amount());
        to.deposit(request.amount());

        transactionRepository.save(
                new Transaction(from, TransactionType.TRANSFER_OUT, request.amount(), toNumber)
        );
        transactionRepository.save(
                new Transaction(to, TransactionType.TRANSFER_IN, request.amount(), fromNumber)
        );

        return AccountResponse.from(from);
    }

    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 계좌입니다."));
    }

    private Account findAccountForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 계좌입니다."));
    }

    // 110-123-456789 형식의 12자리 숫자 계좌번호
    private String generateAccountNumber() {
        String accountNumber;
        do {
            String digits = String.format("%012d", RANDOM.nextLong(1_000_000_000_000L));
            accountNumber = digits.substring(0, 3) + "-"
                    + digits.substring(3, 6) + "-"
                    + digits.substring(6);
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }
}
