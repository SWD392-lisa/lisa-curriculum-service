package com.lisa.curriculum.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ImportValidationException extends RuntimeException {
    public ImportValidationException(String message) {
        super(message);
    }
}
