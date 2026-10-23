package com.osporo.engine.user;

import com.osporo.engine.auth.JwtService;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class UserProfileIntegrationTest {

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
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void setUpTenants() {
        tenantId = createTenant();
        otherTenantId = createTenant();
    }

    @ParameterizedTest
    @CsvSource({
            "SELLER, ACTIVE, GUEST, 200, PUBLIC",
            "BUYER, ACTIVE, GUEST, 404, HIDDEN",
            "MARKETPLACE_STAFF, ACTIVE, GUEST, 404, HIDDEN",
            "MARKETPLACE_OWNER, ACTIVE, PUBLIC, 404, HIDDEN",
            "SELLER, ACTIVE, NONE, 200, PUBLIC",
            "BUYER, ACTIVE, NONE, 404, HIDDEN",
            "BUYER, ACTIVE, PUBLIC, 200, PUBLIC",
            "SELLER, ACTIVE, PUBLIC, 200, PUBLIC",
            "MARKETPLACE_STAFF, ACTIVE, ANY, 200, FULL",
            "MARKETPLACE_OWNER, ACTIVE, ANY, 200, FULL",
            "SELLER, SUSPENDED, GUEST, 404, HIDDEN",
            "SELLER, SUSPENDED, PUBLIC, 404, HIDDEN",
            "SELLER, SUSPENDED, ANY, 200, FULL",
            "MARKETPLACE_STAFF, SUSPENDED, ANY, 200, FULL",
            "SELLER, DELETED, ANY, 404, HIDDEN",
            "BUYER, DELETED, PUBLIC, 404, HIDDEN",
            "OSPORO_ADMIN+SELLER, ACTIVE, ANY, 404, HIDDEN",
            "OSPORO_ADMIN+SELLER, ACTIVE, GUEST, 404, HIDDEN",
            "MARKETPLACE_STAFF+SELLER, ACTIVE, GUEST, 200, PUBLIC",
            "MARKETPLACE_OWNER+SELLER, ACTIVE, PUBLIC, 200, PUBLIC",
            "MARKETPLACE_OWNER+BUYER, ACTIVE, PUBLIC, 200, PUBLIC",
            "BUYER, ACTIVE, BOTH, 200, FULL",
            "BUYER, ACTIVE, PLATFORM, 404, HIDDEN",
            "SELLER, ACTIVE, PLATFORM, 200, PUBLIC",
            "MARKETPLACE_STAFF, ACTIVE, PLATFORM, 404, HIDDEN"
    })
    void getUserProfile_shouldEnforceVisibilityAndProjection(
            String roles,
            String accountStatus,
            String caller,
            int expectedStatus,
            String projection
    ) throws Exception {
        User target = createUser(tenantId, roles, accountStatus);
        ResultActions result = mockMvc.perform(profileRequest(target.getId(), caller))
                .andExpect(status().is(expectedStatus));

        if (projection.equals("HIDDEN")) {
            assertProfileNotFound(result);
            return;
        }

        result.andExpect(jsonPath("$.data.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.data.display_name").value("Profile Name"))
                .andExpect(jsonPath("$.data.avatar_url").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.seller_rating").value(nullValue()))
                .andExpect(jsonPath("$.meta.requestId").isString());

        if (projection.equals("PUBLIC")) {
            result.andExpect(jsonPath("$.data.*", hasSize(5)))
                    .andExpect(jsonPath("$.data.member_since").isString())
                    .andExpect(jsonPath("$.data.email").doesNotExist())
                    .andExpect(jsonPath("$.data.roles").doesNotExist())
                    .andExpect(jsonPath("$.data.status").doesNotExist());
        } else {
            result.andExpect(jsonPath("$.data.*", hasSize(9)))
                    .andExpect(jsonPath("$.data.email").value(target.getEmail()))
                    .andExpect(jsonPath("$.data.roles").value(contains(roles.split("\\+"))))
                    .andExpect(jsonPath("$.data.status").value(accountStatus))
                    .andExpect(jsonPath("$.data.created_at").isString());

            if (accountStatus.equals("SUSPENDED")) {
                result.andExpect(jsonPath("$.data.suspended_at").isString());
            } else {
                result.andExpect(jsonPath("$.data.suspended_at").value(nullValue()));
            }
        }

        result.andExpect(jsonPath("$.data.password_hash").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.stripe_connect_id").doesNotExist())
                .andExpect(jsonPath("$.data.stripeConnectId").doesNotExist())
                .andExpect(jsonPath("$.data.permissions").doesNotExist())
                .andExpect(jsonPath("$.data.tenant_id").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "PUBLIC", "ANY"})
    void getUserProfile_shouldHideCrossTenantAndMissingTargets(String caller) throws Exception {
        User target = createUser(otherTenantId, "SELLER", "ACTIVE");

        assertProfileNotFound(mockMvc.perform(profileRequest(target.getId(), caller)));
        assertProfileNotFound(mockMvc.perform(profileRequest(UUID.randomUUID(), caller)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-tenant"})
    void getUserProfile_shouldRequireValidTenantContextForGuests(String tenantHeader) throws Exception {
        mockMvc.perform(get("/v1/users/" + UUID.randomUUID()).header("X-Tenant-ID", tenantHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TENANT_CONTEXT_MISSING"));
    }

    @Test
    void getUserProfile_shouldRejectUnknownTenant() throws Exception {
        mockMvc.perform(get("/v1/users/" + UUID.randomUUID()).header("X-Tenant-ID", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TENANT_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "ANY"})
    void getUserProfile_shouldRejectInactiveTenant(String caller) throws Exception {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.saveAndFlush(tenant);

        mockMvc.perform(profileRequest(UUID.randomUUID(), caller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_SUSPENDED"));
    }

    @Test
    void getUserProfile_shouldRejectMalformedProfileId() throws Exception {
        mockMvc.perform(get("/v1/users/not-a-uuid").header("X-Tenant-ID", tenantId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    void getUserProfile_shouldKeepNullablePublicFields() throws Exception {
        User target = createUser(tenantId, "SELLER", "ACTIVE");
        target.setDisplayName(null);
        target.setAvatarUrl(null);
        userRepository.saveAndFlush(target);

        mockMvc.perform(profileRequest(target.getId(), "GUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.*", hasSize(5)))
                .andExpect(jsonPath("$.data.display_name").value(nullValue()))
                .andExpect(jsonPath("$.data.avatar_url").value(nullValue()));
    }

    private void assertProfileNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("The requested profile was not found."))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private MockHttpServletRequestBuilder profileRequest(UUID targetId, String caller) {
        MockHttpServletRequestBuilder request = get("/v1/users/" + targetId);

        if (caller.equals("GUEST")) {
            return request.header("X-Tenant-ID", tenantId);
        }

        List<String> permissions = switch (caller) {
            case "PUBLIC" -> List.of("USER_READ_PUBLIC");
            case "ANY" -> List.of("USER_READ_ANY");
            case "BOTH" -> List.of("USER_READ_PUBLIC", "USER_READ_ANY");
            case "PLATFORM" -> List.of("PLATFORM_ADMIN");
            default -> List.of();
        };
        User callerAccount = createUser(tenantId, "MARKETPLACE_OWNER", "ACTIVE");
        String token = jwtService.generateAccessToken(
                callerAccount.getId(), tenantId, List.of("MARKETPLACE_OWNER"), permissions);

        return request.header("Authorization", "Bearer " + token).header("X-Tenant-ID", otherTenantId);
    }

    private UUID createTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Profile test marketplace");
        tenant.setSlug("profile-test-" + UUID.randomUUID());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlan("STARTER");
        tenant.setCreatedAt(OffsetDateTime.now());

        return tenantRepository.saveAndFlush(tenant).getId();
    }

    private User createUser(UUID userTenantId, String roles, String accountStatus) {
        User user = new User();
        user.setTenantId(userTenantId);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("private-password-hash");
        user.setStripeConnectId("private-provider-id");
        user.setDisplayName("Profile Name");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setRoles(Arrays.stream(roles.split("\\+")).map(RoleType::valueOf).toList());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        if (accountStatus.equals("SUSPENDED")) {
            user.setSuspendedAt(OffsetDateTime.now());
        } else if (accountStatus.equals("DELETED")) {
            user.setDeletedAt(OffsetDateTime.now());
        }

        return userRepository.saveAndFlush(user);
    }
}
