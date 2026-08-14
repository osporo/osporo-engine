package com.osporo.engine.user.model;

import com.osporo.engine.shared.converter.RoleTypeListConverter;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Convert(converter = RoleTypeListConverter.class)
    @Column(
        name = "roles",
        columnDefinition = "text[]",
        nullable = false
    )
    private List<RoleType> roles;

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

    public boolean hasRole(RoleType role) {
        return roles.contains(role);
    }

    public void addRole(RoleType role) {
        if (!roles.contains(role)) {
            roles.add(role);
        }
    }

    public void removeRole(RoleType role) {
        roles.remove(role);
    }

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
