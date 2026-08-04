package com.river.agi.chat.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long sessionId;
    private Long datasetId;
    private String message;
}
