package com.river.agi.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("annotation_item")
public class Annotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long datasetId;
    private Integer rowIndex;
    private String labelCode;
    private String labelName;
    private String comment;
    private String fieldAnnotationsJson;
    private String status;
    private Long annotatedBy;
    private Long reviewedBy;
    private String reviewComment;
    private LocalDateTime annotatedAt;
    private LocalDateTime reviewedAt;
    private String annotationType;
    private BigDecimal confidence;
    private String modelSource;
    private String ruleVersion;
    private Boolean isCorrected;
    private BigDecimal originalConfidence;
    private String originalLabelCode;
    private LocalDateTime correctedAt;
    private Integer deleted;
    
    public enum Status {
        PENDING,
        PRE_ANNOTATED,
        SUBMITTED,
        IN_REVIEW,
        APPROVED,
        REJECTED,
        ARBITRATED,
        PUBLISHED
    }
    
    public enum AnnotationType {
        MANUAL,
        PRE_ANNOTATION,
        CORRECTION,
        ARBITRATION
    }
}
