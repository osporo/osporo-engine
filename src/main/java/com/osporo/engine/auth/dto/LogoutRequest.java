package com.osporo.engine.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @JsonProperty("refresh_token")
    @NotBlank String refreshToken
) {}
