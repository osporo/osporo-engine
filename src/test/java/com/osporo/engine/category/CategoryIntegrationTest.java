package com.osporo.engine.category;

import com.osporo.engine.auth.JwtService;
import com.osporo.engine.auth.dto.LoginRequest;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.UserRepository;
import com.osporo.engine.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
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
import java.util.Map;
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
class CategoryIntegrationTest {

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
    private JdbcClient jdbcClient;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void create_shouldPersistAFlatCategoryInTheAuthenticatedTenant() throws Exception {
        UUID tenantId = defaultTenantId();
        String ownerToken = login(tenantId, "owner@osporo.example");

        String response = mockMvc.perform(post("/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("X-Tenant-ID", createTenant().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Gadgets & Electronics  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Gadgets & Electronics"))
                .andExpect(jsonPath("$.data.slug").value("gadgets-electronics"))
                .andExpect(jsonPath("$.data.is_active").value(true))
                .andExpect(jsonPath("$.data.created_at").isString())
                .andExpect(jsonPath("$.meta.requestId").isString())
                .andReturn().getResponse().getContentAsString();

        UUID categoryId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
        assertThat(jdbcClient.sql("SELECT tenant_id FROM categories WHERE id = :id AND parent_id IS NULL")
                .param("id", categoryId).query(UUID.class).single()).isEqualTo(tenantId);
    }

    @Test
    void create_shouldRejectDuplicateSlugsOnlyWithinTheSameTenant() throws Exception {
        String ownerToken = login(defaultTenantId(), "owner@osporo.example");
        Tenant otherTenant = createTenant();
        String otherToken = categoryCreatorToken(otherTenant.getId());

        mockMvc.perform(post("/v1/categories").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Home & Garden\",\"is_active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.is_active").value(false));

        mockMvc.perform(post("/v1/categories").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"HOME - GARDEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_SLUG_EXISTS"));

        mockMvc.perform(post("/v1/categories").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Home & Garden\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("home-garden"));
    }

    @Test
    void create_shouldRequireAuthenticationAndPermission() throws Exception {
        String staffToken = login(defaultTenantId(), "staff@osporo.example");
        String body = "{\"name\":\"Electronics\"}";

        mockMvc.perform(post("/v1/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/categories").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_PERMISSIONS"));

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM categories").query(Long.class).single()).isZero();
    }

    @Test
    void create_shouldRejectInvalidNames() throws Exception {
        String ownerToken = login(defaultTenantId(), "owner@osporo.example");
        for (String name : List.of("   ", "a".repeat(101), "!!!", "bad\0name")) {
            mockMvc.perform(post("/v1/categories").header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", name))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("name"));
        }

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM categories").query(Long.class).single()).isZero();
    }

    @Test
    void list_shouldPaginateOnlyActiveCategoriesInTheGuestsTenant() throws Exception {
        UUID tenantId = defaultTenantId();
        insertCategory(tenantId, "Books", true);
        insertCategory(tenantId, "Art", true);
        insertCategory(tenantId, "Archived", false);
        insertCategory(createTenant().getId(), "Another tenant", true);

        mockMvc.perform(get("/v1/categories").header("X-Tenant-ID", tenantId).param("per_page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Art"))
                .andExpect(jsonPath("$.data[0].is_active").value(true))
                .andExpect(jsonPath("$.meta.pagination.page").value(1))
                .andExpect(jsonPath("$.meta.pagination.perPage").value(1))
                .andExpect(jsonPath("$.meta.pagination.total").value(2))
                .andExpect(jsonPath("$.meta.pagination.totalPages").value(2));

        mockMvc.perform(get("/v1/categories").header("X-Tenant-ID", tenantId)
                        .param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.pagination.total").value(2));
    }

    @Test
    void list_shouldRestrictInactiveFiltersAndUseTheSignedTenant() throws Exception {
        UUID tenantId = defaultTenantId();
        UUID otherTenantId = createTenant().getId();
        insertCategory(tenantId, "Books", true);
        insertCategory(tenantId, "Archived", false);
        insertCategory(otherTenantId, "Private archive", false);
        String ownerToken = login(tenantId, "owner@osporo.example");
        String staffToken = login(tenantId, "staff@osporo.example");

        mockMvc.perform(get("/v1/categories").header("X-Tenant-ID", tenantId).param("status", "ALL"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_PERMISSIONS"));
        mockMvc.perform(get("/v1/categories").header("Authorization", "Bearer " + staffToken)
                        .param("status", "INACTIVE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/categories").header("Authorization", "Bearer " + ownerToken)
                        .header("X-Tenant-ID", otherTenantId).param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Archived"))
                .andExpect(jsonPath("$.data[0].is_active").value(false));
        mockMvc.perform(get("/v1/categories").header("Authorization", "Bearer " + ownerToken)
                        .param("status", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.pagination.total").value(2));
    }

    @Test
    void list_shouldRejectInvalidFiltersAndUnavailableGuestTenants() throws Exception {
        UUID tenantId = defaultTenantId();
        Tenant suspended = createTenant();
        suspended.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.saveAndFlush(suspended);

        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TENANT_CONTEXT_MISSING"));
        mockMvc.perform(get("/v1/categories").header("X-Tenant-ID", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TENANT_NOT_FOUND"));
        mockMvc.perform(get("/v1/categories").header("X-Tenant-ID", suspended.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_SUSPENDED"));

        for (String query : List.of("page=0", "per_page=101", "status=DELETED", "page=1&page=2")) {
            mockMvc.perform(get("/v1/categories?" + query).header("X-Tenant-ID", tenantId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
        }
    }

    private void insertCategory(UUID tenantId, String name, boolean active) {
        jdbcClient.sql("""
                INSERT INTO categories (tenant_id, name, slug, is_active)
                VALUES (:tenantId, :name, :slug, :active)
                """)
                .param("tenantId", tenantId)
                .param("name", name)
                .param("slug", UUID.randomUUID().toString())
                .param("active", active)
                .update();
    }

    private UUID defaultTenantId() {
        return jdbcClient.sql("SELECT id FROM tenants WHERE slug = 'default-marketplace'").query(UUID.class).single();
    }

    private String login(UUID tenantId, String email) throws Exception {
        String response = mockMvc.perform(post("/v1/auth/login")
                        .header("X-Tenant-ID", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "OsporoLocalOwner123!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("access_token").asText();
    }

    private Tenant createTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Other marketplace");
        tenant.setSlug("other-" + UUID.randomUUID());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlan("STARTER");
        tenant.setCreatedAt(OffsetDateTime.now());

        return tenantRepository.saveAndFlush(tenant);
    }

    private String categoryCreatorToken(UUID tenantId) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail("category-creator@example.com");
        user.setPasswordHash("unused");
        user.setRoles(List.of(RoleType.BUYER));
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.saveAndFlush(user);

        return jwtService.generateAccessToken(user.getId(), tenantId, List.of("BUYER"), List.of("CATEGORY_CREATE"));
    }
}
