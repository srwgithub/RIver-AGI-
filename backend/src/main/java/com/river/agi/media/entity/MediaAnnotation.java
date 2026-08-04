package com.river.agi.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_annotation")
public class MediaAnnotation {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long taskId;
    
    private String mediaType;
    
    private String mediaUrl;
    
    private String thumbnailUrl;
    
    private Long durationSeconds;
    
    private Integer frameCount;
    
    private String annotationData;
    
    private String boundingBoxes;
    
    private String keyFrames;
    
    private String transcription;
    
    private Long annotatedBy;
    
    private String status;
    
    private Double confidence;
    
    private String comment;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
