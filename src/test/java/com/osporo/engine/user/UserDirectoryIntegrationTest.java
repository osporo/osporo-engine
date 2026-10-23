package com.osporo.engine.user;

import com.osporo.engine.auth.JwtService;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class UserDirectoryIntegrationTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");

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

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID otherTenantId;
    private final List<User> tenantUsers = new ArrayList<>();

    @BeforeEach
    void setUpDirectory() {
        tenantId = createTenant();
        otherTenantId = createTenant();
        tenantUsers.clear();

        createUser(tenantId, "BUYER", "ACTIVE");
        createUser(tenantId, "SELLER", "ACTIVE");
        createUser(tenantId, "BUYER+SELLER", "ACTIVE");
        createUser(tenantId, "MARKETPLACE_STAFF", "ACTIVE");
        createUser(tenantId, "MARKETPLACE_OWNER", "SUSPENDED");
        createUser(tenantId, "SELLER", "DELETED");
        createUser(tenantId, "BUYER", "DELETED_AND_SUSPENDED");
        createUser(tenantId, "OSPORO_ADMIN+SELLER", "ACTIVE");
        createUser(otherTenantId, "SELLER", "ACTIVE");
        createUser(otherTenantId, "SELLER", "DELETED");
        entityManager.clear();
    }

    @Test
    void directory_shouldReturnOnlyTenantSummariesWithExistingMetadataContract() throws Exception {
        mockMvc.perform(directoryRequest(tenantId, List.of("USER_READ_ANY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].*", hasSize(6)))
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].display_name").hasJsonPath())
                .andExpect(jsonPath("$.data[0].avatar_url").hasJsonPath())
                .andExpect(jsonPath("$.data[0].email").isString())
                .andExpect(jsonPath("$.data[0].roles").isArray())
                .andExpect(jsonPath("$.data[0].created_at").isString())
                .andExpect(jsonPath("$.data[0].password_hash").doesNotExist())
                .andExpect(jsonPath("$.data[0].stripe_connect_id").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").isString())
                .andExpect(jsonPath("$.meta.filter_roles").doesNotExist())
                .andExpect(jsonPath("$.meta.pagination.page").value(1))
                .andExpect(jsonPath("$.meta.pagination.perPage").value(20))
                .andExpect(jsonPath("$.meta.pagination.total").value(5))
                .andExpect(jsonPath("$.meta.pagination.totalPages").value(1));
    }

    @ParameterizedTest
    @CsvSource({"DEFAULT, 5", "ACTIVE, 4", "SUSPENDED, 1", "DELETED, 2", "ALL, 7"})
    void directory_shouldApplyLifecycleFiltersToResultsAndCount(String lifecycle, int total) throws Exception {
        MockHttpServletRequestBuilder request = directoryRequest(tenantId, List.of("USER_READ_ANY"));

        if (!lifecycle.equals("DEFAULT")) {
            request.param("status", lifecycle);
        }

        JsonNode response = responseFor(request);
        assertThat(response.get("meta").get("pagination").get("total").asInt()).isEqualTo(total);
        assertThat(ids(response)).containsExactlyInAnyOrderElementsOf(
                eligibleUsers(lifecycle).stream().map(user -> user.getId().toString()).toList());
    }

    @Test
    void directory_shouldMatchAnyRepeatedRoleWithoutDuplicatingMultiRoleUsers() throws Exception {
        JsonNode response = responseFor(directoryRequest(tenantId, List.of("USER_READ_ANY"))
                .param("roles", "BUYER", "SELLER", "BUYER"));

        List<String> expected = eligibleUsers("DEFAULT").stream()
                .filter(user -> user.hasRole(RoleType.BUYER) || user.hasRole(RoleType.SELLER))
                .map(user -> user.getId().toString())
                .toList();

        assertThat(ids(response)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(response.get("meta").get("pagination").get("total").asInt()).isEqualTo(3);
    }

    @Test
    void directory_shouldCombineRoleAndLifecycleFilters() throws Exception {
        JsonNode response = responseFor(directoryRequest(tenantId, List.of("USER_READ_ANY"))
                .param("roles", "SELLER")
                .param("status", "DELETED"));

        assertThat(ids(response)).containsExactly(
                tenantUsers.stream().filter(user -> user.isDeleted() && user.hasRole(RoleType.SELLER))
                        .findFirst().orElseThrow().getId().toString());
        assertThat(response.get("meta").get("pagination").get("total").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED_AT, asc", "CREATED_AT, desc",
            "UPDATED_AT, asc", "UPDATED_AT, desc",
            "DELETED_AT, asc", "DELETED_AT, desc",
            "SUSPENDED_AT, asc", "SUSPENDED_AT, desc"
    })
    void directory_shouldSortApprovedFieldsWithNullsLastAndUuidTieBreaker(String sort, String order)
            throws Exception {
        Comparator<OffsetDateTime> timestamps = order.equals("asc")
                ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<User> expectedOrder = Comparator
                .comparing((User user) -> timestamp(user, sort), Comparator.nullsLast(timestamps))
                .thenComparing(user -> user.getId().toString());
        List<String> expected = eligibleUsers("ALL").stream().sorted(expectedOrder)
                .map(user -> user.getId().toString()).toList();

        JsonNode response = responseFor(directoryRequest(tenantId, List.of("USER_READ_ANY"))
                .param("status", "ALL").param("sort", sort).param("order", order));

        assertThat(ids(response)).containsExactlyElementsOf(expected);
    }

    @Test
    void directory_shouldKeepTotalsAndOrderingAcrossPages() throws Exception {
        List<String> expected = eligibleUsers("DEFAULT").stream()
                .map(user -> user.getId().toString()).sorted().toList();
        List<String> actual = new ArrayList<>();

        for (int page = 1; page <= 3; page++) {
            JsonNode response = responseFor(directoryRequest(tenantId, List.of("USER_READ_ANY"))
                    .param("per_page", "2").param("page", Integer.toString(page)));
            JsonNode pagination = response.get("meta").get("pagination");

            assertThat(pagination.get("page").asInt()).isEqualTo(page);
            assertThat(pagination.get("total").asInt()).isEqualTo(5);
            assertThat(pagination.get("totalPages").asInt()).isEqualTo(3);
            actual.addAll(ids(response));
        }

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void directory_shouldReturnEmptyFarPageWithoutOverflowAndPreserveTotal() throws Exception {
        mockMvc.perform(directoryRequest(tenantId, List.of("USER_READ_ANY"))
                        .param("page", "2147483647").param("per_page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.pagination.page").value(2147483647))
                .andExpect(jsonPath("$.meta.pagination.perPage").value(100))
                .andExpect(jsonPath("$.meta.pagination.total").value(5));
    }

    @ParameterizedTest
    @MethodSource("com.osporo.engine.user.UserDirectoryQueryTest#invalidParameters")
    void directory_shouldRejectInvalidParameters(String name, String value) throws Exception {
        mockMvc.perform(directoryRequest(tenantId, List.of("USER_READ_ANY")).param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @ParameterizedTest
    @CsvSource({"NONE", "USER_READ_PUBLIC", "PLATFORM_ADMIN"})
    void directory_shouldRequireReadAnyBeforeParsingFilters(String permission) throws Exception {
        List<String> permissions = permission.equals("NONE") ? List.of() : List.of(permission);

        mockMvc.perform(directoryRequest(tenantId, permissions).param("sort", "invalid-sort"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void directory_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v1/users").header("X-Tenant-ID", tenantId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        mockMvc.perform(get("/v1/users").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void directory_shouldRejectUnknownAndInactiveTenants() throws Exception {
        mockMvc.perform(directoryRequest(UUID.randomUUID(), List.of("USER_READ_ANY")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TENANT_NOT_FOUND"));

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.saveAndFlush(tenant);

        mockMvc.perform(directoryRequest(tenantId, List.of("USER_READ_ANY")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_SUSPENDED"));
    }

    private JsonNode responseFor(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body);
    }

    private List<String> ids(JsonNode response) {
        List<String> ids = new ArrayList<>();

        for (JsonNode user : response.get("data")) {
            ids.add(user.get("id").asString());
        }

        return ids;
    }

    private List<User> eligibleUsers(String lifecycle) {
        return tenantUsers.stream().filter(user -> !user.hasRole(RoleType.OSPORO_ADMIN))
                .filter(user -> switch (lifecycle) {
                    case "ACTIVE" -> !user.isDeleted() && !user.isSuspended();
                    case "SUSPENDED" -> !user.isDeleted() && user.isSuspended();
                    case "DELETED" -> user.isDeleted();
                    case "ALL" -> true;
                    default -> !user.isDeleted();
                }).toList();
    }

    private OffsetDateTime timestamp(User user, String sort) {
        return switch (sort) {
            case "CREATED_AT" -> user.getCreatedAt();
            case "UPDATED_AT" -> user.getUpdatedAt();
            case "DELETED_AT" -> user.getDeletedAt();
            case "SUSPENDED_AT" -> user.getSuspendedAt();
            default -> throw new IllegalArgumentException("Unsupported test sort field.");
        };
    }

    private MockHttpServletRequestBuilder directoryRequest(UUID tokenTenantId, List<String> permissions) {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(), tokenTenantId, List.of("BUYER"), permissions);

        return get("/v1/users").header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", otherTenantId);
    }

    private UUID createTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Directory test marketplace");
        tenant.setSlug("directory-" + UUID.randomUUID());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlan("STARTER");
        tenant.setCreatedAt(CREATED_AT);

        return tenantRepository.saveAndFlush(tenant).getId();
    }

    private void createUser(UUID userTenantId, String roles, String lifecycle) {
        User user = new User();
        user.setTenantId(userTenantId);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("private-password-hash");
        user.setStripeConnectId("private-provider-id");
        user.setRoles(Arrays.stream(roles.split("\\+")).map(RoleType::valueOf).toList());
        user.setCreatedAt(CREATED_AT);
        user.setUpdatedAt(CREATED_AT.plusMinutes(tenantUsers.size()));

        if (lifecycle.contains("SUSPENDED")) {
            user.setSuspendedAt(CREATED_AT.plusDays(tenantUsers.size()));
        }

        if (lifecycle.contains("DELETED")) {
            user.setDeletedAt(CREATED_AT.plusDays(tenantUsers.size()));
        }

        userRepository.saveAndFlush(user);

        if (userTenantId.equals(tenantId)) {
            tenantUsers.add(user);
        }
    }
}
