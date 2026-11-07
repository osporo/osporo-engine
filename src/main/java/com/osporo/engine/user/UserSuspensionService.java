package com.osporo.engine.user;

import com.osporo.engine.auth.AccountStatusService;
import com.osporo.engine.auth.AuthService;
import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.InvalidTokenException;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.user.dto.SuspendUserRequest;
import com.osporo.engine.user.dto.UserSuspensionResponse;
import com.osporo.engine.user.dto.UserUnsuspensionResponse;
import com.osporo.engine.user.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserSuspensionService {

    private final UserRepository userRepository;
    private final UserSuspensionRepository suspensionRepository;
    private final UserAuthorizationPolicy authorizationPolicy;
    private final AccountStatusService accountStatusService;
    private final AuthService authService;

    public UserSuspensionService(
            UserRepository userRepository,
            UserSuspensionRepository suspensionRepository,
            UserAuthorizationPolicy authorizationPolicy,
            AccountStatusService accountStatusService,
            AuthService authService
    ) {
        this.userRepository = userRepository;
        this.suspensionRepository = suspensionRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.accountStatusService = accountStatusService;
        this.authService = authService;
    }

    @Transactional
    public UserSuspensionResponse suspend(OsporoPrincipal principal, UUID targetId, SuspendUserRequest request) {
        authorizationPolicy.requireSuspensionPermission(principal);
        request.validate();

        User target = requireEligibleTarget(principal, targetId);
        if (target.isSuspended()) {
            throw new OsporoException(
                    ErrorCode.USER_ALREADY_SUSPENDED, HttpStatus.CONFLICT, "The user is already suspended."
            );
        }

        OffsetDateTime suspendedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        target.setSuspendedAt(suspendedAt);
        target.setUpdatedAt(suspendedAt);

        UUID tenantId = principal.getTenantId();
        UUID actorId = principal.getUserId();
        String violationType = request.violationTypeText();
        String details = request.detailsText();
        UUID actionId = suspensionRepository.recordSuspension(
                tenantId, targetId, actorId, violationType, details, suspendedAt
        );
        authService.revokeRefreshTokens(targetId, tenantId);

        return new UserSuspensionResponse(actionId, targetId, actorId, "SUSPEND", violationType, details, suspendedAt);
    }

    @Transactional
    public UserUnsuspensionResponse unsuspend(OsporoPrincipal principal, UUID targetId, String details) {
        authorizationPolicy.requireSuspensionPermission(principal);

        User target = requireEligibleTarget(principal, targetId);
        if (!target.isSuspended()) {
            throw new OsporoException(
                    ErrorCode.USER_NOT_SUSPENDED, HttpStatus.CONFLICT, "The user is not suspended."
            );
        }

        UUID tenantId = principal.getTenantId();
        UUID actorId = principal.getUserId();
        UserUnsuspensionResponse.Suspension suspension = suspensionRepository
                .findSuspension(tenantId, targetId, target.getSuspendedAt())
                .map(record -> new UserUnsuspensionResponse.Suspension(
                        record.id(), record.actorId(), record.violationType(), record.details(), record.actionDate()
                ))
                .orElse(null);

        OffsetDateTime actionDate = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String auditDetails = Objects.requireNonNullElse(details, "");
        UUID suspensionId = suspension == null ? null : suspension.id();
        UUID actionId = suspensionRepository.recordLift(tenantId, targetId, actorId, auditDetails, actionDate, suspensionId);

        target.setSuspendedAt(null);
        target.setUpdatedAt(actionDate);
        authService.revokeRefreshTokens(targetId, tenantId);

        return new UserUnsuspensionResponse(actionId, targetId, actorId, "UPLIFT", auditDetails, actionDate, suspension);
    }

    private User requireEligibleTarget(OsporoPrincipal principal, UUID targetId) {
        UUID tenantId = principal.getTenantId();
        UUID actorId = principal.getUserId();
        List<User> users = userRepository.lockUsersInTenant(tenantId, List.of(actorId, targetId));

        User actor = users.stream()
                .filter(user -> user.getId().equals(actorId))
                .findFirst()
                .orElseThrow(InvalidTokenException::new);
        accountStatusService.requireActiveAccount(actor);

        User target = users.stream()
                .filter(user -> user.getId().equals(targetId))
                .filter(user -> !user.isDeleted() && !user.hasRole(RoleType.OSPORO_ADMIN))
                .findFirst()
                .orElseThrow(ProfileNotFoundException::new);

        authorizationPolicy.requireLowerSuspensionRank(actor, target);

        return target;
    }
}
