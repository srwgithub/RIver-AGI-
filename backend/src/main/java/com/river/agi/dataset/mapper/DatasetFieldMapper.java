package com.river.agi.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.dataset.entity.DatasetField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DatasetFieldMapper extends BaseMapper<DatasetField> {
    
    @Select("SELECT * FROM dataset_column WHERE dataset_id = #{datasetId} AND deleted = 0 ORDER BY position ASC")
    List<DatasetField> selectByDatasetId(@Param("datasetId") Long datasetId);
}
