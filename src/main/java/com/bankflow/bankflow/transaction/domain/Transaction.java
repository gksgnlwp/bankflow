package com.bankflow.bankflow.transaction.domain;

import com.bankflow.bankflow.account.domain.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long balanceAfter;

    // 이체 거래일 때 상대 계좌번호
    private String counterpartyAccountNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Transaction(
            Account account,
            TransactionType type,
            Long amount,
            String counterpartyAccountNumber
    ) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.counterpartyAccountNumber = counterpartyAccountNumber;
        this.createdAt = LocalDateTime.now();
    }
}
