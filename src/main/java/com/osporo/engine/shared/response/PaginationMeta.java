package com.osporo.engine.shared.response;

// Pagination details — null when not a list response
public record PaginationMeta(
        int page,
        int perPage,
        long total,
        int totalPages
) {}
