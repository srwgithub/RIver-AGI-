package com.river.agi.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("security_policy")
public class SecurityPolicy {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String name;
    private String policyType;
    private String classification;
    private String rulesJson;
    private Boolean enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
