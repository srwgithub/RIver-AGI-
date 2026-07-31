package com.river.agi.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String actionType;
    private String resourceType;
    private Long resourceId;
    private String resourceName;
    private Long userId;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String requestMethod;
    private String requestPath;
    private String operationDetails;
    private String result;
    private Long durationMs;
    private LocalDateTime createdAt;
    private Integer deleted;
}
