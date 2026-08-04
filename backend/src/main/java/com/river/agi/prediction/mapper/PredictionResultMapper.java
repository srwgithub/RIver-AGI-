package com.river.agi.prediction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.prediction.entity.PredictionResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PredictionResultMapper extends BaseMapper<PredictionResult> {
    
    @Select("SELECT * FROM prediction_result WHERE task_id = #{taskId} ORDER BY prediction_date ASC")
    List<PredictionResult> selectByTaskId(@Param("taskId") Long taskId);
}
