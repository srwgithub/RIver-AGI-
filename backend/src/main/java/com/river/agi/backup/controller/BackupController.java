package com.river.agi.backup.controller;

import com.river.agi.backup.entity.BackupRecord;
import com.river.agi.backup.service.BackupService;
import com.river.agi.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/v1/backups", "/api/v1/backup/records"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {
    
    private final BackupService backupService;
    
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createBackup(
            @RequestParam(defaultValue = "MANUAL") String type) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            String backupId = backupService.createFullBackup(type);
            result.put("code", 200);
            result.put("message", "Backup created successfully");
            result.put("data", Map.of("backupId", backupId));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to create backup: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> listBackups() {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            List<BackupRecord> backups = backupService.listBackups();
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", backups);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to list backups: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{backupId}")
    public ResponseEntity<Map<String, Object>> getBackup(@PathVariable String backupId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            BackupRecord backup = backupService.getBackup(backupId);
            if (backup != null) {
                result.put("code", 200);
                result.put("message", "Success");
                result.put("data", backup);
            } else {
                result.put("code", 404);
                result.put("message", "Backup not found");
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get backup: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{backupId}/restore")
    public ResponseEntity<Map<String, Object>> restoreBackup(@PathVariable String backupId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            boolean success = backupService.restoreFromBackup(backupId);
            if (success) {
                result.put("code", 200);
                result.put("message", "Backup restored successfully");
            } else {
                result.put("code", 500);
                result.put("message", "Failed to restore backup");
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to restore backup: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{backupId}")
    public ResponseEntity<Map<String, Object>> deleteBackup(@PathVariable String backupId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            boolean success = backupService.deleteBackup(backupId);
            result.put("code", success ? 200 : 404);
            result.put("message", success ? "Backup deleted successfully" : "Backup not found");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to delete backup: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            Map<String, Object> status = backupService.getBackupStatus();
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", status);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get backup status: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
