package com.river.agi.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.media.entity.MediaAnnotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MediaAnnotationMapper extends BaseMapper<MediaAnnotation> {
    
    @Select("SELECT * FROM media_annotation WHERE task_id = #{taskId}")
    List<MediaAnnotation> selectByTaskId(@Param("taskId") Long taskId);
    
    @Select("SELECT * FROM media_annotation WHERE task_id = #{taskId} AND media_type = #{mediaType}")
    List<MediaAnnotation> selectByTaskAndType(@Param("taskId") Long taskId, @Param("mediaType") String mediaType);
    
    @Select("SELECT COUNT(*) FROM media_annotation WHERE task_id = #{taskId}")
    long countByTaskId(@Param("taskId") Long taskId);
}
