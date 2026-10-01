package com.example.demo.savings.api;

import com.example.demo.savings.service.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(
            ProductNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(new ErrorResponse.ErrorBody(
                        "PRODUCT_NOT_FOUND",
                        exception.getMessage(),
                        "productIds"
                ))
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String field = exception.getBindingResult().getFieldErrors().isEmpty()
                ? null
                : exception.getBindingResult().getFieldErrors().get(0).getField();
        return ResponseEntity.badRequest().body(
                new ErrorResponse(new ErrorResponse.ErrorBody(
                        "INVALID_PROFILE",
                        "요청값 검증에 실패했습니다.",
                        field
                ))
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
                new ErrorResponse(new ErrorResponse.ErrorBody(
                        "CONSTRAINT_VIOLATION",
                        exception.getMessage(),
                        null
                ))
        );
    }
}
