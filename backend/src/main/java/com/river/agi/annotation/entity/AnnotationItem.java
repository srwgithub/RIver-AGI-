package com.river.agi.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("annotation_item")
public class AnnotationItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long datasetId;
    private Integer rowIndex;
    private String labelCode;
    private String labelName;
    private String comment;
    private String status;
    private Long annotatedBy;
    private Long reviewedBy;
    private String reviewComment;
    private LocalDateTime annotatedAt;
    private LocalDateTime reviewedAt;
    private Integer deleted;
}
