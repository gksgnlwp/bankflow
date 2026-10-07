package com.bankflow.bankflow.transaction.dto;

import com.bankflow.bankflow.transaction.domain.Transaction;
import com.bankflow.bankflow.transaction.domain.TransactionType;

import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        TransactionType type,
        Long amount,
        Long balanceAfter,
        String counterpartyAccountNumber,
        LocalDateTime createdAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCounterpartyAccountNumber(),
                transaction.getCreatedAt()
        );
    }
}
