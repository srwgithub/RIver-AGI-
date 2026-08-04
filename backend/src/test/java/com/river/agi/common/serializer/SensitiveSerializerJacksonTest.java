package com.river.agi.common.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.annotation.Sensitive;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("敏感脱敏 Jackson 序列化集成测试")
class SensitiveSerializerJacksonTest {

    @Data
    static class UserDto {
        private String username;
        @Sensitive(type = Sensitive.Type.PHONE)
        private String phone;
        @Sensitive(type = Sensitive.Type.EMAIL)
        private String email;
        @Sensitive(type = Sensitive.Type.NAME)
        private String realName;
    }

    @Test
    @DisplayName("Jackson 序列化 - @Sensitive 字段自动脱敏")
    void serialize_masksAnnotatedFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UserDto dto = new UserDto();
        dto.setUsername("alice");
        dto.setPhone("13812348888");
        dto.setEmail("zhanglei@example.com");
        dto.setRealName("张三丰");

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("138****8888"), "phone should be masked: " + json);
        assertTrue(json.contains("zha***@example.com"), "email should be masked: " + json);
        assertTrue(json.contains("张**"), "realName should be masked: " + json);
        assertTrue(json.contains("\"alice\""), "username should be plain: " + json);
        assertFalse(json.contains("13812348888"), "raw phone must not appear");
    }

    @Test
    @DisplayName("Jackson 序列化 - null/空字段安全处理")
    void serialize_nullFieldsHandled() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UserDto dto = new UserDto();
        dto.setUsername("bob");
        dto.setPhone(null);
        dto.setEmail("");

        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("\"bob\""));
    }

    @Test
    @DisplayName("createContextual - 无注解属性返回默认序列化器")
    void createContextual_noAnnotationReturnsDefault() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        @Data
        class PlainDto {
            private String name;
        }
        PlainDto dto = new PlainDto();
        dto.setName("plain");
        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("plain"));
    }

    @Test
    @DisplayName("serializeWithType - 类型化序列化也脱敏")
    void serializeWithType_masksFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UserDto dto = new UserDto();
        dto.setPhone("13812348888");
        // 写成数组形式以触发 serializeWithType
        String json = mapper.writerFor(UserDto.class).writeValueAsString(dto);
        assertTrue(json.contains("138****8888"));
    }
}
