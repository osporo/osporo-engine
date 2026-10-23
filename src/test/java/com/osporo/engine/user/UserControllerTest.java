package com.osporo.engine.user;

import com.osporo.engine.auth.JwtService;
import com.osporo.engine.shared.config.OsporoSecurityConfig;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.security.JwtAuthenticationFilter;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.shared.security.SecurityErrorHandler;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.user.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.osporo.engine.user.dto.UpdateUserProfileRequest;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
        OsporoSecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        SecurityErrorHandler.class,
        TenantContextHolder.class,
        UserMapper.class
})
class UserControllerTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac";

    @DynamicPropertySource
    static void configureJwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        registry.add("jwt.access-token-expiry", () -> "900");
        registry.add("jwt.refresh-token-expiry", () -> "2592000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @Test
    void getOwnProfile_shouldUsePrincipalAndReturnOnlyProfileFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<String> permissions = List.of("ORDER_CREATE");
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("profile@example.com");
        user.setRoles(List.of(RoleType.BUYER));
        user.setPasswordHash("private-password-hash");
        user.setStripeConnectId("private-provider-id");

        when(userService.getOwnProfile(any(OsporoPrincipal.class))).thenAnswer(invocation -> {
            OsporoPrincipal principal = invocation.getArgument(0);
            assertThat(principal.getUserId()).isEqualTo(userId);
            assertThat(principal.getTenantId()).isEqualTo(tenantId);
            return user;
        });

        String token = jwtService.generateAccessToken(userId, tenantId, List.of("BUYER"), permissions);

        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-ID", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.*", hasSize(6)))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("profile@example.com"))
                .andExpect(jsonPath("$.data.display_name").value(nullValue()))
                .andExpect(jsonPath("$.data.avatar_url").value(nullValue()))
                .andExpect(jsonPath("$.data.roles").value(contains("BUYER")))
                .andExpect(jsonPath("$.data.permissions").value(contains("ORDER_CREATE")))
                .andExpect(jsonPath("$.meta.requestId").isString());
    }

    @Test
    void getOwnProfile_shouldRequireTokenDespitePublicUserMatcher() throws Exception {
        mockMvc.perform(get("/v1/users/me").header("X-Tenant-ID", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.error.details").isEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void getOwnProfile_shouldRejectInvalidTokenWithoutCallingService() throws Exception {
        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        verifyNoInteractions(userService);
    }

    @Test
    void getOwnProfile_shouldAllowAuthenticatedUserWithoutReadPermissions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setRoles(List.of(RoleType.BUYER));

        when(userService.getOwnProfile(any(OsporoPrincipal.class))).thenReturn(user);

        String token = jwtService.generateAccessToken(userId, tenantId, List.of("BUYER"), List.of());

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void getOwnProfile_shouldRejectExpiredTokenWithoutCallingService() throws Exception {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", UUID.randomUUID().toString())
                .claim("permissions", List.of())
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateOwnProfile_shouldReturnSameProjectionUsingTokenIdentityAndPermissions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("profile@example.com");
        user.setDisplayName("Updated Name");
        user.setRoles(List.of(RoleType.BUYER));

        when(userService.updateOwnProfile(any(OsporoPrincipal.class), any(UpdateUserProfileRequest.class)))
                .thenAnswer(invocation -> {
                    OsporoPrincipal principal = invocation.getArgument(0);
                    UpdateUserProfileRequest request = invocation.getArgument(1);

                    assertThat(principal.getUserId()).isEqualTo(userId);
                    assertThat(principal.getTenantId()).isEqualTo(tenantId);
                    assertThat(request.getDisplayName()).isEqualTo("Updated Name");
                    assertThat(request.hasAvatarUrl()).isTrue();
                    assertThat(request.getAvatarUrl()).isNull();

                    return user;
                });

        String token = jwtService.generateAccessToken(
                userId, tenantId, List.of("BUYER"), List.of("ORDER_CREATE"));

        mockMvc.perform(patch("/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-ID", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"display_name\":\"  Updated Name  \",\"avatar_url\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.*", hasSize(6)))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.display_name").value("Updated Name"))
                .andExpect(jsonPath("$.data.avatar_url").value(nullValue()))
                .andExpect(jsonPath("$.data.permissions").value(contains("ORDER_CREATE")))
                .andExpect(jsonPath("$.meta.requestId").isString());
    }

    @ParameterizedTest
    @MethodSource("invalidProfileUpdates")
    void updateOwnProfile_shouldRejectInvalidFieldsBeforeCallingService(String body) throws Exception {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of("BUYER"), List.of());

        mockMvc.perform(patch("/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.requestId").doesNotExist());

        verifyNoInteractions(userService);
    }

    static Stream<String> invalidProfileUpdates() {
        return Stream.of(
                "{\"display_name\":null}",
                "{\"display_name\":\"   \"}",
                "{\"display_name\":42}",
                "{\"display_name\":[]}",
                "{\"display_name\":\"" + "a".repeat(101) + "\"}",
                "{\"avatar_url\":\"http://example.com/avatar.png\"}",
                "{\"avatar_url\":\"/avatar.png\"}",
                "{\"avatar_url\":\"https:///avatar.png\"}",
                "{\"avatar_url\":\"https://example.com/a b\"}",
                "{\"avatar_url\":false}",
                "{\"avatar_url\":{}}",
                "{\"avatar_url\":\"\"}",
                "{\"avatar_url\":\"https://example.com/" + "a".repeat(2048) + "\"}",
                "{", "null", "[]"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "password", "roles", "tenant_id", "status", "id", "displayName"})
    void updateOwnProfile_shouldRejectUnsupportedFields(String field) throws Exception {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of("BUYER"), List.of());

        mockMvc.perform(patch("/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value(field));

        verifyNoInteractions(userService);
    }

    @Test
    void updateOwnProfile_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        verifyNoInteractions(userService);
    }
}
