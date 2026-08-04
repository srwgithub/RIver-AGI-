package com.river.agi.trend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("anomaly_alert")
public class AnomalyAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long predictionTaskId;
    private Long datasetId;
    private String anomalyType;
    private String severity;
    private String dimension;
    private String anomalyDate;
    private Double actualValue;
    private Double predictedValue;
    private Double deviationPercent;
    private Double expectedLowerBound;
    private Double expectedUpperBound;
    private String description;
    private String status;
    private String rootCauseHint;
    private Long tenantId;
    private LocalDateTime detectedAt;
    private LocalDateTime resolvedAt;
}
