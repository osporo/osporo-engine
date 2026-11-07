package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserUnsuspensionResponse(
        UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("actor_id") UUID actorId,
        String action,
        String details,
        @JsonProperty("action_date") OffsetDateTime actionDate,
        Suspension suspension
) {
    public record Suspension(
            UUID id,
            @JsonProperty("actor_id") UUID actorId,
            @JsonProperty("violation_type") String violationType,
            String details,
            @JsonProperty("action_date") OffsetDateTime actionDate
    ) {}
}
