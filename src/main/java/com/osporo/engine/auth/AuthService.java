package com.osporo.engine.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.osporo.engine.user.UserRepository;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.TenantRoleConfigRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.tenant.model.TenantRoleConfig;
import com.osporo.engine.auth.dto.LoginRequest;
import com.osporo.engine.auth.dto.LogoutRequest;
import com.osporo.engine.auth.dto.RefreshRequest;
import com.osporo.engine.auth.dto.RegisterRequest;
import com.osporo.engine.auth.model.RefreshToken;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.exception.AccountSuspendedException;
import com.osporo.engine.shared.exception.AuthenticationFailedException;
import com.osporo.engine.shared.exception.EmailAlreadyExistsException;
import com.osporo.engine.shared.exception.InvalidTokenException;
import com.osporo.engine.shared.exception.TenantNotFoundException;
import com.osporo.engine.shared.exception.TenantSuspendedException;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.user.model.User;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final TenantRoleConfigRepository tenantRoleConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantContextHolder tenantContextHolder;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        TenantRepository tenantRepository,
        TenantRoleConfigRepository tenantRoleConfigRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        TenantContextHolder tenantContextHolder
    ) {
        this.userRepository             = userRepository;
        this.refreshTokenRepository     = refreshTokenRepository;
        this.tenantRepository           = tenantRepository;
        this.tenantRoleConfigRepository = tenantRoleConfigRepository;
        this.passwordEncoder            = passwordEncoder;
        this.jwtService                 = jwtService;
        this.tenantContextHolder        = tenantContextHolder;
    }

    // ── Register ────────────────────────────────────────────────────────────

    @Transactional
    public User register(RegisterRequest request) {
        UUID tenantId = tenantContextHolder.getTenantId();

        // Verify the tenant exists and is active
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(tenantId);
        }

        // Reject duplicate email within this tenant
        if (userRepository.existsByEmailAndTenantId(request.email(), tenantId)) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // Resolve the default role — falls back to BUYER if tenant has not configured one
        RoleType defaultRole = tenantRoleConfigRepository
            .findByTenantIdAndIsDefaultTrue(tenantId)
            .map(TenantRoleConfig::getRoleName)
            .orElse(RoleType.BUYER);

        // Build the user entity
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.email().toLowerCase().strip());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(List.of(RoleType.valueOf(defaultRole.name())));
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        return userRepository.save(user);
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @Transactional
    public TokenPair login(LoginRequest request) {
        UUID tenantId = tenantContextHolder.getTenantId();

        // Find user by email within this tenant
        // Generic error message intentional — never confirm whether an email exists
        User user = userRepository
            .findByEmailAndTenantId(request.email().toLowerCase().strip(), tenantId)
            .orElseThrow(() -> new AuthenticationFailedException());

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }

        // Reject suspended accounts
        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }

        return issueTokenPair(user, tenantId);
    }

    // ── Refresh ─────────────────────────────────────────────────────────────

    @Transactional
    public TokenPair refresh(RefreshRequest request) {
        // Look up the refresh token
        RefreshToken stored = refreshTokenRepository
            .findByToken(request.refreshToken())
            .orElseThrow(() -> new InvalidTokenException());

        // Reject if invalidated (logged out) or expired
        if (stored.isInvalidated()) {
            throw new InvalidTokenException();
        }

        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException();
        }

        // Load the associated user — verify they are still active
        User user = userRepository
            .findByIdAndTenantId(stored.getUserId(), stored.getTenantId())
            .orElseThrow(() -> new InvalidTokenException());

        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }

        // Invalidate the old refresh token — rotation
        stored.setInvalidated(true);
        refreshTokenRepository.save(stored);

        // Issue a fresh token pair
        return issueTokenPair(user, stored.getTenantId());
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    @Transactional
    public void logout(LogoutRequest request) {
        // Silently succeed if token is not found or already invalidated
        // A client that sends a bad token on logout should not receive an error
        refreshTokenRepository
            .findByToken(request.refreshToken())
            .ifPresent(token -> {
                token.setInvalidated(true);
                refreshTokenRepository.save(token);
            });
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private TokenPair issueTokenPair(User user, UUID tenantId) {
        // Resolve permissions from the user's roles via tenant role config
        List<String> permissions = resolvePermissions(user.getRoles(), tenantId);
        List<String> roleNames   = user.getRoles().stream()
            .map(RoleType::name)
            .collect(Collectors.toList());

        // Generate access token — short lived, carries full permission set
        String accessToken = jwtService.generateAccessToken(
            user.getId(),
            tenantId,
            roleNames,
            permissions
        );

        // Generate refresh token — long lived, stored in database
        String refreshTokenValue = jwtService.generateRefreshToken();

        // Persist the refresh token
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setUserId(user.getId());
        refreshToken.setTenantId(tenantId);
        refreshToken.setExpiresAt(OffsetDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiry()));
        refreshToken.setInvalidated(false);
        refreshToken.setCreatedAt(OffsetDateTime.now());

        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, refreshTokenValue, jwtService.getAccessTokenExpiry());
    }

    private List<String> resolvePermissions(List<RoleType> roles, UUID tenantId) {
        // Load all role configs for this tenant in one query
        List<TenantRoleConfig> configs = tenantRoleConfigRepository
            .findAllByTenantId(tenantId);

        // Build a map of role name → permission list
        Map<RoleType, List<String>> permissionsByRole = configs.stream()
            .collect(Collectors.toMap(
                TenantRoleConfig::getRoleName,
                TenantRoleConfig::getPermissions
            ));

        // Union the permission sets across all of the user's roles
        // LinkedHashSet preserves order and deduplicates
        Set<String> resolved = new LinkedHashSet<>();
        for (RoleType role : roles) {
            List<String> rolePermissions = permissionsByRole.get(role);
            if (rolePermissions != null) {
                resolved.addAll(rolePermissions);
            }
        }

        return new ArrayList<>(resolved);
    }
}