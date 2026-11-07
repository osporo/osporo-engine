package com.osporo.engine.user.dto;

import jakarta.validation.constraints.Pattern;

public record UnsuspendUserRequest(
        @Pattern(regexp = "[^\\x00]*", message = "Must not contain NUL characters.") String details
) {}
