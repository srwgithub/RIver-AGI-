package com.river.agi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("API 响应测试")
class ApiResponseTest {

    @Test
    @DisplayName("ok - 带数据")
    void ok_withData() {
        ApiResponse<String> response = ApiResponse.ok("data");

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("data", response.getData());
        assertNotNull(response.getTraceId());
    }

    @Test
    @DisplayName("ok - 带 traceId")
    void ok_withTraceId() {
        ApiResponse<String> response = ApiResponse.ok("data", "trace-123");

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("data", response.getData());
        assertEquals("trace-123", response.getTraceId());
    }

    @Test
    @DisplayName("ok - null 数据")
    void ok_nullData() {
        ApiResponse<Object> response = ApiResponse.ok(null);

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("error - 仅消息")
    void error_message() {
        ApiResponse<Void> response = ApiResponse.error("something went wrong");

        assertEquals(400, response.getCode());
        assertEquals("something went wrong", response.getMessage());
        assertNull(response.getData());
        assertNotNull(response.getTraceId());
    }

    @Test
    @DisplayName("error - 带状态码和消息")
    void error_codeAndMessage() {
        ApiResponse<Void> response = ApiResponse.error(500, "server error");

        assertEquals(500, response.getCode());
        assertEquals("server error", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("error - 带消息和 traceId")
    void error_messageAndTraceId() {
        ApiResponse<Void> response = ApiResponse.error("bad request", "trace-456");

        assertEquals(400, response.getCode());
        assertEquals("bad request", response.getMessage());
        assertEquals("trace-456", response.getTraceId());
    }

    @Test
    @DisplayName("构造器 - 所有字段")
    void constructor_allFields() {
        ApiResponse<String> response = new ApiResponse<>(201, "created", "result", "trace-789");

        assertEquals(201, response.getCode());
        assertEquals("created", response.getMessage());
        assertEquals("result", response.getData());
        assertEquals("trace-789", response.getTraceId());
    }
}
