package com.bankflow.bankflow.account.repository;

import com.bankflow.bankflow.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<Account> findByAccountNumber(String accountNumber);

    // 잔액 변경 시 동시 요청으로 인한 갱신 손실을 막기 위해 행 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a join fetch a.user where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(String accountNumber);

    @EntityGraph(attributePaths = "user")
    List<Account> findByUserIdOrderByIdAsc(Long userId);

    boolean existsByAccountNumber(String accountNumber);
}
