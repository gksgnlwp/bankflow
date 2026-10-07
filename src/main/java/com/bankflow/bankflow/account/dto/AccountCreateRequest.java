package com.bankflow.bankflow.account.dto;

import jakarta.validation.constraints.NotNull;

public record AccountCreateRequest(
        @NotNull
        Long userId
) {
}