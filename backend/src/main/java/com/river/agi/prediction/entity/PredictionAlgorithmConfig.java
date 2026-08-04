package com.river.agi.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prediction_algorithm_config")
public class PredictionAlgorithmConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String algorithmType;
    private String algorithmName;
    private String algorithmFamily;
    private String taskType;
    private String defaultParams;
    private Boolean isDefault;
    private Boolean isEnabled;
    private Integer priority;
    private LocalDateTime createdAt;
}
