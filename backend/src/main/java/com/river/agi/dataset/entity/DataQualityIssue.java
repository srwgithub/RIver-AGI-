package com.river.agi.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_quality_issue")
public class DataQualityIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long datasetId;
    private String fieldName;
    private Integer rowIndex;
    @TableField("issue_value")
    private String value;
    private Double zScore;
    private Double iqrScore;
    private String outlierType;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer deleted;
}
