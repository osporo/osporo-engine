package com.osporo.engine.auth.dto;

public record RegisterRequest(
        String email,
        String password
) {}
