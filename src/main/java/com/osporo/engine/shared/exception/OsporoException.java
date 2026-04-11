package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OsporoException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public OsporoException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }
}