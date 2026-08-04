package com.river.agi.chat.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatResponse {
    private Long sessionId;
    private String reply;
    private String chartConfigJson;
    private String dataJson;
    private LocalDateTime timestamp;
}
