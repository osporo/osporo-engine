package com.osporo.engine.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.osporo.engine.shared.exception.RequestValidationException;
import com.osporo.engine.shared.response.ErrorDetail;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdateUserProfileRequest {

    private static final Set<String> SUPPORTED_FIELDS = Set.of("display_name", "avatar_url");

    private final Map<String, JsonNode> fields;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public UpdateUserProfileRequest(Map<String, JsonNode> fields) {
        this.fields = Map.copyOf(fields);
    }

    public boolean hasDisplayName() {
        return fields.containsKey("display_name");
    }

    public String getDisplayName() {
        return fields.get("display_name").asString().strip();
    }

    public boolean hasAvatarUrl() {
        return fields.containsKey("avatar_url");
    }

    public String getAvatarUrl() {
        JsonNode value = fields.get("avatar_url");

        return value.isNull() ? null : value.asString();
    }

    public void validate() {
        List<ErrorDetail> errors = new ArrayList<>();

        for (String field : fields.keySet()) {
            if (!SUPPORTED_FIELDS.contains(field)) {
                errors.add(new ErrorDetail(field, "This field cannot be updated."));
            }
        }

        if (hasDisplayName()) {
            JsonNode value = fields.get("display_name");

            if (!value.isString() || value.asString().strip().isBlank()) {
                errors.add(new ErrorDetail("display_name", "Must be a nonblank string."));
            } else if (value.asString().strip().length() > 100) {
                errors.add(new ErrorDetail(
                        "display_name", "Must be at most 100 characters after trimming."));
            }
        }

        if (hasAvatarUrl()) {
            JsonNode value = fields.get("avatar_url");

            if (!value.isNull() && (!value.isString() || !isValidAvatarUrl(value.asString()))) {
                errors.add(new ErrorDetail(
                        "avatar_url", "Must be null or an absolute HTTPS URL of at most 2048 characters."));
            }
        }

        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
    }

    private boolean isValidAvatarUrl(String value) {
        if (value.length() > 2048) {
            return false;
        }

        try {
            URI uri = URI.create(value);

            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
