package com.osporo.engine.tenant;

import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.tenant.model.TenantRoleConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRoleConfigRepository
        extends JpaRepository<TenantRoleConfig, UUID> {

    Optional<TenantRoleConfig> findByTenantIdAndRoleName(
            UUID tenantId,
            RoleType roleName
    );

    List<TenantRoleConfig> findAllByTenantId(UUID tenantId);

    Optional<TenantRoleConfig> findByTenantIdAndIsDefaultTrue(UUID tenantId);
}
