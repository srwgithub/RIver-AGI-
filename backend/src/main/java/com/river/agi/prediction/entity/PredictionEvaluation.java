package com.river.agi.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prediction_evaluation")
public class PredictionEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long modelVersionId;
    private String evaluationType;
    private String algorithm;
    private Double mae;
    private Double rmse;
    private Double mape;
    private Double r2;
    private Double biasPercentage;
    private Double accuracyScore;
    private String status;
    private String recommendation;
    private String parametersJson;
    private LocalDateTime createdAt;
}
