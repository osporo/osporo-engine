package com.osporo.engine.tenant.model;

import com.osporo.engine.shared.converter.PermissionListConverter;
import com.osporo.engine.shared.enums.Permission;
import com.osporo.engine.shared.enums.RoleType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tenant_role_config")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TenantRoleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false)
    private RoleType roleName;

    @Convert(converter = PermissionListConverter.class)
    @Column(
        name = "permissions",
        columnDefinition = "text[]",
        nullable = false
    )
    private List<Permission> permissions;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
