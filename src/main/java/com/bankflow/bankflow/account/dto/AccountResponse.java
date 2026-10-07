package com.bankflow.bankflow.account.dto;

import com.bankflow.bankflow.account.domain.Account;

import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String accountNumber,
        Long balance,
        Long userId,
        String ownerName,
        LocalDateTime createdAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getUser().getId(),
                account.getUser().getName(),
                account.getCreatedAt()
        );
    }
}
