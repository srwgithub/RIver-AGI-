package com.river.agi.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.security.entity.SensitiveDataDetection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SensitiveDataDetectionMapper extends BaseMapper<SensitiveDataDetection> {
    
    @Select("SELECT * FROM sensitive_data_detection WHERE scan_task_id = #{scanTaskId} AND deleted = 0")
    List<SensitiveDataDetection> selectByScanTaskId(@Param("scanTaskId") Long scanTaskId);
    
    default List<SensitiveDataDetection> selectByDatasetId(Long datasetId) {
        return selectByScanTaskId(datasetId);
    }
}
