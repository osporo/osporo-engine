package com.osporo.engine.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[^\\x00]*", message = "Must not contain NUL characters.") String name,
        @JsonProperty("is_active") Boolean active
) {
    public CreateCategoryRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
