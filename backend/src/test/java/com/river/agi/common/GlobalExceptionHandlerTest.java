package com.river.agi.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleBusinessException - 返回正确状态码")
    void handleBusinessException() {
        BusinessException e = new BusinessException(404, "not found");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn("trace-1");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(e, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getCode());
        assertEquals("not found", response.getBody().getMessage());
        assertEquals("trace-1", response.getBody().getTraceId());
    }

    @Test
    @DisplayName("handleBusinessException - 默认状态码 400")
    void handleBusinessException_defaultCode() {
        BusinessException e = new BusinessException("error");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(e, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertNotNull(response.getBody().getTraceId());
    }

    @Test
    @DisplayName("handleBusinessException - 高状态码 503 正确返回")
    void handleBusinessException_highCode() {
        BusinessException e = new BusinessException(503, "unavailable");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(e, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, response.getBody().getCode());
    }

    @Test
    @DisplayName("handleAuthenticationException - 返回 401")
    void handleAuthenticationException() {
        AuthenticationException e = new AuthenticationException("auth failed") {};
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn("trace-2");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthenticationException(e, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ErrorCode.UNAUTHORIZED, response.getBody().getCode());
        assertEquals("Unauthorized", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleBadCredentialsException - 返回 401")
    void handleBadCredentialsException() {
        BadCredentialsException e = new BadCredentialsException("bad creds");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBadCredentialsException(e, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid username or password", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleAccessDeniedException - 返回 403")
    void handleAccessDeniedException() {
        AccessDeniedException e = new AccessDeniedException("denied");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(e, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleValidationException - 返回 400 和字段错误")
    void handleValidationException() {
        MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        FieldError fieldError = new FieldError("object", "fieldName", "must not be null");
        when(e.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(java.util.List.of(fieldError));
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidationException(e, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals("must not be null", response.getBody().getData().get("fieldName"));
    }

    @Test
    @DisplayName("handleMissingParamException - 返回 400")
    void handleMissingParamException() {
        MissingServletRequestParameterException e = new MissingServletRequestParameterException("param", "String");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParamException(e, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("param"));
    }

    @Test
    @DisplayName("handleMaxUploadSizeExceededException - 返回 400")
    void handleMaxUploadSizeExceededException() {
        MaxUploadSizeExceededException e = new MaxUploadSizeExceededException(1024);
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSizeExceededException(e, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("File size exceeds maximum limit", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleNotFoundException - 返回 404")
    void handleNotFoundException() {
        NoResourceFoundException e = mock(NoResourceFoundException.class);
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFoundException(e, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Resource not found", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleIllegalArgumentException - 返回 400")
    void handleIllegalArgumentException() {
        IllegalArgumentException e = new IllegalArgumentException("bad arg");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(e, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad arg", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleGenericException - 返回 500")
    void handleGenericException() {
        Exception e = new RuntimeException("unexpected");
        when(request.getAttribute(RequestTraceInterceptor.TRACE_ID_ATTRIBUTE)).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(e, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().getMessage());
    }
}
