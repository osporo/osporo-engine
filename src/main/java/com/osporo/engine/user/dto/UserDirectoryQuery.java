package com.osporo.engine.user.dto;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.shared.exception.OsporoException;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record UserDirectoryQuery(
        int page,
        int perPage,
        String sort,
        String order,
        List<RoleType> roles,
        String status
) {

    private static final Set<String> PARAMETERS = Set.of("page", "per_page", "sort", "order", "roles", "status");
    private static final Set<String> SORT_FIELDS = Set.of("CREATED_AT", "DELETED_AT", "UPDATED_AT", "SUSPENDED_AT");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "DELETED", "ALL");
    private static final List<RoleType> TENANT_ROLES = List.of(
            RoleType.BUYER, RoleType.SELLER, RoleType.MARKETPLACE_STAFF, RoleType.MARKETPLACE_OWNER);

    public UserDirectoryQuery {
        if (page < 1) {
            throw invalidParameter("page");
        }

        if (perPage < 1 || perPage > 100) {
            throw invalidParameter("per_page");
        }

        if (!SORT_FIELDS.contains(sort)) {
            throw invalidParameter("sort");
        }

        if (!Set.of("asc", "desc").contains(order)) {
            throw invalidParameter("order");
        }

        if (roles.isEmpty() || !TENANT_ROLES.containsAll(roles)) {
            throw invalidParameter("roles");
        }

        if (status != null && !STATUSES.contains(status)) {
            throw invalidParameter("status");
        }

        roles = List.copyOf(roles.stream().distinct().toList());
    }

    public static UserDirectoryQuery from(MultiValueMap<String, String> parameters) {
        for (String parameter : parameters.keySet()) {
            if (!PARAMETERS.contains(parameter)) {
                throw invalidParameter(parameter);
            }
        }

        List<RoleType> roles = TENANT_ROLES;

        if (parameters.containsKey("roles")) {
            try {
                roles = parameters.get("roles").stream().map(RoleType::valueOf).toList();
            } catch (IllegalArgumentException exception) {
                throw invalidParameter("roles");
            }
        }

        return new UserDirectoryQuery(
                integerParameter(parameters, "page", "1"),
                integerParameter(parameters, "per_page", "20"),
                singleParameter(parameters, "sort", "CREATED_AT"),
                singleParameter(parameters, "order", "desc").toLowerCase(Locale.ROOT),
                roles,
                singleParameter(parameters, "status", null)
        );
    }

    private static int integerParameter(MultiValueMap<String, String> parameters, String name, String fallback) {
        try {
            return Integer.parseInt(singleParameter(parameters, name, fallback));
        } catch (NumberFormatException exception) {
            throw invalidParameter(name);
        }
    }

    private static String singleParameter(MultiValueMap<String, String> parameters, String name, String fallback) {
        List<String> values = parameters.get(name);

        if (values == null) {
            return fallback;
        }

        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw invalidParameter(name);
        }

        return values.getFirst();
    }

    private static OsporoException invalidParameter(String name) {
        return new OsporoException(
                ErrorCode.INVALID_PARAMETER,
                HttpStatus.BAD_REQUEST,
                "The " + name + " parameter has an invalid or unsupported value."
        );
    }
}
