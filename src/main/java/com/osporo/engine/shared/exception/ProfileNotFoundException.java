package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class ProfileNotFoundException extends OsporoException {

    public ProfileNotFoundException() {
        super(
                ErrorCode.PROFILE_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "The requested profile was not found."
        );
    }
}
