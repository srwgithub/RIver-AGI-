package com.river.agi.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_version")
public class ModelVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String modelName;
    private String modelType;
    private String algorithmType;
    private String taskType;
    private String modelPath;
    private Integer versionNumber;
    private String status;
    private Double mae;
    private Double rmse;
    private Double mape;
    private String trainingMetricsJson;
    private String algorithmParams;
    private String featureImportanceJson;
    private Long trainingSamples;
    private Long predictionTaskId;
    private Boolean isProduction;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
