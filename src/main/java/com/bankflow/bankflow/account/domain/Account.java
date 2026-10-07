package com.bankflow.bankflow.account.domain;

import com.bankflow.bankflow.global.exception.InvalidRequestException;
import com.bankflow.bankflow.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private Long balance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Account(String accountNumber, User user) {
        this.accountNumber = accountNumber;
        this.balance = 0L;
        this.user = user;
        this.createdAt = LocalDateTime.now();
    }

    public void deposit(Long amount) {
        validateAmount(amount);
        this.balance += amount;
    }

    public void withdraw(Long amount) {
        validateAmount(amount);

        if (this.balance < amount) {
            throw new InvalidRequestException("잔액이 부족합니다.");
        }

        this.balance -= amount;
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new InvalidRequestException("금액은 0보다 커야 합니다.");
        }
    }
}
