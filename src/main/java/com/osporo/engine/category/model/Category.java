package com.osporo.engine.category.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Category {

    private final UUID id;
    private final String name;
    private final String slug;
    private final boolean active;
    private final OffsetDateTime createdAt;
}
