package com.river.agi.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.security.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    @Select("SELECT * FROM audit_log WHERE user_id = #{userId} ORDER BY created_at DESC")
    Page<AuditLog> selectByUserId(Page<AuditLog> page, @Param("userId") Long userId);
    
    @Select("SELECT * FROM audit_log WHERE resource_type = #{resourceType} ORDER BY created_at DESC")
    Page<AuditLog> selectByResourceType(Page<AuditLog> page, @Param("resourceType") String resourceType);
}
