package com.osporo.engine.shared.security;

import com.osporo.engine.auth.AccountStatusService;
import com.osporo.engine.auth.JwtService;
import com.osporo.engine.shared.exception.OsporoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AccountStatusService accountStatusService;

    private final TenantContextHolder tenantContextHolder = new TenantContextHolder();
    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, tenantContextHolder,
                accountStatusService, new SecurityErrorHandler(new ObjectMapper()));
    }

    @AfterEach
    void clearContexts() {
        tenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_shouldCheckMembershipAndUseSignedTenantThenClearContexts() throws Exception {
        when(jwtService.validateAndExtract("token")).thenReturn(claims(validClaims()));
        MockHttpServletRequest request = bearerRequest();
        request.addHeader("X-Tenant-ID", UUID.randomUUID());

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> {
            verify(accountStatusService).requireActiveAccount(userId, tenantId);
            assertThat(tenantContextHolder.getTenantId()).isEqualTo(tenantId);
            OsporoPrincipal principal = (OsporoPrincipal) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            assertThat(principal.getUserId()).isEqualTo(userId);
            assertThat(principal.getPermissions()).containsExactly("USER_READ_PUBLIC");
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThatThrownBy(tenantContextHolder::getTenantId).isInstanceOf(OsporoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing_subject", "missing_tenant", "missing_permissions", "bad_subject",
            "bad_tenant", "non_list_permissions", "non_string_permission", "blank_permission"})
    void malformedClaims_shouldReturnJson401WithoutRunningPublicEndpoint(String invalidClaim) throws Exception {
        Map<String, Object> values = validClaims();

        switch (invalidClaim) {
            case "missing_subject" -> values.remove("sub");
            case "missing_tenant" -> values.remove("tenant_id");
            case "missing_permissions" -> values.remove("permissions");
            case "bad_subject" -> values.put("sub", "bad-uuid");
            case "bad_tenant" -> values.put("tenant_id", "bad-uuid");
            case "non_list_permissions" -> values.put("permissions", "USER_READ_PUBLIC");
            case "non_string_permission" -> values.put("permissions", List.of(42));
            case "blank_permission" -> values.put("permissions", List.of(""));
            default -> throw new IllegalArgumentException(invalidClaim);
        }

        when(jwtService.validateAndExtract("token")).thenReturn(claims(values));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(bearerRequest(), response, (incoming, outgoing) -> {
            throw new AssertionError("Rejected tokens must not reach the endpoint");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN").doesNotContain("requestId");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void guest_shouldResolveHeaderWithoutLoadingAnAccountAndClearContextOnFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/" + userId);
        request.addHeader("X-Tenant-ID", tenantId);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> {
            assertThat(tenantContextHolder.getTenantId()).isEqualTo(tenantId);
            throw new IllegalStateException("Downstream failure");
        })).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(jwtService, accountStatusService);
        assertThatThrownBy(tenantContextHolder::getTenantId).isInstanceOf(OsporoException.class);
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    private Map<String, Object> validClaims() {
        Map<String, Object> values = new HashMap<>();
        values.put("sub", userId.toString());
        values.put("tenant_id", tenantId.toString());
        values.put("permissions", List.of("USER_READ_PUBLIC"));
        return values;
    }

    private Claims claims(Map<String, Object> values) {
        return Jwts.claims().add(values).build();
    }
}
