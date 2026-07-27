package com.example.demo.savings.api;

public record ErrorResponse(
        ErrorBody error
) {
    public record ErrorBody(
            String code,
            String message,
            String field
    ) {
    }
}

