package com.river.agi.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("performance_sample")
public class PerformanceSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long predictionTaskId;
    private Long modelVersionId;
    private String sampleType;
    private Long durationMs;
    private Double latencyMs;
    private Double throughputQps;
    private Double cpuPercent;
    private Double memoryPercent;
    private Double gpuPercent;
    private Double storageIoPercent;
    private String status;
    private String errorCode;
    private String detailsJson;
    private LocalDateTime sampledAt;
    private LocalDateTime createdAt;
}
