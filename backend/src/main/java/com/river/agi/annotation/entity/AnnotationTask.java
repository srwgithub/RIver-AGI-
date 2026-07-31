package com.river.agi.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("annotation_task")
public class AnnotationTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private Long datasetId;
    private Long labelSchemaId;
    private String status;
    private Integer totalRows;
    private Integer completedRows;
    private Integer assignedAnnotators;
    private Long createdBy;
    private Double qualityScore;
    private String qualityReportJson;
    private Integer reviewCount;
    private Integer arbitrationCount;
    private Double passRate;
    private Double consistencyRate;
    private String publishVersion;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    
    public enum Status {
        PENDING,
        PRE_ANNOTATED,
        IN_PROGRESS,
        COMPLETED,
        IN_REVIEW,
        ARBITRATION,
        PUBLISHED,
        CANCELLED
    }
}
