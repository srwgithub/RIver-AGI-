package com.river.agi.backup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.backup.entity.BackupRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BackupRecordMapper extends BaseMapper<BackupRecord> {
}
