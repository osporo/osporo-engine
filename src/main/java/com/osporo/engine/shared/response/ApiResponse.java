package com.osporo.engine.shared.response;

public record ApiResponse<T>(T data, Meta meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Meta.simple());
    }

    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }
}
