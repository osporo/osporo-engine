package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        @JsonProperty("display_name") String displayName,
        String email,
        List<String> roles,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
}
