package com.osporo.engine.auth;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.exception.InvalidTokenException;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.shared.exception.TenantSuspendedException;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import com.osporo.engine.user.UserRepository;
import com.osporo.engine.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void requireActiveAccount_shouldScopeUserLookupAndCheckTenant() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setTenantId(tenantId);
        Tenant tenant = new Tenant();
        tenant.setStatus(TenantStatus.ACTIVE);
        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThatCode(() -> service().requireActiveAccount(userId, tenantId)).doesNotThrowAnyException();

        verify(userRepository).findByIdAndTenantId(userId, tenantId);
        verify(tenantRepository).findById(tenantId);
        verifyNoMoreInteractions(userRepository, tenantRepository);
    }

    @Test
    void requireActiveAccount_shouldRejectMissingOrOutOfTenantUser() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().requireActiveAccount(userId, tenantId))
                .isInstanceOf(InvalidTokenException.class);

        verifyNoInteractions(tenantRepository);
    }

    @ParameterizedTest
    @CsvSource({"DELETED, INVALID_TOKEN", "SUSPENDED, ACCOUNT_SUSPENDED"})
    void requireActiveAccount_shouldRejectInactiveAccountsBeforeTenantLookup(String state, ErrorCode code) {
        User user = new User();

        if (state.equals("DELETED")) {
            user.setDeletedAt(OffsetDateTime.now());
        } else {
            user.setSuspendedAt(OffsetDateTime.now());
        }

        assertThatThrownBy(() -> service().requireActiveAccount(user))
                .isInstanceOfSatisfying(OsporoException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(code));

        verifyNoInteractions(userRepository, tenantRepository);
    }

    @Test
    void requireActiveAccount_shouldRejectInactiveTenant() {
        User user = new User();
        user.setTenantId(UUID.randomUUID());
        Tenant tenant = new Tenant();
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(user.getTenantId())).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service().requireActiveAccount(user))
                .isInstanceOf(TenantSuspendedException.class);
    }

    @Test
    void requireActiveAccount_shouldRejectMissingTenant() {
        User user = new User();
        user.setTenantId(UUID.randomUUID());
        when(tenantRepository.findById(user.getTenantId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().requireActiveAccount(user))
                .isInstanceOf(InvalidTokenException.class);
    }

    private AccountStatusService service() {
        return new AccountStatusService(userRepository, tenantRepository);
    }
}
