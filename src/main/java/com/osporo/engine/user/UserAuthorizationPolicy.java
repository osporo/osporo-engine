package com.osporo.engine.user;

import com.osporo.engine.shared.enums.Permission;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.exception.InsufficientPermissionsException;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.user.model.User;
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

    private boolean hasPermission(OsporoPrincipal principal, Permission permission) {
        return principal != null && principal.getPermissions().contains(permission.name());
    }
}
