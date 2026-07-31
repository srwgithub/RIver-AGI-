package com.river.agi.chat.controller;

import com.river.agi.chat.dto.ChatRequest;
import com.river.agi.chat.dto.ChatResponse;
import com.river.agi.chat.entity.ChatMessage;
import com.river.agi.chat.entity.ChatSession;
import com.river.agi.chat.service.ChatService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "AI chat and conversation APIs")
public class ChatController {
    
    private final ChatService chatService;
    
    @PostMapping("/sessions")
    @Operation(summary = "Create session", description = "Create a new chat session")
    public ApiResponse<ChatSession> createSession(
            @Parameter(description = "Dataset ID") @RequestParam(required = false) Long datasetId,
            Authentication authentication) {
        return ApiResponse.ok(chatService.createSession(datasetId, authentication));
    }
    
    @GetMapping("/sessions")
    @Operation(summary = "List sessions", description = "Get paginated list of chat sessions")
    public ApiResponse<PageResult<ChatSession>> getSessions(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return ApiResponse.ok(chatService.getSessions(page, size, authentication));
    }
    
    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get session", description = "Get chat session by ID")
    public ApiResponse<ChatSession> getSession(@Parameter(description = "Session ID") @PathVariable Long id) {
        return ApiResponse.ok(chatService.getSession(id));
    }
    
    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Delete session", description = "Delete chat session by ID")
    public ApiResponse<Void> deleteSession(@Parameter(description = "Session ID") @PathVariable Long id) {
        chatService.deleteSession(id);
        return ApiResponse.ok(null);
    }
    
    @PostMapping("/sessions/{id}/messages")
    @Operation(summary = "Send message", description = "Send a message to the chat session")
    public ApiResponse<ChatResponse> sendMessage(
            @Parameter(description = "Session ID") @PathVariable Long id,
            @RequestBody ChatRequest request,
            Authentication authentication) {
        request.setSessionId(id);
        return ApiResponse.ok(chatService.sendMessage(request, authentication));
    }
    
    @GetMapping("/sessions/{id}/messages")
    @Operation(summary = "Get messages", description = "Get all messages in a chat session")
    public ApiResponse<List<ChatMessage>> getMessages(@Parameter(description = "Session ID") @PathVariable Long id) {
        return ApiResponse.ok(chatService.getMessages(id));
    }
    
    @PostMapping("/messages")
    @Operation(summary = "Quick message", description = "Send a message without specifying session")
    public ApiResponse<ChatResponse> quickMessage(
            @RequestBody ChatRequest request,
            Authentication authentication) {
        return ApiResponse.ok(chatService.sendMessage(request, authentication));
    }
}
