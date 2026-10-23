package com.osporo.engine.user;

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

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantContextHolder tenantContextHolder;
    private final TenantRepository tenantRepository;
    private final UserAuthorizationPolicy userAuthorizationPolicy;

    public UserService (
            UserRepository userRepository,
            TenantContextHolder tenantContextHolder,
            TenantRepository tenantRepository,
            UserAuthorizationPolicy userAuthorizationPolicy
    ) {
        this.userRepository = userRepository;
        this.tenantContextHolder = tenantContextHolder;
        this.tenantRepository = tenantRepository;
        this.userAuthorizationPolicy = userAuthorizationPolicy;
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
}
