package com.river.agi.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.analysis.entity.FieldStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FieldStatisticsMapper extends BaseMapper<FieldStatistics> {
    
    @Select("SELECT * FROM dataset_profile WHERE dataset_id = #{datasetId} AND deleted = 0")
    List<FieldStatistics> selectByDatasetId(@Param("datasetId") Long datasetId);
}
