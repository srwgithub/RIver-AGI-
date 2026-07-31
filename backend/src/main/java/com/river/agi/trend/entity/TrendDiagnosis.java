package com.river.agi.trend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trend_diagnosis")
public class TrendDiagnosis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long predictionTaskId;
    private Long datasetId;
    private String targetField;
    private String trendDirection;
    private Double trendSlope;
    private Double trendStrength;
    private Double rSquared;
    private String seasonalityStatus;
    private Integer seasonalPeriod;
    private Double seasonalStrength;
    private String volatilityLevel;
    private Double volatilityCoefficient;
    private String turningPointsJson;
    private String decompositionJson;
    private String trendSummary;
    private Long tenantId;
    private LocalDateTime createdAt;
}
