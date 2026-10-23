package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FullUserProfileResponse(
        UUID id,

        @JsonProperty("display_name")
        String displayName,

        String email,

        @JsonProperty("avatar_url")
        String avatarUrl,

        List<String> roles,
        String status,

        @JsonProperty("seller_rating")
        BigDecimal sellerRating,

        @JsonProperty("created_at")
        OffsetDateTime createdAt,

        @JsonProperty("suspended_at")
        OffsetDateTime suspendedAt
) {
}
