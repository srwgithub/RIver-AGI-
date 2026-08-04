package com.river.agi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("错误码测试")
class ErrorCodeTest {

    @Test
    @DisplayName("getMessage - SUCCESS")
    void getMessage_success() {
        assertEquals("success", ErrorCode.getMessage(ErrorCode.SUCCESS));
    }

    @Test
    @DisplayName("getMessage - BAD_REQUEST")
    void getMessage_badRequest() {
        assertEquals("Bad request", ErrorCode.getMessage(ErrorCode.BAD_REQUEST));
    }

    @Test
    @DisplayName("getMessage - UNAUTHORIZED")
    void getMessage_unauthorized() {
        assertEquals("Unauthorized", ErrorCode.getMessage(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("getMessage - FORBIDDEN")
    void getMessage_forbidden() {
        assertEquals("Forbidden", ErrorCode.getMessage(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("getMessage - NOT_FOUND")
    void getMessage_notFound() {
        assertEquals("Not found", ErrorCode.getMessage(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("getMessage - INTERNAL_SERVER_ERROR")
    void getMessage_internalServerError() {
        assertEquals("Internal server error", ErrorCode.getMessage(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("getMessage - DATASET_NOT_FOUND")
    void getMessage_datasetNotFound() {
        assertEquals("Dataset not found", ErrorCode.getMessage(ErrorCode.DATASET_NOT_FOUND));
    }

    @Test
    @DisplayName("getMessage - DATASET_UPLOAD_FAILED")
    void getMessage_datasetUploadFailed() {
        assertEquals("Dataset upload failed", ErrorCode.getMessage(ErrorCode.DATASET_UPLOAD_FAILED));
    }

    @Test
    @DisplayName("getMessage - DATASET_PARSE_FAILED")
    void getMessage_datasetParseFailed() {
        assertEquals("Dataset parse failed", ErrorCode.getMessage(ErrorCode.DATASET_PARSE_FAILED));
    }

    @Test
    @DisplayName("getMessage - SECURITY_SCAN_FAILED")
    void getMessage_securityScanFailed() {
        assertEquals("Security scan failed", ErrorCode.getMessage(ErrorCode.SECURITY_SCAN_FAILED));
    }

    @Test
    @DisplayName("getMessage - RESOURCE_ACCESS_DENIED")
    void getMessage_resourceAccessDenied() {
        assertEquals("Access denied", ErrorCode.getMessage(ErrorCode.RESOURCE_ACCESS_DENIED));
    }

    @Test
    @DisplayName("getMessage - RESOURCE_NOT_FOUND")
    void getMessage_resourceNotFound() {
        assertEquals("Resource not found", ErrorCode.getMessage(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("getMessage - 未知错误码")
    void getMessage_unknownCode() {
        assertEquals("Unknown error", ErrorCode.getMessage(99999));
    }

    @Test
    @DisplayName("常量值 - 验证关键错误码")
    void constantValues() {
        assertEquals(200, ErrorCode.SUCCESS);
        assertEquals(400, ErrorCode.BAD_REQUEST);
        assertEquals(401, ErrorCode.UNAUTHORIZED);
        assertEquals(403, ErrorCode.FORBIDDEN);
        assertEquals(404, ErrorCode.NOT_FOUND);
        assertEquals(500, ErrorCode.INTERNAL_SERVER_ERROR);
        assertEquals(1001, ErrorCode.DATASET_NOT_FOUND);
        assertEquals(7001, ErrorCode.RESOURCE_ACCESS_DENIED);
    }
}
