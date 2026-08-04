package com.river.agi.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dashboard")
public class Dashboard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long datasetId;
    private String category;
    private String layoutJson;
    private String filterConfigJson;
    private Boolean isDefault;
    private Boolean isPublic;
    private Long tenantId;
    private Long createdBy;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
