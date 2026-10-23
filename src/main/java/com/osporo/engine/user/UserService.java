package com.osporo.engine.user;

import com.osporo.engine.user.model.User;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.shared.security.OsporoPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.osporo.engine.user.dto.UpdateUserProfileRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantContextHolder tenantContextHolder;

    public UserService (
            UserRepository userRepository,
            TenantContextHolder tenantContextHolder
    ) {
        this.userRepository = userRepository;
        this.tenantContextHolder = tenantContextHolder;
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
