package com.river.agi.chart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String title;
    private String content;
    private String reportType;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
}
