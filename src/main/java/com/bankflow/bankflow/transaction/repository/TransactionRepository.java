package com.bankflow.bankflow.transaction.repository;

import com.bankflow.bankflow.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByIdDesc(Long accountId);
}
