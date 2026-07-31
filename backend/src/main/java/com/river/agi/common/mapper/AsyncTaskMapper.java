package com.river.agi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.common.entity.AsyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTask> {
    
    @Select("SELECT * FROM async_task WHERE status IN ('PENDING', 'RUNNING') AND deleted = 0 ORDER BY created_at ASC LIMIT #{limit}")
    List<AsyncTask> selectPendingTasks(@Param("limit") int limit);
    
    @Select("SELECT * FROM async_task WHERE task_type = #{taskType} AND resource_id = #{resourceId} AND deleted = 0 ORDER BY created_at DESC LIMIT 1")
    AsyncTask selectLatestByTypeAndResource(@Param("taskType") String taskType, @Param("resourceId") Long resourceId);
    
    @Select("SELECT * FROM async_task WHERE created_by = #{userId} AND deleted = 0 ORDER BY created_at DESC")
    List<AsyncTask> selectByUserId(@Param("userId") Long userId);
}
