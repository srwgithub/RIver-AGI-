package com.river.agi.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long sessionId;
    private String role;
    private String content;
    private String toolCallsJson;
    private String toolResultsJson;
    private LocalDateTime createdAt;
    private Integer deleted;
}
