package com.river.agi.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_task")
public class AnalysisTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String taskType;
    private String status;
    private String resultJson;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
