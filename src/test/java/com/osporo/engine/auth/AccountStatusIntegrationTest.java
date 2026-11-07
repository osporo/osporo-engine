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
import org.springframework.jdbc.core.simple.JdbcClient;
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
import static org.hamcrest.Matchers.nullValue;
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

    @Autowired
    private JdbcClient jdbcClient;

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
    void refresh_shouldRejectRevokedTokenEvenWhenTheLoadedEntityIsStale() throws Exception {
        RefreshToken token = createRefreshToken(user.getId(), tenant.getId());
        authService.revokeRefreshTokens(user.getId(), tenant.getId());
        assertThat(token.isInvalidated()).isFalse();

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(token.getToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void suspension_shouldRecordActionAndRevokeOnlyTargetSessions() throws Exception {
        User actor = createSuspensionActor();
        RefreshToken targetSession = createRefreshToken(user.getId(), tenant.getId());
        RefreshToken actorSession = createRefreshToken(actor.getId(), tenant.getId());
        String actorToken = suspensionToken(actor);

        String response = mockMvc.perform(post("/v1/users/{id}/suspend", user.getId())
                        .header("Authorization", "Bearer " + actorToken)
                        .header("X-Tenant-ID", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"violation_type":" PAYMENT_FRAUD ","details":"Moved payment off-platform."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.actor_id").value(actor.getId().toString()))
                .andExpect(jsonPath("$.data.action").value("SUSPEND"))
                .andExpect(jsonPath("$.data.violation_type").value("PAYMENT_FRAUD"))
                .andExpect(jsonPath("$.data.details").value("Moved payment off-platform."))
                .andReturn().getResponse().getContentAsString();

        var data = objectMapper.readTree(response).get("data");
        UUID actionId = UUID.fromString(data.get("id").asString());
        OffsetDateTime suspendedAt = OffsetDateTime.parse(data.get("suspended_at").asString());
        entityManager.clear();

        User suspended = userRepository.findByIdAndTenantId(user.getId(), tenant.getId()).orElseThrow();
        assertThat(suspended.getSuspendedAt()).isEqualTo(suspendedAt);
        assertThat(suspended.getUpdatedAt()).isEqualTo(suspendedAt);
        assertThat(refreshTokenRepository.findById(targetSession.getId()).orElseThrow().isInvalidated()).isTrue();
        assertThat(refreshTokenRepository.findById(actorSession.getId()).orElseThrow().isInvalidated()).isFalse();

        long matchingRecords = jdbcClient.sql("""
                        SELECT COUNT(*) FROM suspensions
                        WHERE id = :id AND tenant_id = :tenantId AND user_id = :userId AND actor_id = :actorId
                          AND action = 'SUSPEND' AND violation_type = 'PAYMENT_FRAUD'
                          AND details = 'Moved payment off-platform.' AND action_date = :suspendedAt
                        """)
                .param("id", actionId)
                .param("tenantId", tenant.getId())
                .param("userId", user.getId())
                .param("actorId", actor.getId())
                .param("suspendedAt", suspendedAt)
                .query(Long.class).single();
        assertThat(matchingRecords).isEqualTo(1);

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(targetSession.getToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        expectSuspensionError("suspend", user.getId(), actorToken, 409, "USER_ALREADY_SUSPENDED");
        assertThat(suspensionCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "unsuspend"})
    void suspensionActions_shouldUseCurrentRanksAndHideUnavailableTargets(String action) throws Exception {
        User actor = createSuspensionActor();
        String actorToken = suspensionToken(actor);
        User higherRank = createUser(tenant.getId());
        higherRank.setRoles(List.of(RoleType.BUYER, RoleType.MARKETPLACE_OWNER));
        User peer = createUser(tenant.getId());
        peer.setRoles(List.of(RoleType.BUYER, RoleType.MARKETPLACE_STAFF));
        User platformAdmin = createUser(tenant.getId());
        platformAdmin.setRoles(List.of(RoleType.BUYER, RoleType.OSPORO_ADMIN));
        User deleted = createUser(tenant.getId());
        deleted.setDeletedAt(OffsetDateTime.now());
        User otherTenant = createUser(createTenant().getId());
        userRepository.flush();
        entityManager.clear();

        for (User target : List.of(actor, peer, higherRank)) {
            expectSuspensionError(action, target.getId(), actorToken, 422, "ROLE_HIERARCHY_VIOLATION");
        }
        for (UUID id : List.of(platformAdmin.getId(), deleted.getId(), otherTenant.getId(), UUID.randomUUID())) {
            expectSuspensionError(action, id, actorToken, 404, "PROFILE_NOT_FOUND");
        }

        assertThat(suspensionCount()).isZero();
        assertThat(userRepository.findById(peer.getId()).orElseThrow().isSuspended()).isFalse();
        assertThat(userRepository.findById(otherTenant.getId()).orElseThrow().isSuspended()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"violation_type\":123}", "{\"violation_type\":\" \"}",
            "{\"violation_type\":\"PAYMENT_FRAUD\",\"details\":{}}"})
    void suspension_shouldRejectInvalidPayloads(String body) throws Exception {
        String actorToken = suspensionToken(createSuspensionActor());

        mockMvc.perform(post("/v1/users/{id}/suspend", user.getId())
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(suspensionCount()).isZero();
    }

    private User createSuspensionActor() {
        User actor = createUser(tenant.getId());
        actor.setRoles(List.of(RoleType.SELLER, RoleType.MARKETPLACE_STAFF));

        return userRepository.saveAndFlush(actor);
    }

    private String suspensionToken(User actor) {
        return jwtService.generateAccessToken(
                actor.getId(), actor.getTenantId(), List.of("MARKETPLACE_OWNER"), List.of("USER_SUSPEND")
        );
    }

    private void expectSuspensionError(String action, UUID targetId, String token, int expectedStatus, String errorCode)
            throws Exception {
        var request = post("/v1/users/{id}/{action}", targetId, action).header("Authorization", "Bearer " + token);
        if (action.equals("suspend")) {
            request.contentType("application/json").content("{\"violation_type\":\"PAYMENT_FRAUD\"}");
        }

        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(errorCode));
    }

    private long suspensionCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM suspensions WHERE tenant_id = :tenantId")
                .param("tenantId", tenant.getId())
                .query(Long.class).single();
    }

    @Test
    void unsuspension_shouldLinkCurrentSuspensionAndKeepItsRefreshTokenRevoked() throws Exception {
        User suspendingActor = createSuspensionActor();
        User liftingActor = createSuspensionActor();
        String liftingToken = suspensionToken(liftingActor);
        RefreshToken targetSession = createRefreshToken(user.getId(), tenant.getId());

        String suspensionResponse = mockMvc.perform(post("/v1/users/{id}/suspend", user.getId())
                        .header("Authorization", "Bearer " + suspensionToken(suspendingActor))
                        .contentType("application/json")
                        .content("{\"violation_type\":\"PAYMENT_FRAUD\",\"details\":\"Payment moved off-platform.\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var suspension = objectMapper.readTree(suspensionResponse).get("data");

        String response = mockMvc.perform(post("/v1/users/{id}/unsuspend", user.getId())
                        .header("Authorization", "Bearer " + liftingToken)
                        .header("X-Tenant-ID", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"details\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("UPLIFT"))
                .andExpect(jsonPath("$.data.user_id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.actor_id").value(liftingActor.getId().toString()))
                .andExpect(jsonPath("$.data.details").value("123"))
                .andExpect(jsonPath("$.data.suspension.id").value(suspension.get("id").asString()))
                .andExpect(jsonPath("$.data.suspension.actor_id").value(suspendingActor.getId().toString()))
                .andExpect(jsonPath("$.data.suspension.violation_type").value("PAYMENT_FRAUD"))
                .andExpect(jsonPath("$.data.suspension.details").value("Payment moved off-platform."))
                .andExpect(jsonPath("$.data.suspension.action_date").value(suspension.get("suspended_at").asString()))
                .andReturn().getResponse().getContentAsString();

        var lift = objectMapper.readTree(response).get("data");
        entityManager.clear();
        User restored = userRepository.findById(user.getId()).orElseThrow();
        assertThat(restored.getSuspendedAt()).isNull();
        assertThat(restored.getUpdatedAt()).isEqualTo(OffsetDateTime.parse(lift.get("action_date").asString()));

        UUID suspensionId = jdbcClient.sql("""
                        SELECT suspension_id FROM suspensions
                        WHERE id = :id AND tenant_id = :tenantId AND user_id = :userId
                          AND actor_id = :actorId AND action = 'LIFT' AND violation_type IS NULL
                        """)
                .param("id", UUID.fromString(lift.get("id").asString()))
                .param("tenantId", tenant.getId())
                .param("userId", user.getId())
                .param("actorId", liftingActor.getId())
                .query(UUID.class).single();
        assertThat(suspensionId).isEqualTo(UUID.fromString(suspension.get("id").asString()));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshRequest(targetSession.getToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        expectSuspensionError("unsuspend", user.getId(), liftingToken, 409, "USER_NOT_SUSPENDED");
        assertThat(suspensionCount()).isEqualTo(2);
    }

    @Test
    void unsuspension_shouldLiftLegacySuspensionWithoutInventingAnOriginalAuditRecord() throws Exception {
        String actorToken = suspensionToken(createSuspensionActor());
        RefreshToken legacySession = createRefreshToken(user.getId(), tenant.getId());
        user.setSuspendedAt(OffsetDateTime.now());
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/users/{id}/unsuspend", user.getId())
                        .header("Authorization", "Bearer " + actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("UPLIFT"))
                .andExpect(jsonPath("$.data.details").value(""))
                .andExpect(jsonPath("$.data.suspension").value(nullValue()));

        entityManager.clear();
        assertThat(refreshTokenRepository.findById(legacySession.getId()).orElseThrow().isInvalidated()).isTrue();
        assertThat(suspensionCount()).isEqualTo(1);

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void unsuspension_shouldRejectDetailsContainingNul() throws Exception {
        String actorToken = suspensionToken(createSuspensionActor());
        user.setSuspendedAt(OffsetDateTime.now());
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/v1/users/{id}/unsuspend", user.getId())
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType("application/json")
                        .content("{\"details\":\"invalid\\u0000text\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(suspensionCount()).isZero();
        assertThat(user.isSuspended()).isTrue();
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
