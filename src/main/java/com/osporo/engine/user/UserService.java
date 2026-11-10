package com.osporo.engine.user;

import com.osporo.engine.auth.AccountStatusService;
import com.osporo.engine.auth.AuthService;
import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.InvalidTokenException;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.user.model.User;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.shared.security.OsporoPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.osporo.engine.user.dto.UpdateUserProfileRequest;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.exception.TenantNotFoundException;
import com.osporo.engine.shared.exception.TenantSuspendedException;
import com.osporo.engine.user.dto.UserDirectoryQuery;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantContextHolder tenantContextHolder;
    private final TenantRepository tenantRepository;
    private final UserAuthorizationPolicy userAuthorizationPolicy;
    private final AccountStatusService accountStatusService;
    private final AuthService authService;

    public UserService (
            UserRepository userRepository,
            TenantContextHolder tenantContextHolder,
            TenantRepository tenantRepository,
            UserAuthorizationPolicy userAuthorizationPolicy,
            AccountStatusService accountStatusService,
            AuthService authService
    ) {
        this.userRepository = userRepository;
        this.tenantContextHolder = tenantContextHolder;
        this.tenantRepository = tenantRepository;
        this.userAuthorizationPolicy = userAuthorizationPolicy;
        this.accountStatusService = accountStatusService;
        this.authService = authService;
    }

    public User getUser(UUID id) {
        UUID tenantId = tenantContextHolder.getTenantId();

        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(ProfileNotFoundException::new);
    }

    public User getOwnProfile(OsporoPrincipal principal) {
        return userRepository.findByIdAndTenantId(principal.getUserId(), principal.getTenantId())
                .orElseThrow(ProfileNotFoundException::new);
    }

    public User getUserProfile(UUID id, OsporoPrincipal principal) {
        UUID tenantId = principal == null ? tenantContextHolder.getTenantId() : principal.getTenantId();
        requireActiveTenant(tenantId);

        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(ProfileNotFoundException::new);

        userAuthorizationPolicy.requireVisibleProfile(user, principal);

        return user;
    }

    @Transactional(readOnly = true)
    public Page<User> getUserDirectory(OsporoPrincipal principal, UserDirectoryQuery filters) {
        userAuthorizationPolicy.requireDirectoryAccess(principal);
        UUID tenantId = principal.getTenantId();
        requireActiveTenant(tenantId);

        return userRepository.findDirectory(tenantId, filters);
    }

    private void requireActiveTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(tenantId);
        }
    }

    @Transactional
    public User updateOwnProfile(OsporoPrincipal principal, UpdateUserProfileRequest request) {
        request.validate();

        User user = getOwnProfile(principal);

        if (request.hasDisplayName()) {
            user.setDisplayName(request.getDisplayName());
        }

        if (request.hasAvatarUrl()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        user.setUpdatedAt(OffsetDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public void deleteOwnAccount(OsporoPrincipal principal) {
        UUID userId = principal.getUserId();
        UUID tenantId = principal.getTenantId();

        // Lock the owners in a shared order so two concurrent deletions cannot both pass the guard.
        List<User> accounts = userRepository.lockAccountAndActiveOwners(tenantId, userId);
        User user = accounts.stream()
                .filter(account -> account.getId().equals(userId))
                .findFirst()
                .orElseThrow(InvalidTokenException::new);
        accountStatusService.requireActiveAccount(user);

        if (user.hasRole(RoleType.MARKETPLACE_OWNER) && accounts.size() == 1) {
            throw new OsporoException(
                    ErrorCode.LAST_MARKETPLACE_OWNER,
                    HttpStatus.CONFLICT,
                    "Another active marketplace owner is required before deleting this account."
            );
        }

        OffsetDateTime deletedAt = OffsetDateTime.now();
        user.setDeletedAt(deletedAt);
        user.setUpdatedAt(deletedAt);
        authService.revokeRefreshTokens(userId, tenantId);
    }
}
