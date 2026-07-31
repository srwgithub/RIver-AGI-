package com.river.agi.annotation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.annotation.entity.LabelSchema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LabelSchemaMapper extends BaseMapper<LabelSchema> {
    
    @Select("SELECT * FROM label_schema WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY sort_order ASC")
    List<LabelSchema> selectByParentId(@Param("parentId") Long parentId);
}
