package com.river.agi.collection.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.mapper.AnnotationMapper;
import com.river.agi.annotation.mapper.AnnotationTaskMapper;
import com.river.agi.collection.entity.CollectionTask;
import com.river.agi.collection.mapper.CollectionTaskMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.dataset.service.DatasetParserService;
import com.river.agi.dataset.service.LocalStorageService;
import com.river.agi.media.entity.MediaAnnotation;
import com.river.agi.media.mapper.MediaAnnotationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionTaskServiceTest {

    @Mock private CollectionTaskMapper taskMapper;
    @Mock private DatasetMapper datasetMapper;
    @Mock private LocalStorageService localStorageService;
    @Mock private SecurityUtils securityUtils;
    @Mock private DatasetDataReaderService dataReader;
    @Mock private DatasetParserService datasetParserService;
    @Mock private MediaAnnotationMapper mediaAnnotationMapper;
    @Mock private AnnotationTaskMapper annotationTaskMapper;
    @Mock private AnnotationMapper annotationMapper;
    @Mock private Authentication authentication;

    private CollectionTaskService service;

    @BeforeEach
    void setUp() {
        service = new CollectionTaskService(taskMapper, datasetMapper, localStorageService,
                securityUtils, dataReader, datasetParserService, mediaAnnotationMapper,
                annotationTaskMapper, annotationMapper);
    }

    private CollectionTask task() {
        CollectionTask t = new CollectionTask();
        t.setId(1L);
        t.setName("test-task");
        t.setSourceType("DATASET");
        t.setDatasetId(42L);
        t.setLabelSchemaId(7L);
        return t;
    }

    private Dataset dataset() {
        Dataset d = new Dataset();
        d.setId(42L);
        d.setName("test-dataset");
        d.setStatus("PARSED");
        d.setRowCount(100);
        d.setFileType("csv");
        d.setFileUrl("file://test.csv");
        d.setColumnCount(3);
        return d;
    }

    // ===== list =====

    @Test
    @DisplayName("list returns paged tasks")
    void list_returnsPaged() {
        Page<CollectionTask> page = new Page<>(1, 10);
        page.setRecords(List.of(task()));
        page.setTotal(1L);
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.list(1, 10);

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
    }

    @Test
    @DisplayName("list returns empty page when no tasks")
    void list_empty() {
        Page<CollectionTask> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.list(1, 10);

        assertTrue(result.getRecords().isEmpty());
    }

    // ===== create =====

    @Test
    @DisplayName("create throws when name is blank")
    void create_blankName() {
        CollectionTask t = task();
        t.setName("  ");
        assertThrows(BusinessException.class, () -> service.create(t, authentication));
    }

    @Test
    @DisplayName("create throws when name is null")
    void create_nullName() {
        CollectionTask t = task();
        t.setName(null);
        assertThrows(BusinessException.class, () -> service.create(t, authentication));
    }

    @Test
    @DisplayName("create throws when sourceType is blank")
    void create_blankSourceType() {
        CollectionTask t = task();
        t.setSourceType("");
        assertThrows(BusinessException.class, () -> service.create(t, authentication));
    }

    @Test
    @DisplayName("create throws when datasetId set but dataset not found")
    void create_datasetNotFound() {
        CollectionTask t = task();
        when(datasetMapper.selectById(42L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(t, authentication));
        assertTrue(ex.getMessage().contains("数据集不存在"));
    }

    @Test
    @DisplayName("create sets READY status when dataset and labelSchema present")
    void create_readyStatus() {
        CollectionTask t = task();
        when(datasetMapper.selectById(42L)).thenReturn(dataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        CollectionTask result = service.create(t, authentication);

        assertEquals("READY", result.getStatus());
        assertEquals("SINGLE", result.getCollaborationMode());
        assertEquals(100, result.getTotalItems());
        assertEquals(0, result.getCompletedItems());
        assertEquals(100L, result.getCreatedBy());
        assertNotNull(result.getCreatedAt());
        verify(taskMapper).insert(t);
    }

    @Test
    @DisplayName("create sets DRAFT status when datasetId is null")
    void create_draftStatusWhenNoDataset() {
        CollectionTask t = task();
        t.setDatasetId(null);
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        CollectionTask result = service.create(t, authentication);

        assertEquals("DRAFT", result.getStatus());
        assertEquals(0, result.getTotalItems());
    }

    @Test
    @DisplayName("create sets DRAFT status when labelSchemaId is null")
    void create_draftStatusWhenNoSchema() {
        CollectionTask t = task();
        t.setLabelSchemaId(null);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        CollectionTask result = service.create(t, authentication);

        assertEquals("DRAFT", result.getStatus());
    }

    @Test
    @DisplayName("create uses provided collaborationMode when set")
    void create_usesProvidedCollaborationMode() {
        CollectionTask t = task();
        t.setCollaborationMode("COLLABORATIVE");
        when(datasetMapper.selectById(42L)).thenReturn(dataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        CollectionTask result = service.create(t, authentication);

        assertEquals("COLLABORATIVE", result.getCollaborationMode());
    }

    @Test
    @DisplayName("create preserves provided totalItems when non-zero")
    void create_preservesTotalItems() {
        CollectionTask t = task();
        t.setTotalItems(50);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        CollectionTask result = service.create(t, authentication);

        assertEquals(50, result.getTotalItems());
    }

    @Test
    @DisplayName("create handles dataset with null rowCount")
    void create_datasetNullRowCount() {
        CollectionTask t = task();
        Dataset d = dataset();
        d.setRowCount(null);
        when(datasetMapper.selectById(42L)).thenReturn(d);
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        CollectionTask result = service.create(t, authentication);

        assertEquals(0, result.getTotalItems());
    }

    // ===== get =====

    @Test
    @DisplayName("get returns task when found")
    void get_found() {
        when(taskMapper.selectById(1L)).thenReturn(task());

        CollectionTask result = service.get(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("get throws when task not found")
    void get_notFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.get(99L));
    }

    // ===== update =====

    @Test
    @DisplayName("update applies non-null fields")
    void update_appliesFields() {
        CollectionTask existing = task();
        existing.setStatus("DRAFT");
        when(taskMapper.selectById(1L)).thenReturn(existing);

        CollectionTask updates = new CollectionTask();
        updates.setName("new-name");
        updates.setLabelSchemaId(99L);
        updates.setCleaningConfigJson("{\"removeEmpty\":true}");
        updates.setAnnotationRuleJson("{\"rule\":\"x\"}");
        updates.setCollaborationMode("COLLABORATIVE");
        updates.setAssignedAnnotators("alice,bob");
        updates.setStatus("READY");

        CollectionTask result = service.update(1L, updates);

        assertEquals("new-name", result.getName());
        assertEquals(99L, result.getLabelSchemaId());
        assertEquals("{\"removeEmpty\":true}", result.getCleaningConfigJson());
        assertEquals("{\"rule\":\"x\"}", result.getAnnotationRuleJson());
        assertEquals("COLLABORATIVE", result.getCollaborationMode());
        assertEquals("alice,bob", result.getAssignedAnnotators());
        assertEquals("READY", result.getStatus());
        assertNotNull(result.getUpdatedAt());
        verify(taskMapper).updateById(existing);
    }

    @Test
    @DisplayName("update throws when task not found")
    void update_notFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.update(99L, new CollectionTask()));
    }

    // ===== delete =====

    @Test
    @DisplayName("delete removes media and task")
    void delete_delegates() {
        when(taskMapper.selectById(1L)).thenReturn(task());

        service.delete(1L);

        verify(mediaAnnotationMapper).delete(any());
        verify(taskMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete throws when task not found")
    void delete_notFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.delete(99L));
    }

    // ===== refreshProgress =====

    @Test
    @DisplayName("refreshProgress throws when task not found")
    void refreshProgress_notFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.refreshProgress(99L));
    }

    @Test
    @DisplayName("refreshProgress syncs from annotation task when dataset present")
    void refreshProgress_syncsFromAnnotationTask() {
        CollectionTask t = task();
        t.setTotalItems(100);
        t.setCompletedItems(0);
        when(taskMapper.selectById(1L)).thenReturn(t);

        AnnotationTask at = new AnnotationTask();
        at.setId(50L);
        when(annotationTaskMapper.selectOne(any())).thenReturn(at);
        when(annotationMapper.selectCount(any())).thenReturn(50L);

        CollectionTask result = service.refreshProgress(1L);

        assertEquals(100, result.getTotalItems());
        assertEquals(50, result.getCompletedItems());
        assertEquals("RUNNING", result.getStatus());
        verify(taskMapper).updateById(t);
    }

    @Test
    @DisplayName("refreshProgress marks COMPLETED when annotations reach total")
    void refreshProgress_completed() {
        CollectionTask t = task();
        t.setTotalItems(100);
        t.setCompletedItems(0);
        when(taskMapper.selectById(1L)).thenReturn(t);

        AnnotationTask at = new AnnotationTask();
        at.setId(50L);
        when(annotationTaskMapper.selectOne(any())).thenReturn(at);
        when(annotationMapper.selectCount(any())).thenReturn(100L);

        CollectionTask result = service.refreshProgress(1L);

        assertEquals(100, result.getCompletedItems());
        assertEquals("COMPLETED", result.getStatus());
    }

    @Test
    @DisplayName("refreshProgress syncs total from dataset when total is 0")
    void refreshProgress_syncsTotalFromDataset() {
        CollectionTask t = task();
        t.setTotalItems(0);
        t.setCompletedItems(0);
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());

        AnnotationTask at = new AnnotationTask();
        at.setId(50L);
        when(annotationTaskMapper.selectOne(any())).thenReturn(at);
        when(annotationMapper.selectCount(any())).thenReturn(30L);

        CollectionTask result = service.refreshProgress(1L);

        assertEquals(100, result.getTotalItems());
        assertEquals(30, result.getCompletedItems());
    }

    @Test
    @DisplayName("refreshProgress uses mediaAnnotationMapper when dataset is null")
    void refreshProgress_usesMediaWhenNoDataset() {
        CollectionTask t = task();
        t.setDatasetId(null);
        t.setTotalItems(10);
        t.setCompletedItems(0);
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(mediaAnnotationMapper.selectCount(any())).thenReturn(10L);

        CollectionTask result = service.refreshProgress(1L);

        assertEquals(10, result.getCompletedItems());
        assertEquals("COMPLETED", result.getStatus());
    }

    @Test
    @DisplayName("refreshProgress handles null totalItems")
    void refreshProgress_nullTotalItems() {
        CollectionTask t = task();
        t.setTotalItems(null);
        t.setCompletedItems(null);
        t.setDatasetId(null);
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(mediaAnnotationMapper.selectCount(any())).thenReturn(5L);

        CollectionTask result = service.refreshProgress(1L);

        assertEquals(0, result.getTotalItems());
        assertEquals(5, result.getCompletedItems());
    }

    @Test
    @DisplayName("refreshProgress skips annotation sync when no annotation task found")
    void refreshProgress_noAnnotationTask() {
        CollectionTask t = task();
        t.setTotalItems(100);
        t.setCompletedItems(5);
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(annotationTaskMapper.selectOne(any())).thenReturn(null);

        CollectionTask result = service.refreshProgress(1L);

        assertEquals(100, result.getTotalItems());
        assertEquals(5, result.getCompletedItems());
    }

    // ===== cleanPreview =====

    @Test
    @DisplayName("cleanPreview throws when dataset not found")
    void cleanPreview_datasetNotFound() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.cleanPreview(1L, Map.of()));
    }

    @Test
    @DisplayName("cleanPreview returns summary with row counts")
    void cleanPreview_returnsSummary() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());

        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> r1 = new LinkedHashMap<>();
        r1.put("name", "alice");
        r1.put("age", "30");
        rows.add(r1);
        Map<String, String> r2 = new LinkedHashMap<>();
        r2.put("name", "bob");
        r2.put("age", "25");
        rows.add(r2);
        when(dataReader.readRows(any())).thenReturn(rows);

        Map<String, Object> summary = service.cleanPreview(1L, Map.of("removeEmpty", true, "removeDuplicate", true));

        assertEquals(2, summary.get("inputRows"));
        assertEquals(3, summary.get("inputColumns"));
        assertEquals(0, summary.get("emptyRows"));
        assertEquals(0, summary.get("duplicateRows"));
        assertEquals(2, summary.get("outputRows"));
        assertNotNull(summary.get("previewRows"));
        assertNotNull(summary.get("previewFields"));
        assertEquals("CLEANED", t.getStatus());
        verify(taskMapper).updateById(t);
    }

    @Test
    @DisplayName("cleanPreview detects duplicates and empty rows")
    void cleanPreview_detectsDuplicates() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());

        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> r1 = new LinkedHashMap<>();
        r1.put("name", "alice");
        rows.add(r1);
        // duplicate
        Map<String, String> r2 = new LinkedHashMap<>();
        r2.put("name", "alice");
        rows.add(r2);
        // empty
        Map<String, String> r3 = new LinkedHashMap<>();
        r3.put("name", "");
        rows.add(r3);
        when(dataReader.readRows(any())).thenReturn(rows);

        Map<String, Object> summary = service.cleanPreview(1L, Map.of("removeEmpty", true, "removeDuplicate", true));

        assertEquals(1, summary.get("emptyRows"));
        assertEquals(1, summary.get("duplicateRows"));
        assertEquals(1, summary.get("outputRows"));
    }

    @Test
    @DisplayName("cleanPreview handles empty input")
    void cleanPreview_emptyInput() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());
        when(dataReader.readRows(any())).thenReturn(List.of());

        Map<String, Object> summary = service.cleanPreview(1L, null);

        assertEquals(0, summary.get("inputRows"));
        assertEquals(0, summary.get("outputRows"));
        assertEquals(List.of(), summary.get("previewFields"));
        assertEquals(Map.of(), summary.get("actions"));
    }

    @Test
    @DisplayName("cleanPreview validates format when configured")
    void cleanPreview_validateFormat() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());

        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> r1 = new LinkedHashMap<>();
        r1.put("name", "alice");
        rows.add(r1);
        // invalid — value too long
        Map<String, String> r2 = new LinkedHashMap<>();
        r2.put("name", "x".repeat(20000));
        rows.add(r2);
        when(dataReader.readRows(any())).thenReturn(rows);

        Map<String, Object> summary = service.cleanPreview(1L, Map.of("validateFormat", true));

        assertEquals(1, summary.get("invalidRows"));
    }

    // ===== applyCleaning =====

    @Test
    @DisplayName("applyCleaning throws when dataset not found")
    void applyCleaning_datasetNotFound() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.applyCleaning(1L, Map.of(), authentication));
    }

    @Test
    @DisplayName("applyCleaning throws when input is empty")
    void applyCleaning_emptyInput() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(dataset());
        when(dataReader.readRows(any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.applyCleaning(1L, Map.of(), authentication));
    }

    @Test
    @DisplayName("applyCleaning produces cleaned dataset and result")
    void applyCleaning_happyPath() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        Dataset source = dataset();
        when(datasetMapper.selectById(42L)).thenReturn(source);
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);
        when(localStorageService.writeFile(any(), any())).thenReturn("cleaned-uuid.csv");
        when(localStorageService.fileUrl(any())).thenReturn("http://test/files/cleaned-uuid.csv");

        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> r1 = new LinkedHashMap<>();
        r1.put("name", "alice");
        rows.add(r1);
        Map<String, String> r2 = new LinkedHashMap<>();
        r2.put("name", "");
        rows.add(r2); // empty
        when(dataReader.readRows(any())).thenReturn(rows);

        Map<String, Object> result = service.applyCleaning(1L, Map.of("removeEmpty", true), authentication);

        assertEquals(2, result.get("inputRows"));
        assertEquals(1, result.get("outputRows"));
        assertEquals(1, result.get("emptyRows"));
        assertEquals(0, result.get("duplicateRows"));
        assertEquals("http://test/files/cleaned-uuid.csv", result.get("fileUrl"));
        verify(datasetMapper).insert(any());
        verify(datasetParserService).parseDataset(any());
        verify(taskMapper).updateById(t);
        assertEquals("CLEANED", t.getStatus());
    }

    @Test
    @DisplayName("applyCleaning handles removeDuplicate and validateFormat")
    void applyCleaning_duplicateAndFormat() {
        CollectionTask t = task();
        when(taskMapper.selectById(1L)).thenReturn(t);
        Dataset source = dataset();
        when(datasetMapper.selectById(42L)).thenReturn(source);
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);
        when(localStorageService.writeFile(any(), any())).thenReturn("cleaned.csv");
        when(localStorageService.fileUrl(any())).thenReturn("http://test/files/cleaned.csv");

        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> r1 = new LinkedHashMap<>();
        r1.put("name", "alice");
        rows.add(r1);
        // duplicate
        Map<String, String> r2 = new LinkedHashMap<>();
        r2.put("name", "alice");
        rows.add(r2);
        // invalid (too long)
        Map<String, String> r3 = new LinkedHashMap<>();
        r3.put("name", "x".repeat(20000));
        rows.add(r3);
        when(dataReader.readRows(any())).thenReturn(rows);

        Map<String, Object> result = service.applyCleaning(1L,
                Map.of("removeDuplicate", true, "validateFormat", true), authentication);

        assertEquals(3, result.get("inputRows"));
        assertEquals(1, result.get("outputRows"));
        assertEquals(1, result.get("duplicateRows"));
        assertEquals(1, result.get("invalidRows"));
    }

    // ===== uploadMedia =====

    @Test
    @DisplayName("uploadMedia throws when file is null")
    void uploadMedia_nullFile() {
        assertThrows(BusinessException.class, () -> service.uploadMedia(null));
    }

    @Test
    @DisplayName("uploadMedia throws when file is empty")
    void uploadMedia_emptyFile() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.uploadMedia(file));
    }

    @Test
    @DisplayName("uploadMedia returns IMAGE type for png")
    void uploadMedia_image() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getSize()).thenReturn(1024L);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/photo.png");

        Map<String, Object> result = service.uploadMedia(file);

        assertEquals("photo.png", result.get("name"));
        assertEquals("IMAGE", result.get("mediaType"));
        assertEquals("http://test/photo.png", result.get("url"));
        assertEquals(1024L, result.get("size"));
    }

    @Test
    @DisplayName("uploadMedia returns VIDEO type for mp4")
    void uploadMedia_video() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("clip.mp4");
        when(file.getSize()).thenReturn(5L * 1024 * 1024);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/clip.mp4");

        Map<String, Object> result = service.uploadMedia(file);

        assertEquals("VIDEO", result.get("mediaType"));
    }

    @Test
    @DisplayName("uploadMedia returns AUDIO type for mp3")
    void uploadMedia_audio() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("song.mp3");
        when(file.getSize()).thenReturn(2L * 1024 * 1024);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/song.mp3");

        Map<String, Object> result = service.uploadMedia(file);

        assertEquals("AUDIO", result.get("mediaType"));
    }

    @Test
    @DisplayName("uploadMedia handles mov as VIDEO type")
    void uploadMedia_mov() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("clip.mov");
        when(file.getSize()).thenReturn(5L * 1024 * 1024);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/clip.mov");

        Map<String, Object> result = service.uploadMedia(file);

        assertEquals("VIDEO", result.get("mediaType"));
    }

    @Test
    @DisplayName("uploadMedia handles wav as AUDIO type")
    void uploadMedia_wav() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("song.wav");
        when(file.getSize()).thenReturn(2L * 1024 * 1024);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/song.wav");

        Map<String, Object> result = service.uploadMedia(file);

        assertEquals("AUDIO", result.get("mediaType"));
    }

    // ===== attachMedia =====

    @Test
    @DisplayName("attachMedia throws when mediaType is null")
    void attachMedia_nullMediaType() {
        when(taskMapper.selectById(1L)).thenReturn(task());

        assertThrows(BusinessException.class,
                () -> service.attachMedia(1L, null, "url", authentication));
    }

    @Test
    @DisplayName("attachMedia throws when mediaUrl is blank")
    void attachMedia_blankUrl() {
        when(taskMapper.selectById(1L)).thenReturn(task());

        assertThrows(BusinessException.class,
                () -> service.attachMedia(1L, "IMAGE", "  ", authentication));
    }

    @Test
    @DisplayName("attachMedia creates annotation and updates task")
    void attachMedia_createsAnnotation() {
        CollectionTask t = task();
        t.setTotalItems(0);
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        MediaAnnotation result = service.attachMedia(1L, "IMAGE", "/file/test.png", authentication);

        assertEquals(1L, result.getTaskId());
        assertEquals("IMAGE", result.getMediaType());
        assertEquals("/file/test.png", result.getMediaUrl());
        assertEquals(100L, result.getAnnotatedBy());
        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getCreatedAt());
        verify(mediaAnnotationMapper).insert(result);
        // task should be updated with media type and incremented totalItems
        assertEquals("IMAGE", t.getMediaType());
        assertEquals(1, t.getTotalItems());
        assertEquals("READY", t.getStatus());
        verify(taskMapper).updateById(t);
    }

    @Test
    @DisplayName("attachMedia increments existing totalItems")
    void attachMedia_incrementsTotalItems() {
        CollectionTask t = task();
        t.setTotalItems(5);
        when(taskMapper.selectById(1L)).thenReturn(t);
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(100L);

        service.attachMedia(1L, "VIDEO", "/file/v.mp4", authentication);

        assertEquals(6, t.getTotalItems());
    }
}
