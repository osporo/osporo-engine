package com.osporo.engine.auth;

// Returned by login and refresh — passed to the controller for mapping
public record TokenPair(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
