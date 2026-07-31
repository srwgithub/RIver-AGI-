package com.river.agi.collaboration.controller;

import com.river.agi.collaboration.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/collaboration")
@RequiredArgsConstructor
public class CollaborationController {
    
    private final CollaborationService collaborationService;
    
    @PostMapping("/tasks/{taskId}/lock")
    public ResponseEntity<Map<String, Object>> acquireLock(
            @PathVariable Long taskId,
            @RequestParam Long rowIndex,
            @RequestParam Long userId,
            @RequestParam String username) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            CollaborationService.LockResult lockResult = 
                collaborationService.acquireLock(taskId, rowIndex, userId, username);
            result.put("code", lockResult.success() ? 200 : 409);
            result.put("message", lockResult.message());
            result.put("data", lockResult.lockInfo());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to acquire lock: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/tasks/{taskId}/unlock")
    public ResponseEntity<Map<String, Object>> releaseLock(
            @PathVariable Long taskId,
            @RequestParam Long rowIndex,
            @RequestParam Long userId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            boolean success = collaborationService.releaseLock(taskId, rowIndex, userId);
            result.put("code", success ? 200 : 403);
            result.put("message", success ? "Lock released successfully" : "Lock release denied");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to release lock: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tasks/{taskId}/locks")
    public ResponseEntity<Map<String, Object>> getActiveLocks(@PathVariable Long taskId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            List<CollaborationService.LockInfo> locks = collaborationService.getActiveLocks(taskId);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", locks);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get locks: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tasks/{taskId}/rows/{rowIndex}/lock-status")
    public ResponseEntity<Map<String, Object>> getLockStatus(
            @PathVariable Long taskId,
            @PathVariable Long rowIndex) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            CollaborationService.LockInfo lockInfo = collaborationService.getLockStatus(taskId, rowIndex);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", lockInfo);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get lock status: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/tasks/{taskId}/rows/{rowIndex}/record-edit")
    public ResponseEntity<Map<String, Object>> recordEdit(
            @PathVariable Long taskId,
            @PathVariable Long rowIndex,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            String username = (String) request.get("username");
            String action = (String) request.get("action");
            String oldValue = (String) request.get("oldValue");
            String newValue = (String) request.get("newValue");
            
            collaborationService.recordEdit(taskId, rowIndex, userId, username, 
                action, oldValue, newValue);
            
            result.put("code", 200);
            result.put("message", "Edit recorded successfully");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to record edit: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tasks/{taskId}/history")
    public ResponseEntity<Map<String, Object>> getTaskHistory(@PathVariable Long taskId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            List<CollaborationService.EditHistory> history = 
                collaborationService.getTaskEditHistory(taskId);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", history);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get history: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/tasks/{taskId}/rows/{rowIndex}/resolve-conflict")
    public ResponseEntity<Map<String, Object>> resolveConflict(
            @PathVariable Long taskId,
            @PathVariable Long rowIndex,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            String currentValue = (String) request.get("currentValue");
            Long userId = Long.valueOf(request.get("userId").toString());
            
            CollaborationService.ConflictResult conflictResult = 
                collaborationService.resolveConflict(taskId, rowIndex, currentValue, userId);
            
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", Map.of(
                "hasConflict", conflictResult.hasConflict(),
                "lastEdit", conflictResult.lastEdit(),
                "message", conflictResult.message()
            ));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to resolve conflict: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
