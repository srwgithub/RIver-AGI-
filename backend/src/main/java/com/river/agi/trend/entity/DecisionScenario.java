package com.river.agi.trend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("decision_scenario")
public class DecisionScenario {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private Long predictionTaskId;
    private String scenarioName;
    private String scenarioType;
    private String assumptionsJson;
    private String adjustedFactorsJson;
    private String forecastResultsJson;
    private Double expectedGrowth;
    private String riskLevel;
    private String actionRecommendationsJson;
    private Long tenantId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
