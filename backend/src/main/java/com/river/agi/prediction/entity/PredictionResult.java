package com.river.agi.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prediction_result")
public class PredictionResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    private String predictionDate;
    private Double predictedValue;
    private Double actualValue;
    private Double lowerBound;
    private Double upperBound;
    private Double confidence;
    private LocalDateTime createdAt;
    private Integer deleted;
}
