package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends OsporoException {

    public EmailAlreadyExistsException(String email) {
        super(
                ErrorCode.EMAIL_ALREADY_EXISTS,
                HttpStatus.CONFLICT,
                "An account with email " + email + " already exists."
        );
    }
}
