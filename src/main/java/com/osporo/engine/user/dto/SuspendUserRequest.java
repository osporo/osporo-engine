package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.osporo.engine.shared.exception.RequestValidationException;
import com.osporo.engine.shared.response.ErrorDetail;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public record SuspendUserRequest(
        @JsonProperty("violation_type") JsonNode violationType,
        JsonNode details
) {
    public void validate() {
        List<ErrorDetail> errors = new ArrayList<>();

        String type = isText(violationType) ? violationTypeText() : "";
        if (type.isBlank() || type.codePointCount(0, type.length()) > 500) {
            errors.add(new ErrorDetail(
                    "violation_type", "Must be a nonblank string of at most 500 characters without NUL characters."
            ));
        }

        if (details != null && !details.isNull() && !isText(details)) {
            errors.add(new ErrorDetail("details", "Must be a string or null and cannot contain NUL characters."));
        }

        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
    }

    public String violationTypeText() {
        return violationType.asString().strip();
    }

    public String detailsText() {
        return details == null || details.isNull() ? "" : details.asString();
    }

    private boolean isText(JsonNode value) {
        return value != null && value.isString() && value.asString().indexOf('\0') < 0;
    }
}
