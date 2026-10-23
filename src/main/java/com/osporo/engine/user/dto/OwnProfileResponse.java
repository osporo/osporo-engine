package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record OwnProfileResponse(
        UUID id,
        @JsonProperty("display_name") String displayName,
        String email,
        @JsonProperty("avatar_url") String avatarUrl,
        List<String> roles,
        List<String> permissions
) {
}
