package com.river.agi.annotation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.annotation.entity.Annotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AnnotationMapper extends BaseMapper<Annotation> {
    
    @Select("SELECT * FROM annotation_item WHERE task_id = #{taskId} AND deleted = 0")
    List<Annotation> selectByTaskId(@Param("taskId") Long taskId);
}
