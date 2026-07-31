package com.river.agi.chart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chart_config")
public class ChartConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String chartType;
    private String title;
    private String configJson;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
}
