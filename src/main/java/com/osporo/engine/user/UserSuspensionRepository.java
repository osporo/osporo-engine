package com.osporo.engine.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserSuspensionRepository {

    private final JdbcClient jdbcClient;

    public UserSuspensionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public UUID recordSuspension(
            UUID tenantId,
            UUID userId,
            UUID actorId,
            String violationType,
            String details,
            OffsetDateTime suspendedAt
    ) {
        return jdbcClient.sql("""
                INSERT INTO suspensions (tenant_id, user_id, actor_id, action, violation_type, details, action_date)
                VALUES (:tenantId, :userId, :actorId, 'SUSPEND', :violationType, :details, :suspendedAt)
                RETURNING id
                """)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .param("actorId", actorId)
                .param("violationType", violationType)
                .param("details", details)
                .param("suspendedAt", suspendedAt)
                .query(UUID.class)
                .single();
    }

    public Optional<RecordedSuspension> findSuspension(UUID tenantId, UUID userId, OffsetDateTime suspendedAt) {
        return jdbcClient.sql("""
                SELECT id, actor_id, violation_type, details, action_date
                FROM suspensions
                WHERE tenant_id = :tenantId AND user_id = :userId
                  AND action = 'SUSPEND' AND action_date = :suspendedAt
                ORDER BY id
                LIMIT 1
                """)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .param("suspendedAt", suspendedAt)
                .query(RecordedSuspension.class)
                .optional();
    }

    public UUID recordLift(
            UUID tenantId,
            UUID userId,
            UUID actorId,
            String details,
            OffsetDateTime actionDate,
            UUID suspensionId
    ) {
        return jdbcClient.sql("""
                INSERT INTO suspensions (tenant_id, user_id, actor_id, action, details, action_date, suspension_id)
                VALUES (:tenantId, :userId, :actorId, 'LIFT', :details, :actionDate, :suspensionId)
                RETURNING id
                """)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .param("actorId", actorId)
                .param("details", details)
                .param("actionDate", actionDate)
                .param("suspensionId", suspensionId)
                .query(UUID.class)
                .single();
    }

    public record RecordedSuspension(
            UUID id, UUID actorId, String violationType, String details, OffsetDateTime actionDate
    ) {}
}
