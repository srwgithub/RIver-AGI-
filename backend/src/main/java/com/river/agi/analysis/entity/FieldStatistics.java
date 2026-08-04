package com.river.agi.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dataset_profile")
public class FieldStatistics {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String profileJson;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
}
