package com.river.agi.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outlier_detection")
public class OutlierDetection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long analysisTaskId;
    private String columnName;
    private Integer outlierCount;
    private String outlierIndices;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
}
