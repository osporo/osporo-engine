package com.osporo.engine.shared.exception;

import org.springframework.http.HttpStatus;
import com.osporo.engine.shared.enums.ErrorCode;

public class AccountSuspendedException extends OsporoException {
    public AccountSuspendedException() {
        super(ErrorCode.ACCOUNT_SUSPENDED, HttpStatus.FORBIDDEN,
            "Your account has been suspended.");
    }
}