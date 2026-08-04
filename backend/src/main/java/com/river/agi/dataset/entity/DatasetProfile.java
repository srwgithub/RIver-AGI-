package com.river.agi.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dataset_profile")
public class DatasetProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long datasetId;
    private String fieldName;
    private Double minValue;
    private Double maxValue;
    private Double meanValue;
    private Double medianValue;
    private Double stdDev;
    private Integer nullCount;
    private Integer totalCount;
    private Double nullRate;
    private Double uniqueRate;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer deleted;
}
