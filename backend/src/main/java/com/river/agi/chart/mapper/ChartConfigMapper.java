package com.river.agi.chart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.chart.entity.ChartConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChartConfigMapper extends BaseMapper<ChartConfig> {
    
    @Select("SELECT * FROM chart_config WHERE dataset_id = #{datasetId} AND deleted = 0")
    List<ChartConfig> selectByDatasetId(@Param("datasetId") Long datasetId);
}
