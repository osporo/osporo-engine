package com.osporo.engine.user;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.Permission;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.exception.InsufficientPermissionsException;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.user.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserAuthorizationPolicy {

    public boolean canReadFullProfile(OsporoPrincipal principal) {
        return hasPermission(principal, Permission.USER_READ_ANY);
    }

    public void requireDirectoryAccess(OsporoPrincipal principal) {
        if (!hasPermission(principal, Permission.USER_READ_ANY)) {
            throw new InsufficientPermissionsException();
        }
    }

    public void requireVisibleProfile(User target, OsporoPrincipal principal) {
        if (target.isDeleted() || target.hasRole(RoleType.OSPORO_ADMIN)) {
            throw new ProfileNotFoundException();
        }

        if (canReadFullProfile(principal)) {
            return;
        }

        if (target.isSuspended()) {
            throw new ProfileNotFoundException();
        }

        boolean publicSeller = target.hasRole(RoleType.SELLER);
        boolean publicBuyer = target.hasRole(RoleType.BUYER)
                && hasPermission(principal, Permission.USER_READ_PUBLIC);

        if (!publicSeller && !publicBuyer) {
            throw new ProfileNotFoundException();
        }
    }

    public void requireSuspensionPermission(OsporoPrincipal principal) {
        if (!hasPermission(principal, Permission.USER_SUSPEND)) {
            throw new InsufficientPermissionsException();
        }
    }

    public void requireLowerSuspensionRank(User actor, User target) {
        if (suspensionRank(actor) <= suspensionRank(target)) {
            throw new OsporoException(
                    ErrorCode.ROLE_HIERARCHY_VIOLATION,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "The target account must have a strictly lower role rank."
            );
        }
    }

    private int suspensionRank(User user) {
        return user.getRoles().stream()
                .mapToInt(role -> switch (role) {
                    case OSPORO_ADMIN -> 4;
                    case MARKETPLACE_OWNER -> 3;
                    case MARKETPLACE_STAFF -> 2;
                    case BUYER, SELLER -> 1;
                })
                .max()
                .orElse(1);
    }

    private boolean hasPermission(OsporoPrincipal principal, Permission permission) {
        return principal != null && principal.getPermissions().contains(permission.name());
    }
}
