package com.osporo.engine.user.model;

import com.osporo.engine.shared.enums.RoleType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(
            name = "roles",
            columnDefinition = "role_type[]",
            nullable = false
    )
    private String[] roles;

    @Column(name = "stripe_connect_id")
    private String stripeConnectId;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public List<RoleType> getRolesList() {
        if (roles == null) return new ArrayList<>();
        return Arrays.stream(roles)
                .map(RoleType::valueOf)
                .collect(Collectors.toList());
    }

    public void setRolesList(List<RoleType> roleList) {
        this.roles = roleList == null
                ? new String[]{}
                : roleList.stream()
                  .map(RoleType::name)
                  .toArray(String[]::new);
    }

    public boolean hasRole(RoleType role) {
        return getRolesList().contains(role);
    }

    public void addRole(RoleType role) {
        List<RoleType> current = new ArrayList<>(getRolesList());
        if (!current.contains(role)) {
            current.add(role);
            setRolesList(current);
        }
    }

    public void removeRole(RoleType role) {
        List<RoleType> current = new ArrayList<>(getRolesList());
        current.remove(role);
        setRolesList(current);
    }
}
