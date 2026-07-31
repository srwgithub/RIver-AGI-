package com.river.agi.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("async_task")
public class AsyncTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String taskType;
    
    private String taskName;
    
    private String status;
    
    private Integer progress;
    
    private String resultJson;
    
    private String errorMessage;
    
    private String paramsJson;
    
    private Long resourceId;
    
    private String resourceType;
    
    private Long createdBy;
    
    private Long tenantId;
    
    private Integer retryCount;
    
    private Integer maxRetries;
    
    private String priority;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private Integer deleted;
    
    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    public enum TaskType {
        DATASET_PARSE,
        SECURITY_SCAN,
        QUALITY_ANALYSIS,
        DATA_PROFILE,
        OUTLIER_DETECTION,
        PREDICTION,
        REPORT_GENERATION,
        DATA_EXPORT,
        ANNOTATION_IMPORT,
        PRE_ANNOTATE,
        MODEL_TRAIN
    }
}
