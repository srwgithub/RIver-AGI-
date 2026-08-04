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

    /** 备份文件 SHA-256 校验值（合同 14.1.3 备份完整性校验） */
    private String checksum;

    /** 异地备份副本路径（合同 14.1.3 异地备份） */
    private String offsitePath;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
