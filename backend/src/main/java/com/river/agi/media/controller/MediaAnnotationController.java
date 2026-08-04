package com.river.agi.media.controller;

import com.river.agi.media.entity.MediaAnnotation;
import com.river.agi.media.service.MediaAnnotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/media-annotations")
@RequiredArgsConstructor
public class MediaAnnotationController {
    
    private final MediaAnnotationService mediaAnnotationService;
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMediaAnnotation(
            @RequestBody MediaAnnotation annotation) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            MediaAnnotation created = mediaAnnotationService.createMediaAnnotation(annotation);
            result.put("code", 200);
            result.put("message", "Media annotation created successfully");
            result.put("data", created);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to create media annotation: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateMediaAnnotation(
            @PathVariable Long id,
            @RequestBody MediaAnnotation updates) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            MediaAnnotation updated = mediaAnnotationService.updateMediaAnnotation(id, updates);
            result.put("code", 200);
            result.put("message", "Media annotation updated successfully");
            result.put("data", updated);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to update media annotation: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMediaAnnotation(@PathVariable Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            MediaAnnotation annotation = mediaAnnotationService.getAnnotation(id);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", annotation);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get media annotation: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskAnnotations(@PathVariable Long taskId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            List<MediaAnnotation> annotations = mediaAnnotationService.getTaskAnnotations(taskId);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", annotations);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get task annotations: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tasks/{taskId}/type/{mediaType}")
    public ResponseEntity<Map<String, Object>> getTaskAnnotationsByType(
            @PathVariable Long taskId,
            @PathVariable String mediaType) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            List<MediaAnnotation> annotations = 
                mediaAnnotationService.getTaskAnnotationsByType(taskId, mediaType);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", annotations);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get annotations: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMediaAnnotation(@PathVariable Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            boolean success = mediaAnnotationService.deleteAnnotation(id);
            result.put("code", success ? 200 : 404);
            result.put("message", success ? "Media annotation deleted successfully" : "Not found");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to delete: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tasks/{taskId}/progress")
    public ResponseEntity<Map<String, Object>> getTaskProgress(@PathVariable Long taskId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            Map<String, Object> progress = mediaAnnotationService.getTaskProgress(taskId);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", progress);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to get progress: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{id}/validate-boxes")
    public ResponseEntity<Map<String, Object>> validateBoundingBoxes(@PathVariable Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            List<Map<String, Object>> issues = mediaAnnotationService.validateBoundingBoxes(id);
            result.put("code", 200);
            result.put("message", "Success");
            result.put("data", Map.of("issues", issues));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to validate boxes: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/auto-generate")
    public ResponseEntity<Map<String, Object>> autoGenerateAnnotation(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            Long taskId = Long.valueOf(request.get("taskId").toString());
            String mediaType = (String) request.get("mediaType");
            String mediaUrl = (String) request.get("mediaUrl");
            Long userId = Long.valueOf(request.get("userId").toString());
            
            MediaAnnotation annotation = mediaAnnotationService.autoGenerateAnnotation(
                taskId, mediaType, mediaUrl, userId);
            
            result.put("code", 200);
            result.put("message", "Media annotation auto-generated successfully");
            result.put("data", annotation);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "Failed to auto-generate: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
