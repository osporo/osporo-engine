package com.osporo.engine.user;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class UserServiceIntegrationTest {

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
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private EntityManager entityManager;

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
