package com.river.agi.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("annotation_task_assignee")
public class AnnotationTaskAssignee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long annotatorId;
    private Long assignedBy;
    private String status;
    private LocalDateTime assignedAt;
}
