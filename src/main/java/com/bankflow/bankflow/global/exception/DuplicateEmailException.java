package com.bankflow.bankflow.global.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
