package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserSuspensionResponse(
        UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("actor_id") UUID actorId,
        String action,
        @JsonProperty("violation_type") String violationType,
        String details,
        @JsonProperty("suspended_at") OffsetDateTime suspendedAt
) {}
