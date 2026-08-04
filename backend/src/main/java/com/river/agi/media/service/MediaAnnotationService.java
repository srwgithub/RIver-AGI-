package com.river.agi.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.common.BusinessException;
import com.river.agi.media.entity.MediaAnnotation;
import com.river.agi.media.mapper.MediaAnnotationMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAnnotationService {
    
    private final MediaAnnotationMapper mediaAnnotationMapper;
    private final ObjectMapper objectMapper;
    
    public MediaAnnotation createMediaAnnotation(MediaAnnotation annotation) {
        annotation.setStatus("PENDING");
        annotation.setCreatedAt(LocalDateTime.now());
        annotation.setUpdatedAt(LocalDateTime.now());
        mediaAnnotationMapper.insert(annotation);
        return annotation;
    }
    
    public MediaAnnotation updateMediaAnnotation(Long id, MediaAnnotation updates) {
        MediaAnnotation existing = mediaAnnotationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Media annotation not found");
        }
        
        if (updates.getAnnotationData() != null) {
            existing.setAnnotationData(updates.getAnnotationData());
        }
        if (updates.getBoundingBoxes() != null) {
            existing.setBoundingBoxes(updates.getBoundingBoxes());
        }
        if (updates.getKeyFrames() != null) {
            existing.setKeyFrames(updates.getKeyFrames());
        }
        if (updates.getTranscription() != null) {
            existing.setTranscription(updates.getTranscription());
        }
        if (updates.getComment() != null) {
            existing.setComment(updates.getComment());
        }
        if (updates.getStatus() != null) {
            existing.setStatus(updates.getStatus());
        }
        if (updates.getConfidence() != null) {
            existing.setConfidence(updates.getConfidence());
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        mediaAnnotationMapper.updateById(existing);
        
        log.info("Media annotation updated: id={}, status={}", id, existing.getStatus());
        return existing;
    }
    
    public List<MediaAnnotation> getTaskAnnotations(Long taskId) {
        return mediaAnnotationMapper.selectByTaskId(taskId);
    }
    
    public List<MediaAnnotation> getTaskAnnotationsByType(Long taskId, String mediaType) {
        return mediaAnnotationMapper.selectByTaskAndType(taskId, mediaType);
    }
    
    public MediaAnnotation getAnnotation(Long id) {
        MediaAnnotation annotation = mediaAnnotationMapper.selectById(id);
        if (annotation == null) {
            throw new BusinessException("Media annotation not found");
        }
        return annotation;
    }
    
    public boolean deleteAnnotation(Long id) {
        MediaAnnotation annotation = mediaAnnotationMapper.selectById(id);
        if (annotation == null) {
            return false;
        }
        mediaAnnotationMapper.deleteById(id);
        return true;
    }
    
    public Map<String, Object> getTaskProgress(Long taskId) {
        List<MediaAnnotation> annotations = getTaskAnnotations(taskId);
        
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("taskId", taskId);
        progress.put("totalCount", annotations.size());
        
        long pendingCount = annotations.stream()
            .filter(a -> "PENDING".equals(a.getStatus()))
            .count();
        long completedCount = annotations.stream()
            .filter(a -> "COMPLETED".equals(a.getStatus()))
            .count();
        long inProgressCount = annotations.stream()
            .filter(a -> "IN_PROGRESS".equals(a.getStatus()))
            .count();
        
        progress.put("pendingCount", pendingCount);
        progress.put("completedCount", completedCount);
        progress.put("inProgressCount", inProgressCount);
        
        double completionRate = annotations.isEmpty() ? 0.0 : 
            (double) completedCount / annotations.size() * 100;
        progress.put("completionRate", Math.round(completionRate * 100.0) / 100.0);
        
        if (!annotations.isEmpty()) {
            double avgConfidence = annotations.stream()
                .filter(a -> a.getConfidence() != null)
                .mapToDouble(MediaAnnotation::getConfidence)
                .average()
                .orElse(0.0);
            progress.put("averageConfidence", Math.round(avgConfidence * 100.0) / 100.0);
        }
        
        Map<String, Long> typeBreakdown = new LinkedHashMap<>();
        for (MediaAnnotation a : annotations) {
            String type = a.getMediaType() != null ? a.getMediaType() : "UNKNOWN";
            typeBreakdown.merge(type, 1L, Long::sum);
        }
        progress.put("typeBreakdown", typeBreakdown);
        
        return progress;
    }
    
    public List<Map<String, Object>> validateBoundingBoxes(Long annotationId) {
        MediaAnnotation annotation = getAnnotation(annotationId);
        List<Map<String, Object>> issues = new ArrayList<>();
        
        if (annotation.getBoundingBoxes() == null || annotation.getBoundingBoxes().isEmpty()) {
            return issues;
        }
        
        try {
            List<Map<String, Object>> boxes = objectMapper.readValue(
                annotation.getBoundingBoxes(), 
                new TypeReference<List<Map<String, Object>>>() {}
            );
            
            for (int i = 0; i < boxes.size(); i++) {
                Map<String, Object> box = boxes.get(i);
                double x = ((Number) box.getOrDefault("x", 0)).doubleValue();
                double y = ((Number) box.getOrDefault("y", 0)).doubleValue();
                double width = ((Number) box.getOrDefault("width", 0)).doubleValue();
                double height = ((Number) box.getOrDefault("height", 0)).doubleValue();
                
                if (width <= 0 || height <= 0) {
                    issues.add(Map.of(
                        "boxIndex", i,
                        "issue", "Bounding box has zero or negative dimensions"
                    ));
                }
                if (x < 0 || y < 0) {
                    issues.add(Map.of(
                        "boxIndex", i,
                        "issue", "Bounding box has negative coordinates"
                    ));
                }
            }
        } catch (Exception e) {
            issues.add(Map.of("issue", "Failed to parse bounding boxes: " + e.getMessage()));
        }
        
        return issues;
    }
    
    public MediaAnnotation autoGenerateAnnotation(Long taskId, String mediaType, 
                                                   String mediaUrl, Long userId) {
        MediaAnnotation annotation = new MediaAnnotation();
        annotation.setTaskId(taskId);
        annotation.setMediaType(mediaType);
        annotation.setMediaUrl(mediaUrl);
        annotation.setAnnotatedBy(userId);
        annotation.setStatus("AUTO_GENERATED");
        annotation.setConfidence(0.5);
        
        if ("IMAGE".equals(mediaType)) {
            annotation.setDurationSeconds(null);
            annotation.setFrameCount(1);
        } else if ("VIDEO".equals(mediaType)) {
            annotation.setDurationSeconds(0L);
            annotation.setFrameCount(0);
        } else if ("AUDIO".equals(mediaType)) {
            annotation.setFrameCount(0);
        }
        
        annotation.setCreatedAt(LocalDateTime.now());
        annotation.setUpdatedAt(LocalDateTime.now());
        mediaAnnotationMapper.insert(annotation);
        
        log.info("Auto-generated media annotation: taskId={}, type={}", taskId, mediaType);
        return annotation;
    }
}
