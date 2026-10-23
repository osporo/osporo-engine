package com.osporo.engine.auth;

import com.osporo.engine.auth.dto.LoginRequest;
import com.osporo.engine.auth.dto.RefreshRequest;
import com.osporo.engine.auth.model.RefreshToken;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.UserRepository;
import com.osporo.engine.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AccountStatusIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWM=");
        registry.add("jwt.access-token-expiry", () -> "900");
        registry.add("jwt.refresh-token-expiry", () -> "604800");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private Tenant tenant;
    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        tenant = createTenant();
        user = createUser(tenant.getId());
        accessToken = accessToken(user.getId(), tenant.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "DELETED", "TENANT_SUSPENDED"})
    void accountState_shouldBlockAccessLoginAndRefreshWithoutRotatingTokens(String state) throws Exception {
        RefreshToken refreshToken = createRefreshToken(user.getId(), tenant.getId());
        changeState(state);

        String code = state.equals("DELETED") ? "INVALID_TOKEN" :
                state.equals("SUSPENDED") ? "ACCOUNT_SUSPENDED" : "TENANT_SUSPENDED";
        int expectedStatus = state.equals("DELETED") ? 401 : 403;

        for (String path : List.of("/v1/users/me", "/v1/users/" + user.getId())) {
            mockMvc.perform(get(path)
                            .header("Authorization", "Bearer " + accessToken)
                            .header("X-Tenant-ID", UUID.randomUUID()))
                    .andExpect(status().is(expectedStatus))
                    .andExpect(jsonPath("$.error.code").value(code));
        }

        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Tenant-ID", tenant.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), "Password123!"))))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(
                        state.equals("DELETED") ? "AUTHENTICATION_FAILED" : code));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken.getToken()))))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(code));

        entityManager.clear();
        assertThat(refreshTokenRepository.findById(refreshToken.getId()).orElseThrow().isInvalidated()).isFalse();
    }

    @Test
    void access_shouldRejectUnknownAndCrossTenantSubjects() throws Exception {
        Tenant otherTenant = createTenant();

        for (String token : List.of(accessToken(UUID.randomUUID(), tenant.getId()),
                accessToken(user.getId(), otherTenant.getId()))) {
            mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }
    }

    @Test
    void revocation_shouldInvalidateOnlyMatchingTenantAndUserSessions() throws Exception {
        RefreshToken first = createRefreshToken(user.getId(), tenant.getId());
        RefreshToken second = createRefreshToken(user.getId(), tenant.getId());
        User otherUser = createUser(tenant.getId());
        RefreshToken otherSession = createRefreshToken(otherUser.getId(), tenant.getId());
        Tenant otherTenant = createTenant();
        RefreshToken otherTenantSession = createRefreshToken(user.getId(), otherTenant.getId());

        authService.revokeRefreshTokens(user.getId(), tenant.getId());
        authService.revokeRefreshTokens(user.getId(), tenant.getId());
        entityManager.clear();

        assertThat(refreshTokenRepository.findById(first.getId()).orElseThrow().isInvalidated()).isTrue();
        assertThat(refreshTokenRepository.findById(second.getId()).orElseThrow().isInvalidated()).isTrue();
        assertThat(refreshTokenRepository.findById(otherSession.getId()).orElseThrow().isInvalidated()).isFalse();
        assertThat(refreshTokenRepository.findById(otherTenantSession.getId()).orElseThrow().isInvalidated()).isFalse();

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(first.getToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void unsuspension_shouldAllowUnexpiredAccessTokenUntilVersioningIsImplemented() throws Exception {
        user.setSuspendedAt(OffsetDateTime.now());
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        user.setSuspendedAt(null);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void invalidBearer_shouldFailOnPublicAuthRoutesWithoutFallingBackToTenantHeader() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .header("Authorization", "Bearer invalid-token")
                        .header("X-Tenant-ID", tenant.getId())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    private void changeState(String state) {
        if (state.equals("SUSPENDED")) {
            user.setSuspendedAt(OffsetDateTime.now());
        } else if (state.equals("DELETED")) {
            user.setDeletedAt(OffsetDateTime.now());
        } else {
            tenant.setStatus(TenantStatus.SUSPENDED);
            tenantRepository.saveAndFlush(tenant);
        }

        userRepository.saveAndFlush(user);
    }

    private String accessToken(UUID userId, UUID tenantId) {
        return jwtService.generateAccessToken(userId, tenantId, List.of("BUYER"), List.of("USER_READ_PUBLIC"));
    }

    private Tenant createTenant() {
        Tenant created = new Tenant();
        created.setName("Account status test");
        created.setSlug("status-" + UUID.randomUUID());
        created.setStatus(TenantStatus.ACTIVE);
        created.setPlan("STARTER");
        created.setCreatedAt(OffsetDateTime.now());

        return tenantRepository.saveAndFlush(created);
    }

    private User createUser(UUID tenantId) {
        User created = new User();
        created.setTenantId(tenantId);
        created.setEmail(UUID.randomUUID() + "@example.com");
        created.setPasswordHash(passwordEncoder.encode("Password123!"));
        created.setRoles(List.of(RoleType.BUYER));
        created.setCreatedAt(OffsetDateTime.now());
        created.setUpdatedAt(OffsetDateTime.now());

        return userRepository.saveAndFlush(created);
    }

    private RefreshToken createRefreshToken(UUID userId, UUID tenantId) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(OffsetDateTime.now().plusDays(1));
        token.setCreatedAt(OffsetDateTime.now());

        return refreshTokenRepository.saveAndFlush(token);
    }
}
