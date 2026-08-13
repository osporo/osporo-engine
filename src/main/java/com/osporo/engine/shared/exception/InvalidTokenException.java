package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends OsporoException {
    public InvalidTokenException() {
        super(ErrorCode.INVALID_TOKEN, HttpStatus.UNAUTHORIZED,
            "Token is invalid or has expired.");
    }
}
