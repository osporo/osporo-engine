package com.osporo.engine.auth;

import com.osporo.engine.auth.dto.*;
import com.osporo.engine.shared.response.ApiResponse;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.user.model.User;

import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthMapper authMapper;

    public AuthController(AuthService authService, AuthMapper authMapper) {
        this.authService = authService;
        this.authMapper  = authMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
        @RequestBody @Valid RegisterRequest request
    ) {
        User user = authService.register(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(authMapper.toRegisterResponse(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @RequestBody @Valid LoginRequest request
    ) {
        TokenPair tokens = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of(authMapper.toLoginResponse(tokens)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
        @RequestBody @Valid RefreshRequest request
    ) {
        TokenPair tokens = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.of(authMapper.toLoginResponse(tokens)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestBody @Valid LogoutRequest request
    ) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}