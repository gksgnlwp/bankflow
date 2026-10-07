package com.bankflow.bankflow.user.controller;

import com.bankflow.bankflow.account.dto.AccountResponse;
import com.bankflow.bankflow.account.service.AccountService;
import com.bankflow.bankflow.user.dto.UserCreateRequest;
import com.bankflow.bankflow.user.dto.UserResponse;
import com.bankflow.bankflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
            @Valid @RequestBody UserCreateRequest request
    ) {
        return userService.createUser(request);
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable Long userId) {
        return userService.getUser(userId);
    }

    @GetMapping("/{userId}/accounts")
    public List<AccountResponse> getUserAccounts(@PathVariable Long userId) {
        return accountService.getAccountsByUser(userId);
    }
}
