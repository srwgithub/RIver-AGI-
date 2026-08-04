package com.river.agi.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dashboard_widget")
public class DashboardWidget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dashboardId;
    private String widgetType;
    private String title;
    private String chartType;
    private Integer positionX;
    private Integer positionY;
    private Integer width;
    private Integer height;
    private String configJson;
    private String dataSourceJson;
    private Integer sortOrder;
    private Long tenantId;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
