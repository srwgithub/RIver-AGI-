package com.river.agi.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_instance")
public class ReportInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long datasetId;
    private String title;
    private String contentJson;
    private String exportFormat;
    private String fileUrl;
    private String status;
    private Long generatedBy;
    private LocalDateTime generatedAt;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
}
