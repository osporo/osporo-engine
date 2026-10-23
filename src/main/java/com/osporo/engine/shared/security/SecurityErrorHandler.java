package com.osporo.engine.shared.security;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.response.ErrorEnvelope;
import com.osporo.engine.shared.exception.OsporoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        writeError(
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.INVALID_TOKEN,
                "Authentication is required. Supply a valid access token."
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        writeError(
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.INSUFFICIENT_PERMISSIONS,
                "You are not authorized to perform this action."
        );
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(response.getOutputStream(), ErrorEnvelope.of(code, message));
    }

    public void handleAuthenticationFailure(HttpServletResponse response, OsporoException exception)
            throws IOException {
        if (exception.getHttpStatus() == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        writeError(response, exception.getHttpStatus(), exception.getErrorCode(), exception.getMessage());
    }
}
