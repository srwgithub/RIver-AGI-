package com.river.agi.common.aspect;

import com.river.agi.common.SecurityUtils;
import com.river.agi.common.annotation.EncryptField;
import lombok.Data;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("字段级加密切面测试")
class EncryptFieldAspectTest {

    @Mock
    private SecurityUtils securityUtils;

    private EncryptFieldAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new EncryptFieldAspect(securityUtils);
    }

    @Data
    static class SampleEntity {
        private Long id;
        @EncryptField
        private String phone;
        @EncryptField
        private String realName;
        private String username;
    }

    @Test
    @DisplayName("aroundWrite - 对标注字段加密")
    void aroundWrite_encryptsAnnotatedFields() throws Throwable {
        when(securityUtils.encrypt("13812348888")).thenReturn("ENC_PHONE");
        when(securityUtils.encrypt("张三")).thenReturn("ENC_NAME");

        SampleEntity entity = new SampleEntity();
        entity.setId(1L);
        entity.setPhone("13812348888");
        entity.setRealName("张三");
        entity.setUsername("zhangsan");

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[]{entity});
        when(jp.proceed()).thenReturn(null);

        aspect.aroundWrite(jp);

        assertEquals("ENC_PHONE", entity.getPhone());
        assertEquals("ENC_NAME", entity.getRealName());
        assertEquals("zhangsan", entity.getUsername());
        verify(jp).proceed();
    }

    @Test
    @DisplayName("aroundRead - 对返回值解密")
    void aroundRead_decryptsAnnotatedFields() throws Throwable {
        when(securityUtils.decrypt("ENC_PHONE")).thenReturn("13812348888");
        when(securityUtils.decrypt("ENC_NAME")).thenReturn("张三");

        SampleEntity entity = new SampleEntity();
        entity.setPhone("ENC_PHONE");
        entity.setRealName("ENC_NAME");

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.proceed()).thenReturn(entity);

        aspect.aroundRead(jp);

        assertEquals("13812348888", entity.getPhone());
        assertEquals("张三", entity.getRealName());
    }

    @Test
    @DisplayName("aroundWrite - 空字符串不加密")
    void aroundWrite_emptyStringSkipped() throws Throwable {
        SampleEntity entity = new SampleEntity();
        entity.setPhone("");

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[]{entity});
        when(jp.proceed()).thenReturn(null);

        aspect.aroundWrite(jp);

        assertEquals("", entity.getPhone());
        verify(securityUtils, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("aroundWrite - 集合参数逐个加密")
    void aroundWrite_collectionEncrypted() throws Throwable {
        when(securityUtils.encrypt(anyString())).thenReturn("ENC");

        SampleEntity e1 = new SampleEntity();
        e1.setPhone("111");
        SampleEntity e2 = new SampleEntity();
        e2.setPhone("222");

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[]{List.of(e1, e2)});
        when(jp.proceed()).thenReturn(null);

        aspect.aroundWrite(jp);

        assertEquals("ENC", e1.getPhone());
        assertEquals("ENC", e2.getPhone());
    }

    @Test
    @DisplayName("aroundRead - 解密失败保持原值不抛异常")
    void aroundRead_decryptFailureKeepsValue() throws Throwable {
        when(securityUtils.decrypt("BAD_CIPHER")).thenThrow(new RuntimeException("bad"));

        SampleEntity entity = new SampleEntity();
        entity.setPhone("BAD_CIPHER");

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.proceed()).thenReturn(entity);

        aspect.aroundRead(jp);

        assertEquals("BAD_CIPHER", entity.getPhone());
    }

    @Test
    @DisplayName("aroundRead - null 返回值安全处理")
    void aroundRead_nullHandled() throws Throwable {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.proceed()).thenReturn(null);
        assertDoesNotThrow(() -> aspect.aroundRead(jp));
    }

    @Test
    @DisplayName("aroundWrite - null 参数安全处理")
    void aroundWrite_nullHandled() throws Throwable {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[]{null});
        when(jp.proceed()).thenReturn(null);
        assertDoesNotThrow(() -> aspect.aroundWrite(jp));
    }

    @Test
    @DisplayName("aroundRead - 集合返回值逐个解密")
    void aroundRead_collectionDecrypted() throws Throwable {
        when(securityUtils.decrypt(anyString())).thenReturn("PLAIN");

        SampleEntity e1 = new SampleEntity();
        e1.setPhone("C1");
        SampleEntity e2 = new SampleEntity();
        e2.setPhone("C2");

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.proceed()).thenReturn(List.of(e1, e2));

        aspect.aroundRead(jp);

        assertEquals("PLAIN", e1.getPhone());
        assertEquals("PLAIN", e2.getPhone());
    }
}
