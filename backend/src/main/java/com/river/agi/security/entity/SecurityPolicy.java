package com.river.agi.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    private String description;
    @JsonIgnore
    private String rules;
    @JsonIgnore
    private Integer priority;
    @JsonIgnore
    @TableField("is_enabled")
    private Boolean legacyEnabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
