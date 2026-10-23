package com.osporo.engine.user;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.model.User;
import com.osporo.engine.auth.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class UserServiceIntegrationTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("osporo_test")
            .withUsername("osporo")
            .withPassword("osporo");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        registry.add("jwt.access-token-expiry", () -> "900");
        registry.add("jwt.refresh-token-expiry", () -> "2592000");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void setUpTenants() {
        tenantId = createTenant();
        otherTenantId = createTenant();
        tenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void clearTenantContext() {
        tenantContextHolder.clear();
    }

    @Test
    void getUser_shouldLoadTenantUserWithNullDisplayName() {
        UUID userId = createUser(tenantId);
        entityManager.clear();

        User user = userService.getUser(userId);

        assertThat(user.getId()).isEqualTo(userId);
        assertThat(user.getTenantId()).isEqualTo(tenantId);
        assertThat(user.getDisplayName()).isNull();
    }

    @Test
    void getUser_shouldHideUserBelongingToAnotherTenant() {
        UUID userId = createUser(otherTenantId);
        entityManager.clear();

        assertProfileNotFound(userId);

        tenantContextHolder.setTenantId(otherTenantId);

        assertThat(userService.getUser(userId).getTenantId()).isEqualTo(otherTenantId);
    }

    @Test
    void getUser_shouldReturnSameErrorForMissingUser() {
        assertProfileNotFound(UUID.randomUUID());
    }

    @Test
    void getOwnProfile_shouldReturnOnlyProfileFieldsAndTokenPermissions() throws Exception {
        UUID userId = createUser(tenantId);
        User user = userRepository.findById(userId).orElseThrow();
        user.setDisplayName("Profile Owner");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setStripeConnectId("private-provider-id");
        userRepository.saveAndFlush(user);

        String token = jwtService.generateAccessToken(
                userId, tenantId, List.of("MARKETPLACE_OWNER"), List.of("ORDER_CREATE"));

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data.*", hasSize(6)))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.display_name").value("Profile Owner"))
                .andExpect(jsonPath("$.data.email").value("profile@example.com"))
                .andExpect(jsonPath("$.data.avatar_url").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.roles").value(contains("BUYER")))
                .andExpect(jsonPath("$.data.permissions").value(contains("ORDER_CREATE")))
                .andExpect(jsonPath("$.meta.requestId").isString())
                .andExpect(jsonPath("$.meta.pagination").value(nullValue()));
    }

    @Test
    void getOwnProfile_shouldAllowNoPermissionsAndKeepNullableFields() throws Exception {
        UUID userId = createUser(tenantId);
        String token = tokenFor(userId, tenantId);

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.*", hasSize(6)))
                .andExpect(jsonPath("$.data.display_name").value(nullValue()))
                .andExpect(jsonPath("$.data.avatar_url").value(nullValue()))
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void getOwnProfile_shouldIgnoreTenantHeaderAndUserQueryOverride() throws Exception {
        UUID userId = createUser(tenantId);
        UUID otherUserId = createUser(otherTenantId);

        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + tokenFor(userId, tenantId))
                        .header("X-Tenant-ID", otherTenantId)
                        .param("user_id", otherUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    void getOwnProfile_shouldHideUserWhenTokenTenantDoesNotMatch() throws Exception {
        UUID userId = createUser(otherTenantId);

        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + tokenFor(userId, tenantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));
    }

    @Test
    void getOwnProfile_shouldReturnProfileNotFoundWhenAccountIsMissing() throws Exception {
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + tokenFor(UUID.randomUUID(), tenantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"));
    }

    @Test
    void getOwnProfile_shouldRequireAuthenticationEvenWithTenantHeader() throws Exception {
        mockMvc.perform(get("/v1/users/me").header("X-Tenant-ID", tenantId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.error.requestId").doesNotExist())
                .andExpect(jsonPath("$.error.request_id").doesNotExist());
    }

    @Test
    void getOwnProfile_shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer invalid-token")
                        .header("X-Tenant-ID", tenantId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void getOwnProfile_shouldRejectExpiredToken() throws Exception {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("permissions", List.of())
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    private String tokenFor(UUID userId, UUID userTenantId) {
        return jwtService.generateAccessToken(userId, userTenantId, List.of("BUYER"), List.of());
    }

    private void assertProfileNotFound(UUID userId) {
        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOfSatisfying(ProfileNotFoundException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("The requested profile was not found.");
                });
    }

    private UUID createTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("User service test marketplace");
        tenant.setSlug("user-test-" + UUID.randomUUID());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlan("STARTER");
        tenant.setCreatedAt(OffsetDateTime.now());

        return tenantRepository.saveAndFlush(tenant).getId();
    }

    private UUID createUser(UUID userTenantId) {
        User user = new User();
        user.setTenantId(userTenantId);
        user.setEmail("profile@example.com");
        user.setPasswordHash("test-password-hash");
        user.setRoles(List.of(RoleType.BUYER));
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        return userRepository.saveAndFlush(user).getId();
    }
}
