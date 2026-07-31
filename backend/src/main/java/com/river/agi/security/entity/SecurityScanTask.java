package com.river.agi.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("security_scan_task")
public class SecurityScanTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String status;
    private LocalDateTime scanTime;
    private Integer riskCount;
    private Integer totalFields;
    private Integer sensitiveFieldsFound;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;
    private String scanSummaryJson;
    private String errorMessage;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
}
