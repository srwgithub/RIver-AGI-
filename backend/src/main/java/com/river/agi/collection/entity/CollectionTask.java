package com.river.agi.collection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Coordinates ingestion, cleaning and annotation for one source. */
@Data
@TableName("collection_task")
public class CollectionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String sourceType;
    private String mediaType;
    private String sourceUri;
    private Long datasetId;
    private Long labelSchemaId;
    private String cleaningConfigJson;
    private String cleaningSummaryJson;
    private String annotationRuleJson;
    private String collaborationMode;
    private String assignedAnnotators;
    private String status;
    private Integer totalItems;
    private Integer completedItems;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
