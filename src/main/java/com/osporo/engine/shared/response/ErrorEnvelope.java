package com.osporo.engine.shared.response;

import com.osporo.engine.shared.enums.ErrorCode;
import lombok.Getter;

import java.util.List;

@Getter
public class ErrorEnvelope {

    private final ErrorBody error;

    private ErrorEnvelope(ErrorBody error) {
        this.error = error;
    }

    public static ErrorEnvelope of(ErrorCode code, String message) {
        return new ErrorEnvelope(new ErrorBody(code, message, List.of()));
    }

    public static ErrorEnvelope of(ErrorCode code, String message, List<ErrorDetail> details) {
        return new ErrorEnvelope(new ErrorBody(code, message, details));
    }

    @Getter
    public static class ErrorBody {
        private final String code;
        private final String message;
        private final List<ErrorDetail> details;

        public ErrorBody(ErrorCode code, String message, List<ErrorDetail> details) {
            this.code    = code.name();
            this.message = message;
            this.details = details;
        }
    }
}

