package com.river.agi.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("runtime_alert")
public class RuntimeAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long predictionTaskId;
    private Long sampleId;
    private String alertType;
    private String severity;
    private String title;
    private String description;
    private String status;
    private String thresholdJson;
    private LocalDateTime detectedAt;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
