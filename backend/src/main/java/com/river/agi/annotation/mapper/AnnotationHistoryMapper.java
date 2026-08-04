package com.river.agi.annotation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.annotation.entity.AnnotationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AnnotationHistoryMapper extends BaseMapper<AnnotationHistory> {
    @Select("SELECT h.* FROM annotation_history h JOIN annotation_item a ON a.id = h.item_id WHERE a.task_id = #{taskId} ORDER BY h.created_at DESC")
    List<AnnotationHistory> selectByTaskId(Long taskId);
}
