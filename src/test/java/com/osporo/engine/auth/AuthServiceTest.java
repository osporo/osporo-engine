package com.osporo.engine.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.osporo.engine.auth.dto.LogoutRequest;
import com.osporo.engine.shared.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

import com.osporo.engine.auth.dto.LoginRequest;
import com.osporo.engine.auth.dto.RefreshRequest;
import com.osporo.engine.auth.dto.RegisterRequest;
import com.osporo.engine.auth.model.RefreshToken;
import com.osporo.engine.shared.enums.Permission;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.TenantRoleConfigRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.tenant.model.TenantRoleConfig;
import com.osporo.engine.user.UserRepository;
import com.osporo.engine.user.model.User;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantRoleConfigRepository tenantRoleConfigRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantContextHolder tenantContextHolder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void register_shouldCreateUser_whenEmailIsUnique() {
        // Arrange
        String email = "john@gmail.com";
        String password = "password123";

        RegisterRequest request = new RegisterRequest(email, password);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        TenantRoleConfig defaultRole = new TenantRoleConfig();
        defaultRole.setRoleName(RoleType.BUYER);

        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByEmailAndTenantId(email, tenantId)).thenReturn(false);
        when(tenantRoleConfigRepository.findByTenantIdAndIsDefaultTrue(tenantId)).thenReturn(Optional.of(defaultRole));
        when(passwordEncoder.encode(password)).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = authService.register(request);

        // Assert
        assertThat(result.getEmail()).isEqualTo("john@gmail.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed_password");
        assertThat(result.getRoles()).containsExactly(RoleType.BUYER);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrowEmailAlreadyExists_whenEmailIsTaken() {
        // Arrange
        String email = "john@gmail.com";
        String password = "password123";

        RegisterRequest registerRequest = new RegisterRequest(email, password);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByEmailAndTenantId(email, tenantId)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> authService.register(registerRequest)).isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrowTenantNotFound_whenTenantDoesNotExist() {
        
        // Arrange
        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        RegisterRequest request = new RegisterRequest("user@gmail.com", "password123");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(TenantNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrowTenantSuspended_whenTenantIsSuspended() {

        // Arrange
        RegisterRequest request = new RegisterRequest("user@gmail.com", "password123");

        Tenant tenant = new Tenant();
        tenant.setStatus(TenantStatus.SUSPENDED);

        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        // Act + Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(TenantSuspendedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_shouldReturnTokenPair_whenCredentialsAreValid() {
        
        // Arrange
        String email = "john@gmail.com";
        String password = "password123";

        LoginRequest request = new LoginRequest(email, password);

        List<Permission> buyerPermissions = List.of(Permission.LISTING_READ, Permission.ORDER_CREATE);

        TenantRoleConfig buyerRoleConfig = new TenantRoleConfig();
        buyerRoleConfig.setRoleName(RoleType.BUYER);
        buyerRoleConfig.setPermissions(buyerPermissions);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRoles(List.of(RoleType.BUYER));
        user.setPasswordHash("hashed_password");

        // -- login()
        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        when(userRepository.findByEmailAndTenantId(email, tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(true);

        // -- issueTokenPair()
        when(tenantRoleConfigRepository.findAllByTenantId(tenantId)).thenReturn(List.of(buyerRoleConfig));
        when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("random_access_token");
        when(jwtService.generateRefreshToken()).thenReturn("random_refresh_token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpiry()).thenReturn(2592000L);
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TokenPair result = authService.login(request);

        // Assert
        assertThat(result.accessToken()).isEqualTo("random_access_token");
        assertThat(result.refreshToken()).isEqualTo("random_refresh_token");
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    void login_shouldThrowAuthenticationFailed_whenEmailIsInvalid() {

        // Arrange
        String email = "john@gmail.com";
        String password = "password123";

        LoginRequest request = new LoginRequest(email, password);

        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        when(userRepository.findByEmailAndTenantId(email, tenantId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AuthenticationFailedException.class);
        verify(jwtService, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void login_shouldThrowAuthenticationFailed_whenPasswordIsInvalid() {

        // Arrange
        String email = "john@gmail.com";
        String password = "invalid_password";

        LoginRequest request = new LoginRequest(email, password);
        User user = new User();
        user.setPasswordHash("hashed_password");

        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        when(userRepository.findByEmailAndTenantId(email, tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AuthenticationFailedException.class);
        verify(jwtService, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void login_shouldThrowAuthenticationFailed_whenAccountIsSuspended() {

        // Arrange
        String email = "john@gmail.com";
        String password = "password123";

        LoginRequest request = new LoginRequest(email, password);
        User user = new User();
        user.setSuspendedAt(OffsetDateTime.now());
        user.setPasswordHash("hashed_password");

        when(tenantContextHolder.getTenantId()).thenReturn(tenantId);
        when(userRepository.findByEmailAndTenantId(email, tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AccountSuspendedException.class);
        verify(jwtService, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void refresh_shouldReturnNewTokenPair_whenRefreshTokenIsValid() {
        
        // Arrange
        OffsetDateTime currentDateTime = OffsetDateTime.now();

        RefreshRequest request = new RefreshRequest("old_refresh_token");

        RefreshToken oldRefreshToken = new RefreshToken();
        oldRefreshToken.setUserId(UUID.randomUUID());
        oldRefreshToken.setExpiresAt(currentDateTime.plusDays(15));
        oldRefreshToken.setTenantId(tenantId);

        User user = new User();
        user.setRoles(List.of(RoleType.BUYER));
        user.setId(oldRefreshToken.getUserId());

        List<Permission> buyerPermissions = List.of(Permission.LISTING_READ, Permission.ORDER_CREATE);

        TenantRoleConfig buyerRoleConfig = new TenantRoleConfig();
        buyerRoleConfig.setRoleName(RoleType.BUYER);
        buyerRoleConfig.setPermissions(buyerPermissions);

        when(refreshTokenRepository.findByToken(request.refreshToken())).thenReturn(Optional.of(oldRefreshToken));
        when(userRepository.findByIdAndTenantId(oldRefreshToken.getUserId(), tenantId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // -- issueTokenPair()
        when(tenantRoleConfigRepository.findAllByTenantId(tenantId)).thenReturn(List.of(buyerRoleConfig));
        when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("new_access_token");
        when(jwtService.generateRefreshToken()).thenReturn("new_refresh_token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpiry()).thenReturn(2592000L);

        // Act
        TokenPair result = authService.refresh(request);

        // Assert
        assertThat(result.accessToken()).isEqualTo("new_access_token");
        assertThat(result.refreshToken()).isEqualTo("new_refresh_token");
        assertThat(result.expiresIn()).isEqualTo(900L);
        assertThat(oldRefreshToken.isInvalidated()).isTrue();
    }

    @Test
    void refresh_shouldThrowInvalidToken_whenInvalidated() {

        // Arrange
        RefreshRequest request = new RefreshRequest("random_refresh_token");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setInvalidated(true);

        when(refreshTokenRepository.findByToken(request.refreshToken())).thenReturn(Optional.of(refreshToken));
        
        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_shouldThrowInvalidToken_whenTokenIsExpired() {

        // Arrange
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        
        RefreshRequest request = new RefreshRequest("random_refresh_token");
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setExpiresAt(currentDateTime.minusDays(1));

        when(refreshTokenRepository.findByToken(request.refreshToken())).thenReturn(Optional.of(refreshToken));
        
        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_shouldThrowInvalidToken_whenUserIsNotFound() {

        // Arrange
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        
        RefreshRequest request = new RefreshRequest("random_refresh_token");
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTenantId(tenantId);
        refreshToken.setUserId(UUID.randomUUID());
        refreshToken.setExpiresAt(currentDateTime.plusMinutes(15));

        when(refreshTokenRepository.findByToken(request.refreshToken())).thenReturn(Optional.of(refreshToken));
        when(userRepository.findByIdAndTenantId(refreshToken.getUserId(), refreshToken.getTenantId())).thenReturn(Optional.empty());
        
        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_shouldThrowAccountSuspend_whenUserIsSuspended() {

        // Arrange
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        
        RefreshRequest request = new RefreshRequest("random_refresh_token");
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTenantId(tenantId);
        refreshToken.setUserId(UUID.randomUUID());
        refreshToken.setExpiresAt(currentDateTime.plusMinutes(15));

        User user = new User();
        user.setSuspendedAt(currentDateTime);

        when(refreshTokenRepository.findByToken(request.refreshToken())).thenReturn(Optional.of(refreshToken));
        when(userRepository.findByIdAndTenantId(refreshToken.getUserId(), refreshToken.getTenantId())).thenReturn(Optional.of(user));
        
        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(AccountSuspendedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logout_shouldInvalidateToken() {

        // Arrange
        LogoutRequest request = new LogoutRequest("random_refresh_token");

        RefreshToken refreshToken = new RefreshToken();
        when(refreshTokenRepository.findByToken(request.refreshToken())).thenReturn(Optional.of(refreshToken));

        // Act
        authService.logout(request);

        // Assert
        assertThat(refreshToken.isInvalidated()).isTrue();
        verify(refreshTokenRepository).save(any());
    }
}
