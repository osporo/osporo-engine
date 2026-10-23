package com.osporo.engine.auth;

import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.exception.AccountSuspendedException;
import com.osporo.engine.shared.exception.InvalidTokenException;
import com.osporo.engine.shared.exception.TenantSuspendedException;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.UserRepository;
import com.osporo.engine.user.model.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountStatusService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public AccountStatusService(UserRepository userRepository, TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    public void requireActiveAccount(UUID userId, UUID tenantId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(InvalidTokenException::new);

        requireActiveAccount(user);
    }

    public void requireActiveAccount(User user) {
        if (user.isDeleted()) {
            throw new InvalidTokenException();
        }

        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(InvalidTokenException::new);

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(user.getTenantId());
        }
    }
}
