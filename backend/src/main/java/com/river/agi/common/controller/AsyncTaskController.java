package com.river.agi.common.controller;

import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.service.AsyncTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Async Tasks", description = "Async task management APIs")
public class AsyncTaskController {
    
    private final AsyncTaskService asyncTaskService;
    private final SecurityUtils securityUtils;
    
    @GetMapping("/{id}")
    @Operation(summary = "Get task status", description = "Get async task status by ID")
    public ApiResponse<Map<String, Object>> getTaskStatus(
            @Parameter(description = "Task ID") @PathVariable Long id,
            Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        return ApiResponse.ok(asyncTaskService.getTaskProgress(id, userId));
    }
    
    @GetMapping
    @Operation(summary = "List tasks", description = "Get paginated list of async tasks")
    public ApiResponse<PageResult<AsyncTask>> getTasks(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Task status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Task type filter") @RequestParam(required = false) String taskType,
            Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        return ApiResponse.ok(asyncTaskService.getTaskList(page, size, status, taskType, userId));
    }
    
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel task", description = "Cancel a pending or running task")
    public ApiResponse<Void> cancelTask(
            @Parameter(description = "Task ID") @PathVariable Long id,
            Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        asyncTaskService.cancelTask(id, userId);
        return ApiResponse.ok(null);
    }
    
    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry task", description = "Retry a failed task")
    public ApiResponse<Void> retryTask(
            @Parameter(description = "Task ID") @PathVariable Long id,
            Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        asyncTaskService.retryTask(id, userId);
        return ApiResponse.ok(null);
    }
}
