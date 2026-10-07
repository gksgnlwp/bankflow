package com.bankflow.bankflow.user.repository;

import com.bankflow.bankflow.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
}
