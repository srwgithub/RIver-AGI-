package com.river.agi.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.analysis.entity.OutlierDetection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OutlierDetectionMapper extends BaseMapper<OutlierDetection> {
    
    @Select("SELECT * FROM outlier_detection WHERE analysis_task_id = #{analysisTaskId} AND deleted = 0")
    List<OutlierDetection> selectByAnalysisTaskId(@Param("analysisTaskId") Long analysisTaskId);
    
    @Select("SELECT * FROM outlier_detection WHERE analysis_task_id IN (SELECT id FROM analysis_task WHERE dataset_id = #{datasetId}) AND deleted = 0")
    List<OutlierDetection> selectByDatasetId(@Param("datasetId") Long datasetId);
}
