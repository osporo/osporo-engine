package com.osporo.engine.tenant.model;

import com.osporo.engine.shared.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "tenant_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TenantStatus status;

    @Column(nullable = false)
    private String plan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
