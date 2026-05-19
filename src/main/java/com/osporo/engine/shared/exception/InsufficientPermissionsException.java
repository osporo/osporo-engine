package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class InsufficientPermissionsException extends OsporoException {

    public InsufficientPermissionsException() {
        super(
                ErrorCode.INSUFFICIENT_PERMISSIONS,
                HttpStatus.FORBIDDEN,
                "You are not authorized to perform this action."
        );
    }
}