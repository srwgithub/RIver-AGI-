package com.river.agi.auth.dto;

import com.river.agi.common.annotation.Sensitive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private Long tenantId;
    private String username;
    @Sensitive(type = Sensitive.Type.EMAIL)
    private String email;
    @Sensitive(type = Sensitive.Type.PHONE)
    private String phone;
    @Sensitive(type = Sensitive.Type.NAME)
    private String realName;
    private String role;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
