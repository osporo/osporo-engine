package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.response.ErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

public class RequestValidationException extends OsporoException {

    private final List<ErrorDetail> details;

    public RequestValidationException(List<ErrorDetail> details) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "The request contains invalid fields.");
        this.details = List.copyOf(details);
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
