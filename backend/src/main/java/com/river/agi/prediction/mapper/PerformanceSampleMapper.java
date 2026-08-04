package com.river.agi.prediction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.prediction.entity.PerformanceSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PerformanceSampleMapper extends BaseMapper<PerformanceSample> {
    @Select("SELECT * FROM performance_sample WHERE prediction_task_id = #{taskId} AND sampled_at >= #{since} ORDER BY sampled_at ASC")
    List<PerformanceSample> selectSince(@Param("taskId") Long taskId, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM performance_sample WHERE prediction_task_id = #{taskId} ORDER BY sampled_at DESC LIMIT #{limit}")
    List<PerformanceSample> selectRecent(@Param("taskId") Long taskId, @Param("limit") int limit);
}
