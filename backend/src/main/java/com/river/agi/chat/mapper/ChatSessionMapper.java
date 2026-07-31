package com.river.agi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND deleted = 0 ORDER BY updated_at DESC")
    Page<ChatSession> selectByUserId(Page<ChatSession> page, @Param("userId") Long userId);
}
