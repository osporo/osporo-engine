package com.osporo.engine.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityErrorHandler handler = new SecurityErrorHandler(objectMapper);

    @Test
    void commence_shouldReturnJsonAuthenticationError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(
                new MockHttpServletRequest(),
                response,
                new AuthenticationCredentialsNotFoundException("Missing credentials")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertErrorBody(response, "INVALID_TOKEN");
    }

    @Test
    void handle_shouldReturnJsonAccessDeniedError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("Missing permission")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertErrorBody(response, "INSUFFICIENT_PERMISSIONS");
    }

    private void assertErrorBody(MockHttpServletResponse response, String code) throws Exception {
        var body = objectMapper.readTree(response.getContentAsByteArray());
        var error = body.get("error");

        assertThat(body.size()).isEqualTo(1);
        assertThat(error.size()).isEqualTo(3);
        assertThat(error.get("code").asString()).isEqualTo(code);
        assertThat(error.get("message").asString()).isNotBlank();
        assertThat(error.get("details").isArray()).isTrue();
        assertThat(error.get("details").size()).isZero();
    }
}
