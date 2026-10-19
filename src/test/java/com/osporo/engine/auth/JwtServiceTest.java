package com.osporo.engine.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Claims;

class JwtServiceTest {

    @Test
    void accessTokenLifetime_shouldUseConfiguredSeconds() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(service, "accessTokenExpiry", 900L);

        String token = service.generateAccessToken(
            UUID.randomUUID(), UUID.randomUUID(), List.of("BUYER"), List.of("LISTING_READ")
        );
        Claims claims = service.validateAndExtract(token);

        assertThat(claims.getExpiration().toInstant())
            .isEqualTo(claims.getIssuedAt().toInstant().plusSeconds(900));
        assertThat(service.getAccessTokenExpiry()).isEqualTo(900L);
    }
}
