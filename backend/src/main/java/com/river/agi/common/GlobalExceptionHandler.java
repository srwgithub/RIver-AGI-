package com.river.agi.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private String getTraceId(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("Business exception: {}", e.getMessage());
        String traceId = getTraceId(request);
        int code = e.getCode() > 0 ? e.getCode() : ErrorCode.BAD_REQUEST;
        return ResponseEntity.status(HttpStatus.valueOf(Math.min(code, 599)))
                .body(new ApiResponse<>(code, e.getMessage(), null, traceId));
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e, HttpServletRequest request) {
        log.error("Authentication failed: {}", e.getMessage());
        String traceId = getTraceId(request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(ErrorCode.UNAUTHORIZED, "Unauthorized", null, traceId));
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException e, HttpServletRequest request) {
        log.error("Bad credentials: {}", e.getMessage());
        String traceId = getTraceId(request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(ErrorCode.UNAUTHORIZED, "Invalid username or password", null, traceId));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.error("Access denied: {}", e.getMessage());
        String traceId = getTraceId(request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(ErrorCode.FORBIDDEN, "Access denied", null, traceId));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.error("Validation failed: {}", errors);
        String traceId = getTraceId(request);
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(ErrorCode.BAD_REQUEST, "Validation failed", errors, traceId));
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParamException(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.error("Missing parameter: {}", e.getParameterName());
        String traceId = getTraceId(request);
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(ErrorCode.BAD_REQUEST, "Missing required parameter: " + e.getParameterName(), null, traceId));
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.error("File size exceeded: {}", e.getMessage());
        String traceId = getTraceId(request);
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(ErrorCode.BAD_REQUEST, "File size exceeds maximum limit", null, traceId));
    }
    
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(NoResourceFoundException e, HttpServletRequest request) {
        String traceId = getTraceId(request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(ErrorCode.NOT_FOUND, "Resource not found", null, traceId));
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.error("Invalid argument: {}", e.getMessage());
        String traceId = getTraceId(request);
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(ErrorCode.BAD_REQUEST, e.getMessage(), null, traceId));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error: ", e);
        String traceId = getTraceId(request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(ErrorCode.INTERNAL_SERVER_ERROR, "Internal server error", null, traceId));
    }
}
