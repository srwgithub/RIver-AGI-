package com.river.agi.backup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("backup_record")
public class BackupRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String backupId;
    
    private String type;
    
    private String status;
    
    private String filePath;
    
    private Long sizeBytes;
    
    private String error;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime completedAt;
}
