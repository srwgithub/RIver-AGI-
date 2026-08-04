package com.river.agi.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dataset_column")
public class DatasetField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long datasetId;
    private String fieldName;
    private String fieldType;
    private Integer position;
    private Integer nullCount;
    private Integer distinctCount;
    private String sampleValues;
    private String statisticsJson;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer deleted;
}
