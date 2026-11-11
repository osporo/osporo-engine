package com.osporo.engine.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suspensions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSuspension {

    public enum Action {
        SUSPEND,
        LIFT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "report_actions")
    private Action action;

    @Column(name = "violation_type", length = 500)
    private String violationType;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(name = "action_date", nullable = false)
    private OffsetDateTime actionDate;

    @Column(name = "suspension_id")
    private UUID suspensionId;

    public UserSuspension(
            UUID tenantId,
            UUID userId,
            UUID actorId,
            Action action,
            String violationType,
            String details,
            OffsetDateTime actionDate,
            UUID suspensionId
    ) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.actorId = actorId;
        this.action = action;
        this.violationType = violationType;
        this.details = details;
        this.actionDate = actionDate;
        this.suspensionId = suspensionId;
    }
}
