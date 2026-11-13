package com.osporo.engine.category.dto;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.exception.OsporoException;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Set;

public record CategoryListQuery(int page, int perPage, Status status) {

    private static final Set<String> PARAMETERS = Set.of("page", "per_page", "status");

    public enum Status {
        ACTIVE,
        INACTIVE,
        ALL
    }

    public CategoryListQuery {
        if (page < 1) {
            throw invalidParameter("page");
        }
        if (perPage < 1 || perPage > 100) {
            throw invalidParameter("per_page");
        }
    }

    public static CategoryListQuery from(MultiValueMap<String, String> parameters) {
        for (String parameter : parameters.keySet()) {
            if (!PARAMETERS.contains(parameter)) {
                throw invalidParameter(parameter);
            }
        }

        Status status;
        try {
            status = Status.valueOf(singleParameter(parameters, "status", "ACTIVE"));
        } catch (IllegalArgumentException exception) {
            throw invalidParameter("status");
        }

        return new CategoryListQuery(
                integerParameter(parameters, "page", "1"),
                integerParameter(parameters, "per_page", "20"),
                status
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
                ErrorCode.INVALID_PARAMETER, HttpStatus.BAD_REQUEST,
                "The " + name + " parameter has an invalid or unsupported value."
        );
    }
}
