package com.river.agi.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("annotation_history")
public class AnnotationHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String oldValue;
    private String newValue;
    private String reason;
    private LocalDateTime createdAt;
}
