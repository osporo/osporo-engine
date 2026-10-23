package com.osporo.engine.user;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.user.dto.UserDirectoryQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDirectoryQueryTest {

    @Test
    void defaults_shouldUseTenantRolesAndExcludeDeletedAccounts() {
        UserDirectoryQuery query = UserDirectoryQuery.from(new LinkedMultiValueMap<>());

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.perPage()).isEqualTo(20);
        assertThat(query.sort()).isEqualTo("CREATED_AT");
        assertThat(query.order()).isEqualTo("desc");
        assertThat(query.roles()).containsExactly(
                RoleType.BUYER, RoleType.SELLER, RoleType.MARKETPLACE_STAFF, RoleType.MARKETPLACE_OWNER);
        assertThat(query.status()).isNull();
    }

    @Test
    void roles_shouldAcceptRepeatedValuesAndRemoveDuplicates() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("roles", "BUYER");
        parameters.add("roles", "SELLER");
        parameters.add("roles", "BUYER");

        assertThat(UserDirectoryQuery.from(parameters).roles())
                .containsExactly(RoleType.BUYER, RoleType.SELLER);
    }

    @ParameterizedTest
    @MethodSource("invalidParameters")
    void invalidParameters_shouldReturnStableClientError(String name, String value) {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add(name, value);

        assertThatThrownBy(() -> UserDirectoryQuery.from(parameters))
                .isInstanceOfSatisfying(OsporoException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER);
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    static Stream<Arguments> invalidParameters() {
        return Stream.of(
                Arguments.of("page", "0"),
                Arguments.of("page", "-1"),
                Arguments.of("page", "2147483648"),
                Arguments.of("page", "1.5"),
                Arguments.of("page", ""),
                Arguments.of("per_page", "0"),
                Arguments.of("per_page", "101"),
                Arguments.of("per_page", "abc"),
                Arguments.of("sort", "email"),
                Arguments.of("sort", "CREATED_AT; DROP TABLE users"),
                Arguments.of("order", "desc; DROP TABLE users"),
                Arguments.of("status", "UNKNOWN"),
                Arguments.of("status", ""),
                Arguments.of("roles", "OSPORO_ADMIN"),
                Arguments.of("roles", "UNKNOWN"),
                Arguments.of("roles", "BUYER,SELLER"),
                Arguments.of("roles", ""),
                Arguments.of("email", "person@example.com"),
                Arguments.of("tenant_id", "another-tenant")
        );
    }

    @Test
    void singleValueParameters_shouldRejectAmbiguousRepeatedValues() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("status", "ACTIVE");
        parameters.add("status", "ALL");

        assertThatThrownBy(() -> UserDirectoryQuery.from(parameters))
                .isInstanceOf(OsporoException.class);
    }
}
