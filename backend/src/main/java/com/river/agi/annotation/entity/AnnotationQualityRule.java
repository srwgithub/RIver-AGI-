package com.river.agi.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Configurable validation rule used by the annotation quality gate. */
@Data
@TableName("annotation_quality_rule")
public class AnnotationQualityRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String ruleType;
    private String pattern;
    private Double threshold;
    private String action;
    private Integer priority;
    private Boolean enabled;
    private String description;
    private String version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
