package com.river.agi.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    @Select("SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectCodesByUserId(@Param("userId") Long userId);
}
