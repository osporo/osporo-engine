package com.osporo.engine.shared.exception;

import org.springframework.http.HttpStatus;
import com.osporo.engine.shared.enums.ErrorCode;

public class AuthenticationFailedException extends OsporoException {
    public AuthenticationFailedException() {
        super(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED,
            "Email or password is invalid.");
    }
}
