package com.osporo.engine.auth.dto;

import java.util.UUID;

public record RegisterResponse (
        UUID id,
        String email,
        String[] roles
) {
}
