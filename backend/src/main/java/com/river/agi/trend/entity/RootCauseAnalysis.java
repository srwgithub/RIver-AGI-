package com.river.agi.trend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("root_cause_analysis")
public class RootCauseAnalysis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long anomalyAlertId;
    private Long predictionTaskId;
    private Long datasetId;
    private String analysisType;
    private String targetMetric;
    private Double impactValue;
    private Double impactPercent;
    private String factorsJson;
    private String topContributorsJson;
    private String recommendationsJson;
    private String analysisSummary;
    private Long tenantId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
