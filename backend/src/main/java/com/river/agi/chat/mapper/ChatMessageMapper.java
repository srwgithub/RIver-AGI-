package com.river.agi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.river.agi.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY created_at ASC")
    List<ChatMessage> selectBySessionId(@Param("sessionId") Long sessionId);
}
