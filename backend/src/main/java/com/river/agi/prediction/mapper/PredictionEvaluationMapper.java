package com.river.agi.prediction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.prediction.entity.PredictionEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PredictionEvaluationMapper extends BaseMapper<PredictionEvaluation> {
    @Select("SELECT * FROM prediction_evaluation WHERE task_id = #{taskId} ORDER BY created_at DESC")
    List<PredictionEvaluation> selectByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT * FROM prediction_evaluation WHERE task_id = #{taskId} "
            + "AND evaluation_type IN ('RETRAINING', 'AUTO_RETRAIN_REQUEST') "
            + "ORDER BY created_at DESC LIMIT 1")
    PredictionEvaluation selectLatestRetraining(@Param("taskId") Long taskId);
}
