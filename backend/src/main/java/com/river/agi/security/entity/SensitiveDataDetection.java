package com.river.agi.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sensitive_data_detection")
public class SensitiveDataDetection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scanTaskId;
    private String columnName;
    private String sensitiveType;
    private String riskLevel;
    private Integer detectedCount;
    private String sampleData;
    private String maskedSampleData;
    private BigDecimal confidence;
    private String ruleVersion;
    private String suggestion;
    private String matchType;
    private String regexPattern;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
}
