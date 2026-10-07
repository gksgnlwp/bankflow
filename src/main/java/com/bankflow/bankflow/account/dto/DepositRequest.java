package com.bankflow.bankflow.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositRequest(

        @NotNull
        @Positive
        Long amount

) {
}