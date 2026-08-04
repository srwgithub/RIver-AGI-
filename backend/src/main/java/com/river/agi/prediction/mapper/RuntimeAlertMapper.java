package com.river.agi.prediction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.prediction.entity.RuntimeAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RuntimeAlertMapper extends BaseMapper<RuntimeAlert> {
    @Select("SELECT * FROM runtime_alert WHERE (#{taskId} IS NULL OR prediction_task_id = #{taskId}) AND (#{status} IS NULL OR status = #{status}) ORDER BY detected_at DESC LIMIT #{limit}")
    List<RuntimeAlert> selectAlerts(@Param("taskId") Long taskId, @Param("status") String status, @Param("limit") int limit);
}
