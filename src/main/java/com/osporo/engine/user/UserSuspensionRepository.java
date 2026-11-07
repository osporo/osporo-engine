package com.osporo.engine.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
}
