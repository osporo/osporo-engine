package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicUserProfileResponse(
        UUID id,

        @JsonProperty("display_name")
        String displayName,

        @JsonProperty("avatar_url")
        String avatarUrl,

        @JsonProperty("member_since")
        OffsetDateTime memberSince,

        @JsonProperty("seller_rating")
        BigDecimal sellerRating
) {
}
