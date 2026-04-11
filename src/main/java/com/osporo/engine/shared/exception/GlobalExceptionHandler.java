package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.response.ErrorDetail;
import com.osporo.engine.shared.response.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handles all OsporoException subclasses
    @ExceptionHandler(OsporoException.class)
    public ResponseEntity<ErrorEnvelope> handleOsporoException(
            OsporoException ex,
            HttpServletRequest request
    ) {
        log.warn("OsporoException [{}] on {} {}: {}",
                ex.getErrorCode(),
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ErrorEnvelope.of(ex.getErrorCode(), ex.getMessage()));
    }

    // Handles @Valid validation failures on request bodies
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ErrorDetail> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new ErrorDetail(err.getField(), err.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("Validation failed on {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                details
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.", details));
    }

    // Handles @PreAuthorize failures
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.warn("Access denied on {} {}",
                request.getMethod(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorEnvelope.of(
                        ErrorCode.INSUFFICIENT_PERMISSIONS,
                        "You are not authorized to perform this action."
                ));
    }

    // Catches anything not handled above — prevents stack traces leaking to clients
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected exception on {} {}: ",
                request.getMethod(),
                request.getRequestURI(),
                ex
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. {}"
                ));
    }
}