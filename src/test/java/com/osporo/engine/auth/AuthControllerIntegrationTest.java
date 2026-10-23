package com.osporo.engine.auth;

import java.util.UUID;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;
import com.osporo.engine.auth.dto.LogoutRequest;
import com.osporo.engine.shared.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.osporo.engine.auth.dto.LoginRequest;
import com.osporo.engine.auth.dto.RefreshRequest;
import com.osporo.engine.auth.dto.RegisterRequest;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.TenantRoleConfigRepository;
import com.osporo.engine.shared.enums.Permission;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.user.UserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

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
                "test-secret-key-that-is-long-enough-for-hmac".getBytes(StandardCharsets.UTF_8)));
        registry.add("jwt.access-token-expiry", () -> "900");
        registry.add("jwt.refresh-token-expiry", () -> "2592000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantRoleConfigRepository tenantRoleConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // Use the seeded tenant from Flyway migrations
        tenantId = tenantRepository.findAll().get(0).getId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", ""})
    void register_shouldReturnValidationErrorForUnreadableOrMissingBody(String body) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isEmpty());
    }

    @Test
    void seededProfilePermissions_shouldKeepParticipantAndAdministrativeAccessSeparate() {
        for (RoleType role : List.of(RoleType.BUYER, RoleType.SELLER)) {
            var config = tenantRoleConfigRepository.findByTenantIdAndRoleName(tenantId, role).orElseThrow();
            assertThat(config.getPermissions())
                .contains(Permission.USER_READ_PUBLIC)
                .doesNotContain(Permission.USER_READ_ANY, Permission.PLATFORM_ADMIN);
        }
        for (RoleType role : List.of(RoleType.MARKETPLACE_STAFF, RoleType.MARKETPLACE_OWNER)) {
            var config = tenantRoleConfigRepository.findByTenantIdAndRoleName(tenantId, role).orElseThrow();
            assertThat(config.getPermissions())
                .contains(Permission.USER_READ_ANY)
                .doesNotContain(Permission.USER_READ_PUBLIC, Permission.PLATFORM_ADMIN);
        }
        var admin = tenantRoleConfigRepository
            .findByTenantIdAndRoleName(tenantId, RoleType.OSPORO_ADMIN).orElseThrow();
        assertThat(admin.getPermissions()).containsExactly(Permission.PLATFORM_ADMIN);
    }

    @Test
    void loginAndRefresh_shouldIssuePublicProfilePermission_forSeededParticipantRoles() throws Exception {
        for (RoleType role : List.of(RoleType.BUYER, RoleType.SELLER)) {
            String email = "profile-permission-" + role.name().toLowerCase(java.util.Locale.ROOT) + "@example.com";
            mockMvc.perform(post("/v1/auth/register")
                    .header("X-Tenant-ID", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andExpect(status().isCreated());

            var user = userRepository.findByEmailAndTenantId(email, tenantId).orElseThrow();
            user.setRoles(List.of(role));
            userRepository.saveAndFlush(user);

            String loginBody = mockMvc.perform(post("/v1/auth/login")
                    .header("X-Tenant-ID", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var login = objectMapper.readTree(loginBody).path("data");
            assertPublicProfileClaims(login.path("access_token").asText(), user.getId());

            String refreshBody = mockMvc.perform(post("/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new RefreshRequest(login.path("refresh_token").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertPublicProfileClaims(
                objectMapper.readTree(refreshBody).path("data").path("access_token").asText(), user.getId()
            );
        }
    }

    private void assertPublicProfileClaims(String accessToken, UUID userId) {
        var claims = jwtService.validateAndExtract(accessToken);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get("permissions", List.class))
            .contains("USER_READ_PUBLIC")
            .doesNotContain("USER_READ_ANY", "PLATFORM_ADMIN");
    }

    @Test
    void register_shouldReturn201_whenRequestIsValid() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser@gmail.com", "password123");

        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("newuser@gmail.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("BUYER"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void register_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicate@gmail.com", "password123");

        // Register once
        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Register again with same email
        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void register_shouldReturn400_whenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "password123");

        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("email"));
    }

    @Test
    void register_shouldReturn400_whenTenantIdHeaderIsMissing() throws Exception {
        RegisterRequest request = new RegisterRequest("user@gmail.com", "password123");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_shouldReturn200WithTokens_whenCredentialsAreValid() throws Exception {
        // Register first
        RegisterRequest registerRequest = new RegisterRequest("logintest@gmail.com", "password123");
        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Login
        LoginRequest loginRequest = new LoginRequest("logintest@gmail.com", "password123");
        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").exists())
                .andExpect(jsonPath("$.data.refresh_token").exists())
                .andExpect(jsonPath("$.data.expires_in").value(900));
    }

    @Test
    void login_shouldReturn401_whenPasswordIsWrong() throws Exception {
        // Register first
        RegisterRequest registerRequest = new RegisterRequest("wrongpass@gmail.com", "password123");
        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Login with wrong password
        LoginRequest loginRequest = new LoginRequest("wrongpass@gmail.com", "wrongpassword");
        mockMvc.perform(post("/v1/auth/login")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void refresh_shouldReturn200WithNewTokens_whenRefreshTokenIsValid() throws Exception {
        // Register and login to get tokens
        RegisterRequest registerRequest = new RegisterRequest("refresh@gmail.com", "password123");
        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("refresh@gmail.com", "password123");
        String loginResponse = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponse)
                .path("data")
                .path("refresh_token")
                .asText();

        // Use the refresh token
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").exists())
                .andExpect(jsonPath("$.data.refresh_token").exists())
                // New refresh token must differ from the old one — rotation
                .andExpect(jsonPath("$.data.refresh_token").value(not(refreshToken)));
    }

    @Test
    void logout_shouldReturn204_andInvalidateRefreshToken() throws Exception {
        // Register and login
        RegisterRequest registerRequest = new RegisterRequest("logout@gmail.com", "password123");
        mockMvc.perform(post("/v1/auth/register")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("logout@gmail.com", "password123");
        String loginResponse = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponse)
                .path("data")
                .path("refresh_token")
                .asText();

        // Logout
        LogoutRequest logoutRequest = new LogoutRequest(refreshToken);
        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        // Attempt to use the invalidated refresh token
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }
}
