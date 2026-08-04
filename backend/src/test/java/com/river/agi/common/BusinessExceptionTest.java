package com.river.agi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("业务异常测试")
class BusinessExceptionTest {

    @Test
    @DisplayName("构造器 - 仅消息")
    void constructor_message() {
        BusinessException exception = new BusinessException("error message");

        assertEquals("error message", exception.getMessage());
        assertEquals(400, exception.getCode());
    }

    @Test
    @DisplayName("构造器 - 带状态码和消息")
    void constructor_codeAndMessage() {
        BusinessException exception = new BusinessException(404, "not found");

        assertEquals("not found", exception.getMessage());
        assertEquals(404, exception.getCode());
    }

    @Test
    @DisplayName("构造器 - 500 状态码")
    void constructor_serverError() {
        BusinessException exception = new BusinessException(500, "server error");

        assertEquals(500, exception.getCode());
        assertEquals("server error", exception.getMessage());
    }

    @Test
    @DisplayName("是 RuntimeException 子类")
    void isRuntimeException() {
        BusinessException exception = new BusinessException("test");

        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    @DisplayName("可以被 try-catch 捕获")
    void canBeCaught() {
        assertThrows(BusinessException.class, () -> {
            throw new BusinessException("caught");
        });
    }
}
