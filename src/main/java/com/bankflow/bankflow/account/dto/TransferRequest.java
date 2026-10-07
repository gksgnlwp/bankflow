package com.bankflow.bankflow.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(

        @NotBlank
        String fromAccountNumber,

        @NotBlank
        String toAccountNumber,

        @NotNull
        @Positive
        Long amount

) {
}
