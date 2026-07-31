package com.river.agi.prediction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.prediction.entity.ModelVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModelVersionMapper extends BaseMapper<ModelVersion> {
    
    @Select("SELECT * FROM model_version WHERE model_name = #{modelName} ORDER BY version_number DESC")
    List<ModelVersion> selectByModelName(@Param("modelName") String modelName);
}
