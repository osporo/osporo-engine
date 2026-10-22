package com.osporo.engine.user;

import com.osporo.engine.user.model.User;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.security.TenantContextHolder;
import org.springframework.stereotype.Service;

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
}
