package com.river.agi.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prediction_task")
public class PredictionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Long datasetId;
    private String targetField;
    private String timeField;
    private String modelType;
    private String taskType;
    private String dlModelId;
    private String status;
    private String parametersJson;
    private Integer forecastDays;
    private String confidenceLevel;
    private Integer windowSize;
    private Long modelVersionId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
