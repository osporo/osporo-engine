package com.osporo.engine.user;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.exception.RequestValidationException;
import com.osporo.engine.user.dto.UpdateUserProfileRequest;
import tools.jackson.databind.ObjectMapper;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.user.model.User;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.TenantNotFoundException;
import com.osporo.engine.shared.exception.TenantSuspendedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    private final UserAuthorizationPolicy userAuthorizationPolicy = new UserAuthorizationPolicy();

    private final TenantContextHolder tenantContextHolder = new TenantContextHolder();

    @AfterEach
    void clearTenantContext() {
        tenantContextHolder.clear();
    }

    @Test
    void getUser_shouldReturnUserWithinCurrentTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);

        tenantContextHolder.setTenantId(tenantId);
        when(userRepository.findByIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.of(user));

        UserService userService = createUserService();

        assertThat(userService.getUser(userId)).isSameAs(user);
        verify(userRepository).findByIdAndTenantId(userId, tenantId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void getUser_shouldReturnProfileNotFoundWhenScopedLookupIsEmpty() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        tenantContextHolder.setTenantId(tenantId);
        when(userRepository.findByIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.empty());

        UserService userService = createUserService();

        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOfSatisfying(ProfileNotFoundException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("The requested profile was not found.");
                });

        verify(userRepository).findByIdAndTenantId(userId, tenantId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void getUser_shouldRejectMissingTenantBeforeQueryingRepository() {
        UserService userService = createUserService();

        assertThatThrownBy(() -> userService.getUser(UUID.randomUUID()))
                .isInstanceOfSatisfying(OsporoException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verifyNoInteractions(userRepository);
    }

    @Test
    void getOwnProfile_shouldLoadOnlyThePrincipalsAccountAndTenant() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OsporoPrincipal principal = new OsporoPrincipal(userId, tenantId, List.of());
        User user = new User();

        tenantContextHolder.setTenantId(UUID.randomUUID());
        when(userRepository.findByIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.of(user));

        UserService userService = createUserService();

        assertThat(userService.getOwnProfile(principal)).isSameAs(user);
        verify(userRepository).findByIdAndTenantId(userId, tenantId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void getOwnProfile_shouldRejectMissingOrOutOfTenantAccount() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OsporoPrincipal principal = new OsporoPrincipal(userId, tenantId, List.of());

        when(userRepository.findByIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.empty());

        UserService userService = createUserService();

        assertThatThrownBy(() -> userService.getOwnProfile(principal))
                .isInstanceOf(ProfileNotFoundException.class);

        verify(userRepository).findByIdAndTenantId(userId, tenantId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateOwnProfile_shouldTrimDisplayNameAndPreserveOmittedAvatar() {
        OsporoPrincipal principal = new OsporoPrincipal(UUID.randomUUID(), UUID.randomUUID(), List.of());
        User user = userForUpdate(principal);
        OffsetDateTime previousUpdate = user.getUpdatedAt();
        UpdateUserProfileRequest request = profileUpdate("{\"display_name\":\"  New Name  \"}");

        UserService userService = createUserService();
        User result = userService.updateOwnProfile(principal, request);

        assertThat(result).isSameAs(user);
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/original.png");
        assertThat(user.getUpdatedAt()).isAfter(previousUpdate);
        verify(userRepository).save(user);
    }

    @Test
    void updateOwnProfile_shouldClearAvatarAndPreserveOmittedDisplayName() {
        OsporoPrincipal principal = new OsporoPrincipal(UUID.randomUUID(), UUID.randomUUID(), List.of());
        User user = userForUpdate(principal);
        UpdateUserProfileRequest request = profileUpdate("{\"avatar_url\":null}");

        UserService userService = createUserService();
        userService.updateOwnProfile(principal, request);

        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getDisplayName()).isEqualTo("Original Name");
        verify(userRepository).save(user);
    }

    @Test
    void updateOwnProfile_shouldAcceptBoundaryLengths() {
        OsporoPrincipal principal = new OsporoPrincipal(UUID.randomUUID(), UUID.randomUUID(), List.of());
        User user = userForUpdate(principal);
        String displayName = "a".repeat(100);
        String avatarUrl = "https://example.com/" + "a".repeat(2048 - "https://example.com/".length());
        UpdateUserProfileRequest request = profileUpdate(
                "{\"display_name\":\"  " + displayName + "  \",\"avatar_url\":\"" + avatarUrl + "\"}");

        UserService userService = createUserService();
        userService.updateOwnProfile(principal, request);

        assertThat(user.getDisplayName()).isEqualTo(displayName);
        assertThat(user.getAvatarUrl()).isEqualTo(avatarUrl);
    }

    @Test
    void updateOwnProfile_shouldKeepProfileFieldsForEmptyUpdate() {
        OsporoPrincipal principal = new OsporoPrincipal(UUID.randomUUID(), UUID.randomUUID(), List.of());
        User user = userForUpdate(principal);

        UserService userService = createUserService();
        userService.updateOwnProfile(principal, profileUpdate("{}"));

        assertThat(user.getDisplayName()).isEqualTo("Original Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/original.png");
    }

    @Test
    void updateOwnProfile_shouldValidateEntireRequestBeforeLoadingOrChangingUser() {
        OsporoPrincipal principal = new OsporoPrincipal(UUID.randomUUID(), UUID.randomUUID(), List.of());
        UpdateUserProfileRequest request = profileUpdate(
                "{\"display_name\":\"New Name\",\"avatar_url\":\"http://example.com/avatar.png\"}");

        UserService userService = createUserService();

        assertThatThrownBy(() -> userService.updateOwnProfile(principal, request))
                .isInstanceOf(RequestValidationException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void updateOwnProfile_shouldRejectMissingOrOutOfTenantUserWithoutSaving() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OsporoPrincipal principal = new OsporoPrincipal(userId, tenantId, List.of());
        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

        UserService userService = createUserService();

        assertThatThrownBy(() -> userService.updateOwnProfile(principal, profileUpdate("{}")))
                .isInstanceOf(ProfileNotFoundException.class);

        verify(userRepository).findByIdAndTenantId(userId, tenantId);
        verifyNoMoreInteractions(userRepository);
    }

    private User userForUpdate(OsporoPrincipal principal) {
        User user = new User();
        user.setId(principal.getUserId());
        user.setTenantId(principal.getTenantId());
        user.setDisplayName("Original Name");
        user.setAvatarUrl("https://example.com/original.png");
        user.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        when(userRepository.findByIdAndTenantId(principal.getUserId(), principal.getTenantId()))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        return user;
    }

    private UserService createUserService() {
        return new UserService(
                userRepository,
                tenantContextHolder,
                tenantRepository,
                userAuthorizationPolicy
        );
    }

    @Test
    void getUserProfile_shouldRequireTenantContextForGuest() {
        assertThatThrownBy(() -> createUserService().getUserProfile(UUID.randomUUID(), null))
                .isInstanceOfSatisfying(OsporoException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING));

        verifyNoInteractions(tenantRepository, userRepository);
    }

    @Test
    void getUserProfile_shouldUsePrincipalTenantInsteadOfHeaderContext() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        tenantContextHolder.setTenantId(UUID.randomUUID());
        OsporoPrincipal principal = new OsporoPrincipal(UUID.randomUUID(), tenantId, List.of());
        Tenant tenant = new Tenant();
        tenant.setStatus(TenantStatus.ACTIVE);
        User user = new User();
        user.setRoles(List.of(RoleType.SELLER));

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

        assertThat(createUserService().getUserProfile(userId, principal)).isSameAs(user);
        verify(userRepository).findByIdAndTenantId(userId, tenantId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void getUserProfile_shouldRejectUnknownTenantBeforeLookingUpTarget() {
        UUID tenantId = UUID.randomUUID();
        tenantContextHolder.setTenantId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createUserService().getUserProfile(UUID.randomUUID(), null))
                .isInstanceOf(TenantNotFoundException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void getUserProfile_shouldRejectInactiveTenantBeforeLookingUpTarget() {
        UUID tenantId = UUID.randomUUID();
        tenantContextHolder.setTenantId(tenantId);
        Tenant tenant = new Tenant();
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> createUserService().getUserProfile(UUID.randomUUID(), null))
                .isInstanceOf(TenantSuspendedException.class);

        verifyNoInteractions(userRepository);
    }

    private UpdateUserProfileRequest profileUpdate(String json) {
        return new ObjectMapper().readValue(json, UpdateUserProfileRequest.class);
    }
}
