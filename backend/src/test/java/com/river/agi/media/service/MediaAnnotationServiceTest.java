package com.river.agi.media.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.media.entity.MediaAnnotation;
import com.river.agi.media.mapper.MediaAnnotationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaAnnotationServiceTest {

    @Mock private MediaAnnotationMapper mediaAnnotationMapper;
    private ObjectMapper objectMapper;
    private MediaAnnotationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new MediaAnnotationService(mediaAnnotationMapper, objectMapper);
    }

    private MediaAnnotation annotation() {
        MediaAnnotation a = new MediaAnnotation();
        a.setId(1L);
        a.setTaskId(10L);
        a.setMediaType("IMAGE");
        a.setMediaUrl("/file/test.png");
        a.setStatus("PENDING");
        return a;
    }

    // ===== createMediaAnnotation =====

    @Test
    @DisplayName("createMediaAnnotation sets defaults and inserts")
    void createMediaAnnotation_setsDefaults() {
        MediaAnnotation a = new MediaAnnotation();
        a.setTaskId(10L);

        MediaAnnotation result = service.createMediaAnnotation(a);

        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        verify(mediaAnnotationMapper).insert(a);
    }

    // ===== updateMediaAnnotation =====

    @Test
    @DisplayName("updateMediaAnnotation throws when not found")
    void updateMediaAnnotation_notFound() {
        when(mediaAnnotationMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.updateMediaAnnotation(99L, new MediaAnnotation()));
    }

    @Test
    @DisplayName("updateMediaAnnotation updates non-null fields and returns")
    void updateMediaAnnotation_updatesFields() {
        MediaAnnotation existing = annotation();
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(existing);

        MediaAnnotation updates = new MediaAnnotation();
        updates.setAnnotationData("{\"label\":\"cat\"}");
        updates.setBoundingBoxes("[{\"x\":0,\"y\":0,\"width\":10,\"height\":10}]");
        updates.setKeyFrames("[1,2,3]");
        updates.setTranscription("hello");
        updates.setComment("looks good");
        updates.setStatus("COMPLETED");
        updates.setConfidence(0.95);

        MediaAnnotation result = service.updateMediaAnnotation(1L, updates);

        assertEquals("{\"label\":\"cat\"}", result.getAnnotationData());
        assertEquals("[{\"x\":0,\"y\":0,\"width\":10,\"height\":10}]", result.getBoundingBoxes());
        assertEquals("[1,2,3]", result.getKeyFrames());
        assertEquals("hello", result.getTranscription());
        assertEquals("looks good", result.getComment());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(0.95, result.getConfidence());
        assertNotNull(result.getUpdatedAt());
        verify(mediaAnnotationMapper).updateById(existing);
    }

    @Test
    @DisplayName("updateMediaAnnotation ignores null fields")
    void updateMediaAnnotation_ignoresNullFields() {
        MediaAnnotation existing = annotation();
        existing.setComment("original comment");
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(existing);

        MediaAnnotation updates = new MediaAnnotation();
        updates.setStatus("IN_PROGRESS");
        // Other fields left null

        MediaAnnotation result = service.updateMediaAnnotation(1L, updates);

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals("original comment", result.getComment());
    }

    // ===== getTaskAnnotations =====

    @Test
    @DisplayName("getTaskAnnotations delegates to mapper")
    void getTaskAnnotations_delegates() {
        when(mediaAnnotationMapper.selectByTaskId(10L)).thenReturn(List.of(annotation()));

        List<MediaAnnotation> result = service.getTaskAnnotations(10L);

        assertEquals(1, result.size());
    }

    // ===== getTaskAnnotationsByType =====

    @Test
    @DisplayName("getTaskAnnotationsByType delegates to mapper")
    void getTaskAnnotationsByType_delegates() {
        when(mediaAnnotationMapper.selectByTaskAndType(10L, "VIDEO")).thenReturn(List.of(annotation()));

        List<MediaAnnotation> result = service.getTaskAnnotationsByType(10L, "VIDEO");

        assertEquals(1, result.size());
    }

    // ===== getAnnotation =====

    @Test
    @DisplayName("getAnnotation returns annotation when found")
    void getAnnotation_found() {
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(annotation());

        MediaAnnotation result = service.getAnnotation(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getAnnotation throws when not found")
    void getAnnotation_notFound() {
        when(mediaAnnotationMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getAnnotation(99L));
    }

    // ===== deleteAnnotation =====

    @Test
    @DisplayName("deleteAnnotation returns false when not found")
    void deleteAnnotation_notFound() {
        when(mediaAnnotationMapper.selectById(99L)).thenReturn(null);

        assertFalse(service.deleteAnnotation(99L));
        verify(mediaAnnotationMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteAnnotation returns true and deletes when found")
    void deleteAnnotation_found() {
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(annotation());

        assertTrue(service.deleteAnnotation(1L));
        verify(mediaAnnotationMapper).deleteById(1L);
    }

    // ===== getTaskProgress =====

    @Test
    @DisplayName("getTaskProgress returns zero counts when no annotations")
    void getTaskProgress_empty() {
        when(mediaAnnotationMapper.selectByTaskId(10L)).thenReturn(List.of());

        Map<String, Object> progress = service.getTaskProgress(10L);

        assertEquals(10L, progress.get("taskId"));
        assertEquals(0, progress.get("totalCount"));
        assertEquals(0L, progress.get("pendingCount"));
        assertEquals(0L, progress.get("completedCount"));
        assertEquals(0L, progress.get("inProgressCount"));
        assertEquals(0.0, progress.get("completionRate"));
        assertNull(progress.get("averageConfidence"));
        assertNotNull(progress.get("typeBreakdown"));
    }

    @Test
    @DisplayName("getTaskProgress aggregates status counts and average confidence")
    void getTaskProgress_withAnnotations() {
        MediaAnnotation a1 = annotation();
        a1.setStatus("PENDING");
        a1.setConfidence(0.6);
        a1.setMediaType("IMAGE");

        MediaAnnotation a2 = annotation();
        a2.setStatus("COMPLETED");
        a2.setConfidence(0.9);
        a2.setMediaType("VIDEO");

        MediaAnnotation a3 = annotation();
        a3.setStatus("IN_PROGRESS");
        a3.setConfidence(null);
        a3.setMediaType("IMAGE");

        when(mediaAnnotationMapper.selectByTaskId(10L)).thenReturn(List.of(a1, a2, a3));

        Map<String, Object> progress = service.getTaskProgress(10L);

        assertEquals(3, progress.get("totalCount"));
        assertEquals(1L, progress.get("pendingCount"));
        assertEquals(1L, progress.get("completedCount"));
        assertEquals(1L, progress.get("inProgressCount"));
        // completionRate = 1/3 * 100 ≈ 33.33
        assertEquals(33.33, progress.get("completionRate"));
        // averageConfidence = (0.6 + 0.9) / 2 = 0.75
        assertEquals(0.75, progress.get("averageConfidence"));
        @SuppressWarnings("unchecked")
        Map<String, Long> typeBreakdown = (Map<String, Long>) progress.get("typeBreakdown");
        assertEquals(2L, typeBreakdown.get("IMAGE"));
        assertEquals(1L, typeBreakdown.get("VIDEO"));
    }

    @Test
    @DisplayName("getTaskProgress handles all-COMPLETED annotations")
    void getTaskProgress_allCompleted() {
        MediaAnnotation a = annotation();
        a.setStatus("COMPLETED");
        a.setConfidence(0.95);
        when(mediaAnnotationMapper.selectByTaskId(10L)).thenReturn(List.of(a));

        Map<String, Object> progress = service.getTaskProgress(10L);

        assertEquals(1, progress.get("totalCount"));
        assertEquals(1L, progress.get("completedCount"));
        assertEquals(100.0, progress.get("completionRate"));
    }

    // ===== validateBoundingBoxes =====

    @Test
    @DisplayName("validateBoundingBoxes returns empty list when annotation has no boxes")
    void validateBoundingBoxes_noBoxes() {
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(annotation());

        List<Map<String, Object>> issues = service.validateBoundingBoxes(1L);

        assertTrue(issues.isEmpty());
    }

    @Test
    @DisplayName("validateBoundingBoxes returns empty list when boxes is empty array")
    void validateBoundingBoxes_emptyArray() {
        MediaAnnotation a = annotation();
        a.setBoundingBoxes("[]");
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(a);

        List<Map<String, Object>> issues = service.validateBoundingBoxes(1L);

        assertTrue(issues.isEmpty());
    }

    @Test
    @DisplayName("validateBoundingBoxes detects zero/negative dimensions")
    void validateBoundingBoxes_zeroDimensions() {
        MediaAnnotation a = annotation();
        a.setBoundingBoxes("[{\"x\":0,\"y\":0,\"width\":0,\"height\":0}]");
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(a);

        List<Map<String, Object>> issues = service.validateBoundingBoxes(1L);

        assertEquals(1, issues.size());
        assertEquals(0, issues.get(0).get("boxIndex"));
    }

    @Test
    @DisplayName("validateBoundingBoxes detects negative coordinates")
    void validateBoundingBoxes_negativeCoords() {
        MediaAnnotation a = annotation();
        a.setBoundingBoxes("[{\"x\":-5,\"y\":0,\"width\":10,\"height\":10}]");
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(a);

        List<Map<String, Object>> issues = service.validateBoundingBoxes(1L);

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).get("issue").toString().contains("negative"));
    }

    @Test
    @DisplayName("validateBoundingBoxes accepts valid boxes")
    void validateBoundingBoxes_validBoxes() {
        MediaAnnotation a = annotation();
        a.setBoundingBoxes("[{\"x\":10,\"y\":10,\"width\":100,\"height\":100}]");
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(a);

        List<Map<String, Object>> issues = service.validateBoundingBoxes(1L);

        assertTrue(issues.isEmpty());
    }

    @Test
    @DisplayName("validateBoundingBoxes returns parse error for invalid JSON")
    void validateBoundingBoxes_invalidJson() {
        MediaAnnotation a = annotation();
        a.setBoundingBoxes("not-json");
        when(mediaAnnotationMapper.selectById(1L)).thenReturn(a);

        List<Map<String, Object>> issues = service.validateBoundingBoxes(1L);

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).get("issue").toString().contains("Failed to parse"));
    }

    // ===== autoGenerateAnnotation =====

    @Test
    @DisplayName("autoGenerateAnnotation for IMAGE sets frameCount=1")
    void autoGenerateAnnotation_image() {
        MediaAnnotation result = service.autoGenerateAnnotation(10L, "IMAGE", "/file/img.png", 100L);

        assertEquals(10L, result.getTaskId());
        assertEquals("IMAGE", result.getMediaType());
        assertEquals("/file/img.png", result.getMediaUrl());
        assertEquals(100L, result.getAnnotatedBy());
        assertEquals("AUTO_GENERATED", result.getStatus());
        assertEquals(0.5, result.getConfidence());
        assertEquals(1, result.getFrameCount());
        assertNull(result.getDurationSeconds());
        verify(mediaAnnotationMapper).insert(result);
    }

    @Test
    @DisplayName("autoGenerateAnnotation for VIDEO sets frameCount=0 and durationSeconds=0")
    void autoGenerateAnnotation_video() {
        MediaAnnotation result = service.autoGenerateAnnotation(10L, "VIDEO", "/file/v.mp4", 100L);

        assertEquals("VIDEO", result.getMediaType());
        assertEquals(0, result.getFrameCount());
        assertEquals(0L, result.getDurationSeconds());
    }

    @Test
    @DisplayName("autoGenerateAnnotation for AUDIO sets frameCount=0")
    void autoGenerateAnnotation_audio() {
        MediaAnnotation result = service.autoGenerateAnnotation(10L, "AUDIO", "/file/a.mp3", 100L);

        assertEquals("AUDIO", result.getMediaType());
        assertEquals(0, result.getFrameCount());
        assertNull(result.getDurationSeconds());
    }
}
