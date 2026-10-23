package com.osporo.engine.user;

import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.ProfileNotFoundException;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.user.model.User;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAuthorizationPolicyTest {

    private final UserAuthorizationPolicy policy = new UserAuthorizationPolicy();

    @ParameterizedTest
    @CsvSource({
            "SELLER, true, true, true",
            "BUYER, false, true, true",
            "MARKETPLACE_STAFF, false, false, true",
            "MARKETPLACE_OWNER, false, false, true",
            "MARKETPLACE_STAFF+SELLER, true, true, true",
            "MARKETPLACE_OWNER+BUYER, false, true, true",
            "BUYER+SELLER, true, true, true",
            "OSPORO_ADMIN+SELLER, false, false, false"
    })
    void activeProfiles_shouldFollowPermissionMatrix(
            String roles,
            boolean guestVisible,
            boolean publicVisible,
            boolean fullVisible
    ) {
        User target = targetWithRoles(roles);

        assertVisibility(target, null, guestVisible);
        assertVisibility(target, caller(List.of()), guestVisible);
        assertVisibility(target, caller(List.of("PLATFORM_ADMIN")), guestVisible);
        assertVisibility(target, caller(List.of("USER_READ_PUBLIC")), publicVisible);
        assertVisibility(target, caller(List.of("USER_READ_ANY")), fullVisible);
        assertVisibility(target, caller(List.of("USER_READ_PUBLIC", "USER_READ_ANY")), fullVisible);
    }

    @ParameterizedTest
    @CsvSource({"SELLER", "BUYER", "MARKETPLACE_STAFF+SELLER", "OSPORO_ADMIN+SELLER"})
    void deletedProfiles_shouldBeHiddenFromEveryCaller(String roles) {
        User target = targetWithRoles(roles);
        target.setDeletedAt(OffsetDateTime.now());

        assertVisibility(target, null, false);
        assertVisibility(target, caller(List.of("USER_READ_PUBLIC")), false);
        assertVisibility(target, caller(List.of("USER_READ_ANY")), false);
    }

    @ParameterizedTest
    @CsvSource({
            "SELLER, true",
            "BUYER, true",
            "MARKETPLACE_STAFF, true",
            "MARKETPLACE_OWNER+SELLER, true",
            "OSPORO_ADMIN+SELLER, false"
    })
    void suspendedProfiles_shouldRequireFullReadPermission(String roles, boolean fullVisible) {
        User target = targetWithRoles(roles);
        target.setSuspendedAt(OffsetDateTime.now());

        assertVisibility(target, null, false);
        assertVisibility(target, caller(List.of()), false);
        assertVisibility(target, caller(List.of("USER_READ_PUBLIC")), false);
        assertVisibility(target, caller(List.of("USER_READ_ANY")), fullVisible);
    }

    private void assertVisibility(User target, OsporoPrincipal principal, boolean expected) {
        if (expected) {
            assertThatCode(() -> policy.requireVisibleProfile(target, principal)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.requireVisibleProfile(target, principal))
                    .isInstanceOf(ProfileNotFoundException.class);
        }

        boolean fullRead = principal != null && principal.getPermissions().contains("USER_READ_ANY");
        assertThat(policy.canReadFullProfile(principal)).isEqualTo(fullRead);
    }

    private User targetWithRoles(String roles) {
        User target = new User();
        target.setRoles(Arrays.stream(roles.split("\\+")).map(RoleType::valueOf).toList());

        return target;
    }

    private OsporoPrincipal caller(List<String> permissions) {
        return new OsporoPrincipal(UUID.randomUUID(), UUID.randomUUID(), permissions);
    }
}
