package com.river.agi.annotation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.annotation.entity.Annotation;
import com.river.agi.annotation.entity.AnnotationHistory;
import com.river.agi.annotation.entity.AnnotationQualityRule;
import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.entity.AnnotationTaskAssignee;
import com.river.agi.annotation.entity.LabelSchema;
import com.river.agi.annotation.mapper.AnnotationHistoryMapper;
import com.river.agi.annotation.mapper.AnnotationMapper;
import com.river.agi.annotation.mapper.AnnotationQualityRuleMapper;
import com.river.agi.annotation.mapper.AnnotationTaskAssigneeMapper;
import com.river.agi.annotation.mapper.AnnotationTaskMapper;
import com.river.agi.annotation.mapper.LabelSchemaMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.SecurityUtils;
import com.river.agi.config.entity.SystemConfig;
import com.river.agi.config.mapper.SystemConfigMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.dataset.service.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("标注服务测试")
class AnnotationServiceTest {

    @Mock private LabelSchemaMapper labelSchemaMapper;
    @Mock private AnnotationTaskMapper annotationTaskMapper;
    @Mock private AnnotationMapper annotationMapper;
    @Mock private DatasetMapper datasetMapper;
    @Mock private SecurityUtils securityUtils;
    @Mock private DatasetDataReaderService dataReader;
    @Mock private AnnotationQualityRuleMapper qualityRuleMapper;
    @Mock private AnnotationHistoryMapper historyMapper;
    @Mock private SystemConfigMapper systemConfigMapper;
    @Mock private AnnotationTaskAssigneeMapper assigneeMapper;
    @Mock private LocalStorageService localStorageService;
    @Mock private Authentication authentication;

    private ObjectMapper objectMapper;
    private AnnotationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AnnotationService(labelSchemaMapper, annotationTaskMapper, annotationMapper,
                datasetMapper, securityUtils, objectMapper, dataReader, qualityRuleMapper,
                historyMapper, systemConfigMapper);
        service.setLocalStorageService(localStorageService);
        ReflectionTestUtils.setField(service, "assigneeMapper", assigneeMapper);
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
    }

    private AnnotationService serviceWithoutAssignee() {
        return new AnnotationService(labelSchemaMapper, annotationTaskMapper, annotationMapper,
                datasetMapper, securityUtils, objectMapper, dataReader, qualityRuleMapper,
                historyMapper, systemConfigMapper);
    }

    private AnnotationService serviceWithoutHistoryAndConfig() {
        return new AnnotationService(labelSchemaMapper, annotationTaskMapper, annotationMapper,
                datasetMapper, securityUtils, objectMapper, dataReader, qualityRuleMapper);
    }

    private LabelSchema label(Long id, String code, String name, Long parentId) {
        LabelSchema l = new LabelSchema();
        l.setId(id);
        l.setCode(code);
        l.setName(name);
        l.setParentId(parentId);
        l.setDeleted(0);
        return l;
    }

    private AnnotationTask task(Long id, Long datasetId, Long schemaId) {
        AnnotationTask t = new AnnotationTask();
        t.setId(id);
        t.setDatasetId(datasetId);
        t.setLabelSchemaId(schemaId);
        t.setStatus("PENDING");
        t.setTotalRows(10);
        t.setReviewCount(0);
        t.setArbitrationCount(0);
        return t;
    }

    private Annotation annotation(Long id, Long taskId, String status, String labelCode) {
        Annotation a = new Annotation();
        a.setId(id);
        a.setTaskId(taskId);
        a.setRowIndex(0);
        a.setStatus(status);
        a.setLabelCode(labelCode);
        a.setConfidence(new BigDecimal("0.90"));
        a.setIsCorrected(false);
        return a;
    }

    // ---- Label Schema CRUD ----

    @Test
    @DisplayName("创建标签体系")
    void createLabelSchema_success() {
        LabelSchema schema = new LabelSchema();
        schema.setName("情感分类");
        schema.setCode("SENTIMENT");
        when(labelSchemaMapper.insert(any())).thenReturn(1);

        LabelSchema result = service.createLabelSchema(schema);

        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        verify(labelSchemaMapper).insert(schema);
    }

    @Test
    @DisplayName("获取标签体系分页 - 已存在默认体系")
    void getLabelSchemas_existingDefault() {
        when(labelSchemaMapper.selectOne(any())).thenReturn(label(1L, "DEFAULT_DATA_CLASSIFICATION", "通用", null));
        Page<LabelSchema> page = new Page<>();
        page.setRecords(List.of(label(1L, "DEFAULT_DATA_CLASSIFICATION", "通用", null)));
        page.setTotal(1L);
        when(labelSchemaMapper.selectPage(any(), any())).thenReturn(page);

        assertNotNull(service.getLabelSchemas(1, 10));
        verify(labelSchemaMapper, never()).insert(any());
    }

    @Test
    @DisplayName("获取标签体系分页 - 初始化默认体系")
    void getLabelSchemas_seedsDefault() {
        when(labelSchemaMapper.selectOne(any())).thenReturn(null);
        Page<LabelSchema> page = new Page<>();
        page.setRecords(List.of());
        page.setTotal(0L);
        when(labelSchemaMapper.selectPage(any(), any())).thenReturn(page);

        assertNotNull(service.getLabelSchemas(1, 10));
        // root + 8 children
        verify(labelSchemaMapper, times(9)).insert(any());
    }

    @Test
    @DisplayName("获取标签体系 - 找到")
    void getLabelSchema_found() {
        when(labelSchemaMapper.selectById(1L)).thenReturn(label(1L, "A", "a", null));
        assertNotNull(service.getLabelSchema(1L));
    }

    @Test
    @DisplayName("获取标签体系 - 未找到")
    void getLabelSchema_notFound() {
        when(labelSchemaMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getLabelSchema(1L));
    }

    @Test
    @DisplayName("获取子标签")
    void getChildLabels() {
        when(labelSchemaMapper.selectByParentId(1L)).thenReturn(List.of(label(2L, "phone", "手机", 1L)));
        assertEquals(1, service.getChildLabels(1L).size());
    }

    @Test
    @DisplayName("获取质量规则 - 已存在规则")
    void getQualityRules_existing() {
        when(qualityRuleMapper.selectCount(any())).thenReturn(2L);
        when(qualityRuleMapper.selectList(any())).thenReturn(List.of(new AnnotationQualityRule()));
        assertEquals(1, service.getQualityRules().size());
        verify(qualityRuleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("获取质量规则 - 初始化默认规则")
    void getQualityRules_seedsDefaults() {
        when(qualityRuleMapper.selectCount(any())).thenReturn(0L);
        when(qualityRuleMapper.selectList(any())).thenReturn(List.of(new AnnotationQualityRule()));
        service.getQualityRules();
        verify(qualityRuleMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("保存质量规则 - 名称为空抛异常")
    void saveQualityRule_blankName() {
        AnnotationQualityRule rule = new AnnotationQualityRule();
        rule.setName("");
        rule.setCode("X");
        assertThrows(BusinessException.class, () -> service.saveQualityRule(rule));
    }

    @Test
    @DisplayName("保存质量规则 - 编码为空抛异常")
    void saveQualityRule_blankCode() {
        AnnotationQualityRule rule = new AnnotationQualityRule();
        rule.setName("n");
        rule.setCode(" ");
        assertThrows(BusinessException.class, () -> service.saveQualityRule(rule));
    }

    @Test
    @DisplayName("保存质量规则 - 新增并补默认值")
    void saveQualityRule_insert() {
        AnnotationQualityRule rule = new AnnotationQualityRule();
        rule.setName("规则");
        rule.setCode("R1");
        when(qualityRuleMapper.insert(any())).thenReturn(1);
        AnnotationQualityRule saved = service.saveQualityRule(rule);
        assertNotNull(saved.getCreatedAt());
        assertEquals(Integer.valueOf(100), saved.getPriority());
        assertTrue(saved.getEnabled());
        assertEquals("1.0.0", saved.getVersion());
        verify(qualityRuleMapper).insert(rule);
    }

    @Test
    @DisplayName("保存质量规则 - 更新")
    void saveQualityRule_update() {
        AnnotationQualityRule rule = new AnnotationQualityRule();
        rule.setId(5L);
        rule.setName("规则");
        rule.setCode("R1");
        rule.setPriority(50);
        rule.setEnabled(false);
        when(qualityRuleMapper.updateById(any())).thenReturn(1);
        service.saveQualityRule(rule);
        verify(qualityRuleMapper).updateById(rule);
    }

    @Test
    @DisplayName("删除质量规则 - 未找到")
    void deleteQualityRule_notFound() {
        when(qualityRuleMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteQualityRule(1L));
    }

    @Test
    @DisplayName("删除质量规则 - 软删除")
    void deleteQualityRule_success() {
        AnnotationQualityRule rule = new AnnotationQualityRule();
        rule.setId(1L);
        when(qualityRuleMapper.selectById(1L)).thenReturn(rule);
        when(qualityRuleMapper.updateById(any())).thenReturn(1);
        service.deleteQualityRule(1L);
        assertEquals(1, rule.getDeleted());
        verify(qualityRuleMapper).updateById(rule);
    }

    @Test
    @DisplayName("删除标签体系")
    void deleteLabelSchema() {
        when(labelSchemaMapper.deleteById(1L)).thenReturn(1);
        assertDoesNotThrow(() -> service.deleteLabelSchema(1L));
    }

    // ---- Annotation Task CRUD ----

    @Test
    @DisplayName("创建标注任务 - 数据集不存在")
    void createAnnotationTask_datasetNotFound() {
        when(datasetMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createAnnotationTask(new AnnotationTask(), authentication));
    }

    @Test
    @DisplayName("创建标注任务 - 成功")
    void createAnnotationTask_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setRowCount(100);
        when(datasetMapper.selectById(any())).thenReturn(dataset);
        AnnotationTask task = new AnnotationTask();
        task.setDatasetId(1L);
        when(annotationTaskMapper.insert(any())).thenReturn(1);

        AnnotationTask result = service.createAnnotationTask(task, authentication);

        assertEquals("PENDING", result.getStatus());
        assertEquals(100, result.getTotalRows());
        assertEquals(0, result.getCompletedRows());
        assertEquals(1.0, result.getPassRate());
        verify(annotationTaskMapper).insert(task);
    }

    @Test
    @DisplayName("获取标注任务分页")
    void getAnnotationTasks() {
        Page<AnnotationTask> page = new Page<>();
        page.setRecords(List.of(task(1L, 1L, 1L)));
        page.setTotal(1L);
        when(annotationTaskMapper.selectPage(any(), any())).thenReturn(page);
        assertEquals(1, service.getAnnotationTasks(1, 10).getRecords().size());
    }

    @Test
    @DisplayName("获取标注任务 - 找到")
    void getAnnotationTask_found() {
        when(annotationTaskMapper.selectById(1L)).thenReturn(task(1L, 1L, 1L));
        assertNotNull(service.getAnnotationTask(1L));
    }

    @Test
    @DisplayName("获取标注任务 - 未找到")
    void getAnnotationTask_notFound() {
        when(annotationTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getAnnotationTask(1L));
    }

    @Test
    @DisplayName("删除标注任务")
    void deleteAnnotationTask() {
        when(annotationMapper.delete(any())).thenReturn(1);
        when(annotationTaskMapper.deleteById(anyLong())).thenReturn(1);
        assertDoesNotThrow(() -> service.deleteAnnotationTask(1L));
    }

    @Test
    @DisplayName("获取标注列表")
    void getAnnotations() {
        when(annotationMapper.selectByTaskId(anyLong())).thenReturn(List.of(new Annotation()));
        assertEquals(1, service.getAnnotations(1L).size());
    }

    // ---- Pre-annotation ----

    @Test
    @DisplayName("预标注 - 任务不存在")
    void preAnnotate_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.preAnnotate(1L));
    }

    @Test
    @DisplayName("预标注 - 数据集不存在")
    void preAnnotate_datasetNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task(1L, 1L, 1L));
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.preAnnotate(1L));
    }

    @Test
    @DisplayName("预标注 - 读取数据失败")
    void preAnnotate_dataReaderThrows() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task(1L, 1L, 1L));
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenThrow(new RuntimeException("io error"));
        assertThrows(BusinessException.class, () -> service.preAnnotate(1L));
    }

    @Test
    @DisplayName("预标注 - 命中多种规则分支")
    void preAnnotate_successMultipleBranches() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        // labels with matching code "phone" to exercise getLabelDisplayName
        when(labelSchemaMapper.selectList(any())).thenReturn(List.of(label(2L, "phone", "手机号", 1L)));

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("phone", "13812345678"));   // columnMatch + valueMatch
        rows.add(Map.of("data", "13812345678"));    // valueMatch only
        rows.add(Map.of("phone", "abc"));            // columnMatch only
        rows.add(Map.of("x", "y"));                  // no match
        when(dataReader.readRows(dataset)).thenReturn(rows);

        service.preAnnotate(1L);

        // 3 matching rows inserted, 1 skipped
        verify(annotationMapper, times(3)).insert(any());
        verify(annotationTaskMapper).updateById(t);
        assertEquals("PRE_ANNOTATED", t.getStatus());
        assertEquals(3, t.getCompletedRows());
    }

    // ---- Submit / Review / Arbitrate ----

    @Test
    @DisplayName("提交标注 - 标注不存在")
    void submitAnnotation_annotationNotFound() {
        when(annotationMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitAnnotation(1L, "phone", "手机", "c", authentication));
    }

    @Test
    @DisplayName("提交标注 - 标签为空")
    void submitAnnotation_blankLabel() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "PRE_ANNOTATED", "old"));
        assertThrows(BusinessException.class, () -> service.submitAnnotation(1L, " ", "手机", "c", authentication));
    }

    @Test
    @DisplayName("提交标注 - 任务不存在")
    void submitAnnotation_taskNotFound() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "PRE_ANNOTATED", "old"));
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitAnnotation(1L, "phone", "手机", "c", authentication));
    }

    @Test
    @DisplayName("提交标注 - 标签非法")
    void submitAnnotation_invalidLabel() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "PRE_ANNOTATED", "old"));
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task(1L, 1L, 1L));
        when(labelSchemaMapper.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.submitAnnotation(1L, "phone", "手机", "c", authentication));
    }

    @Test
    @DisplayName("提交标注 - 标签变更并写入历史")
    void submitAnnotation_labelChange() {
        Annotation a = annotation(1L, 1L, "PRE_ANNOTATED", "old");
        a.setOriginalLabelCode(null);
        when(annotationMapper.selectById(1L)).thenReturn(a);
        AnnotationTask t = task(1L, 1L, 1L);
        t.setTotalRows(10);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(labelSchemaMapper.selectCount(any())).thenReturn(1L);
        when(annotationMapper.updateById(any())).thenReturn(1);
        when(annotationMapper.selectCount(any())).thenReturn(1L);

        Annotation result = service.submitAnnotation(1L, "phone", "手机号", "comment", authentication);

        assertEquals("SUBMITTED", result.getStatus());
        assertTrue(result.getIsCorrected());
        assertEquals("old", result.getOriginalLabelCode());
        assertEquals("CORRECTION", result.getAnnotationType());
        verify(historyMapper).insert(any());
    }

    @Test
    @DisplayName("提交标注 - 标签相同不变更")
    void submitAnnotation_sameLabel() {
        Annotation a = annotation(1L, 1L, "PRE_ANNOTATED", "phone");
        when(annotationMapper.selectById(1L)).thenReturn(a);
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(labelSchemaMapper.selectCount(any())).thenReturn(1L);
        when(annotationMapper.updateById(any())).thenReturn(1);
        when(annotationMapper.selectCount(any())).thenReturn(1L);

        Annotation result = service.submitAnnotation(1L, "phone", "手机号", "comment", authentication);

        assertFalse(result.getIsCorrected());
        assertNull(result.getCorrectedAt());
    }

    @Test
    @DisplayName("审核标注 - 标注不存在")
    void reviewAnnotation_notFound() {
        when(annotationMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reviewAnnotation(1L, "c", true, authentication));
    }

    @Test
    @DisplayName("审核标注 - 状态不可审核")
    void reviewAnnotation_badStatus() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "APPROVED", "phone"));
        assertThrows(BusinessException.class, () -> service.reviewAnnotation(1L, "c", true, authentication));
    }

    @Test
    @DisplayName("审核标注 - 驳回无原因")
    void reviewAnnotation_rejectNoComment() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "SUBMITTED", "phone"));
        assertThrows(BusinessException.class, () -> service.reviewAnnotation(1L, "  ", false, authentication));
    }

    @Test
    @DisplayName("审核标注 - 通过")
    void reviewAnnotation_approve() {
        Annotation a = annotation(1L, 1L, "SUBMITTED", "phone");
        when(annotationMapper.selectById(1L)).thenReturn(a);
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(annotationMapper.updateById(any())).thenReturn(1);
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        Annotation result = service.reviewAnnotation(1L, "ok", true, authentication);

        assertEquals("APPROVED", result.getStatus());
        verify(historyMapper).insert(any());
    }

    @Test
    @DisplayName("审核标注 - 驳回并回退任务")
    void reviewAnnotation_reject() {
        Annotation a = annotation(1L, 1L, "SUBMITTED", "phone");
        when(annotationMapper.selectById(1L)).thenReturn(a);
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(annotationMapper.updateById(any())).thenReturn(1);
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        Annotation result = service.reviewAnnotation(1L, "bad", false, authentication);

        assertEquals("REJECTED", result.getStatus());
        assertEquals("IN_PROGRESS", t.getStatus());
    }

    @Test
    @DisplayName("仲裁标注 - 标注不存在")
    void arbitrateAnnotation_notFound() {
        when(annotationMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.arbitrateAnnotation(1L, "phone", "手机", "c", authentication));
    }

    @Test
    @DisplayName("仲裁标注 - 状态不可仲裁")
    void arbitrateAnnotation_badStatus() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "APPROVED", "phone"));
        assertThrows(BusinessException.class, () -> service.arbitrateAnnotation(1L, "phone", "手机", "c", authentication));
    }

    @Test
    @DisplayName("仲裁标注 - 标签为空")
    void arbitrateAnnotation_blankLabel() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "IN_REVIEW", "phone"));
        assertThrows(BusinessException.class, () -> service.arbitrateAnnotation(1L, "", "手机", "c", authentication));
    }

    @Test
    @DisplayName("仲裁标注 - 任务不存在")
    void arbitrateAnnotation_taskNotFound() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "IN_REVIEW", "phone"));
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.arbitrateAnnotation(1L, "phone", "手机", "c", authentication));
    }

    @Test
    @DisplayName("仲裁标注 - 标签非法")
    void arbitrateAnnotation_invalidLabel() {
        when(annotationMapper.selectById(anyLong())).thenReturn(annotation(1L, 1L, "IN_REVIEW", "phone"));
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task(1L, 1L, 1L));
        when(labelSchemaMapper.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.arbitrateAnnotation(1L, "phone", "手机", "c", authentication));
    }

    @Test
    @DisplayName("仲裁标注 - 成功")
    void arbitrateAnnotation_success() {
        Annotation a = annotation(1L, 1L, "IN_REVIEW", "phone");
        when(annotationMapper.selectById(1L)).thenReturn(a);
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(labelSchemaMapper.selectCount(any())).thenReturn(1L);
        when(annotationMapper.updateById(any())).thenReturn(1);
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        Annotation result = service.arbitrateAnnotation(1L, "phone", "手机号", "c", authentication);

        assertEquals("ARBITRATED", result.getStatus());
        assertEquals("ARBITRATION", result.getAnnotationType());
        assertEquals(0, BigDecimal.ONE.compareTo(result.getConfidence()));
        assertEquals(1, t.getArbitrationCount());
    }

    // ---- History / Metrics ----

    @Test
    @DisplayName("获取标注历史 - 任务不存在")
    void getAnnotationHistory_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getAnnotationHistory(1L));
    }

    @Test
    @DisplayName("获取标注历史 - historyMapper 为空返回空列表")
    void getAnnotationHistory_noHistoryMapper() {
        AnnotationService svc = serviceWithoutHistoryAndConfig();
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task(1L, 1L, 1L));
        assertTrue(svc.getAnnotationHistory(1L).isEmpty());
    }

    @Test
    @DisplayName("获取标注历史 - 返回记录")
    void getAnnotationHistory_withRecords() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task(1L, 1L, 1L));
        AnnotationHistory h = new AnnotationHistory();
        h.setAction("SUBMIT");
        when(historyMapper.selectByTaskId(1L)).thenReturn(List.of(h));
        assertEquals(1, service.getAnnotationHistory(1L).size());
    }

    @Test
    @DisplayName("获取质量指标 - 任务不存在")
    void getAnnotationQualityMetrics_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getAnnotationQualityMetrics(1L));
    }

    @Test
    @DisplayName("获取质量指标 - 默认配置计算")
    void getAnnotationQualityMetrics_defaultConfig() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of());
        when(systemConfigMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> metrics = service.getAnnotationQualityMetrics(1L);

        assertEquals(0, metrics.get("totalAnnotations"));
        assertEquals("NEEDS_IMPROVEMENT", metrics.get("qualityLevel"));
        assertNotNull(metrics.get("qualityWeights"));
    }

    @Test
    @DisplayName("获取质量指标 - 自定义权重配置")
    void getAnnotationQualityMetrics_customConfig() throws Exception {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation a1 = annotation(1L, 1L, "APPROVED", "phone");
        a1.setConfidence(new BigDecimal("0.95"));
        Annotation a2 = annotation(2L, 1L, "SUBMITTED", "phone");
        a2.setConfidence(new BigDecimal("0.80"));
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(a1, a2));
        SystemConfig config = new SystemConfig();
        config.setConfigJson("{\"approveWeight\":0.5,\"correctionPenalty\":0.2,\"validationWeight\":0.2,\"consistencyWeight\":0.1}");
        when(systemConfigMapper.selectOne(any())).thenReturn(config);

        Map<String, Object> metrics = service.getAnnotationQualityMetrics(1L);

        assertEquals(2, metrics.get("totalAnnotations"));
        assertEquals(1L, metrics.get("approvedCount"));
        assertNotNull(metrics.get("qualityScore"));
    }

    @Test
    @DisplayName("获取质量指标 - 配置 JSON 非法回退默认")
    void getAnnotationQualityMetrics_invalidConfigJson() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of());
        SystemConfig config = new SystemConfig();
        config.setConfigJson("not-json");
        when(systemConfigMapper.selectOne(any())).thenReturn(config);

        Map<String, Object> metrics = service.getAnnotationQualityMetrics(1L);
        assertNotNull(metrics.get("qualityWeights"));
    }

    // ---- Publish ----

    @Test
    @DisplayName("发布标注任务 - 任务不存在")
    void publishAnnotationTask_notFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.publishAnnotationTask(1L, authentication));
    }

    @Test
    @DisplayName("发布标注任务 - 质量门禁未通过")
    void publishAnnotationTask_notPublishable() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of());
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        // SUBMITTED but not approved -> approveRate=0 -> not publishable
        Annotation submitted = annotation(1L, 1L, "SUBMITTED", "phone");
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(submitted));
        assertThrows(BusinessException.class, () -> service.publishAnnotationTask(1L, authentication));
    }

    @Test
    @DisplayName("发布标注任务 - 成功")
    void publishAnnotationTask_success() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation approved = annotation(1L, 1L, "APPROVED", "phone");
        approved.setConfidence(new BigDecimal("0.95"));
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(approved));
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        when(annotationTaskMapper.updateById(any())).thenReturn(1);
        when(annotationMapper.updateById(any())).thenReturn(1);

        AnnotationTask result = service.publishAnnotationTask(1L, authentication);

        assertEquals("PUBLISHED", result.getStatus());
        assertEquals("v1.0", result.getPublishVersion());
    }

    // ---- Assignment ----

    @Test
    @DisplayName("分配标注员 - 任务不存在")
    void assignAnnotators_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.assignAnnotators(1L, List.of(2L), authentication));
    }

    @Test
    @DisplayName("分配标注员 - 空列表")
    void assignAnnotators_empty() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task(1L, 1L, 1L));
        assertThrows(BusinessException.class, () -> service.assignAnnotators(1L, List.of(), authentication));
    }

    @Test
    @DisplayName("分配标注员 - 成功写入分配记录")
    void assignAnnotators_success() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(assigneeMapper.delete(any())).thenReturn(0);
        when(assigneeMapper.insert(any())).thenReturn(1);
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        AnnotationTask result = service.assignAnnotators(1L, Arrays.asList(2L, null, 2L, 3L), authentication);

        assertEquals(2, result.getAssignedAnnotators());
        verify(assigneeMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("分配标注员 - 无 assigneeMapper 仅更新计数")
    void assignAnnotators_noAssigneeMapper() {
        AnnotationService svc = serviceWithoutAssignee();
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        AnnotationTask result = svc.assignAnnotators(1L, List.of(2L), authentication);

        assertEquals(1, result.getAssignedAnnotators());
        verify(annotationTaskMapper).updateById(t);
    }

    @Test
    @DisplayName("获取标注员任务 - 已分配任务ID非空走 in 查询")
    void getAnnotatorTasks_assignedNonEmpty() {
        AnnotationTaskAssignee asg = new AnnotationTaskAssignee();
        asg.setTaskId(5L);
        when(assigneeMapper.selectList(any())).thenReturn(List.of(asg));
        Page<Annotation> page = new Page<>();
        page.setRecords(List.of(new Annotation()));
        page.setTotal(1L);
        when(annotationMapper.selectPage(any(), any())).thenReturn(page);
        assertEquals(1, service.getAnnotatorTasks(2L, 1, 10).getRecords().size());
    }

    @Test
    @DisplayName("获取标注员任务 - 已分配为空走 annotatedBy 查询")
    void getAnnotatorTasks_assignedEmpty() {
        when(assigneeMapper.selectList(any())).thenReturn(List.of());
        Page<Annotation> page = new Page<>();
        page.setRecords(List.of());
        page.setTotal(0L);
        when(annotationMapper.selectPage(any(), any())).thenReturn(page);
        assertNotNull(service.getAnnotatorTasks(2L, 1, 10));
    }

    @Test
    @DisplayName("获取标注员任务 - 无 assigneeMapper 走 annotatedBy 查询")
    void getAnnotatorTasks_noAssigneeMapper() {
        AnnotationService svc = serviceWithoutAssignee();
        Page<Annotation> page = new Page<>();
        page.setRecords(List.of());
        page.setTotal(0L);
        when(annotationMapper.selectPage(any(), any())).thenReturn(page);
        assertNotNull(svc.getAnnotatorTasks(2L, 1, 10));
    }

    // ---- Quality Sampling ----

    @Test
    @DisplayName("质量抽检 - 任务不存在")
    void performQualitySampling_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.performQualitySampling(1L, 1.0, Map.of(), authentication));
    }

    @Test
    @DisplayName("质量抽检 - 多种决策分支")
    void performQualitySampling_decisions() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation a1 = annotation(1L, 1L, "SUBMITTED", "phone");
        a1.setId(1L);
        Annotation a2 = annotation(2L, 1L, "SUBMITTED", "phone");
        a2.setId(2L);
        Annotation a3 = annotation(3L, 1L, "APPROVED", "phone");
        a3.setId(3L);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(a1, a2, a3));
        when(annotationMapper.updateById(any())).thenReturn(1);

        Map<String, Object> decisions = new HashMap<>();
        decisions.put("1", true);            // boolean true
        decisions.put("2", "fail");          // string fail
        decisions.put("3", "unknown");       // unknown -> null

        Map<String, Object> report = service.performQualitySampling(1L, 1.0, decisions, authentication);

        assertEquals(3, report.get("sampleSize"));
        assertEquals(1, report.get("passedCount"));
        assertEquals(1, report.get("failedCount"));
        assertTrue((boolean) report.get("manualReviewRequired"));
        verify(historyMapper, times(2)).insert(any()); // only pass/fail record history
    }

    @Test
    @DisplayName("质量抽检 - 空候选")
    void performQualitySampling_emptyCandidates() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of());
        Map<String, Object> report = service.performQualitySampling(1L, 1.0, null, authentication);
        assertEquals(0, report.get("sampleSize"));
        assertEquals(0.0, report.get("passRate"));
    }

    // ---- Consistency ----

    @Test
    @DisplayName("一致性检查 - 任务不存在")
    void checkConsistency_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.checkConsistency(1L));
    }

    @Test
    @DisplayName("一致性检查 - 单标注员")
    void checkConsistency_singleAnnotator() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation a1 = annotation(1L, 1L, "SUBMITTED", "phone");
        a1.setAnnotatedBy(1L);
        a1.setRowIndex(0);
        Annotation a2 = annotation(2L, 1L, "SUBMITTED", "email");
        a2.setAnnotatedBy(1L);
        a2.setRowIndex(0);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(a1, a2));
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        Map<String, Object> result = service.checkConsistency(1L);
        assertEquals(1, result.get("singleAnnotatorRows"));
        assertEquals(0, result.get("inconsistentRows"));
    }

    @Test
    @DisplayName("一致性检查 - 一致")
    void checkConsistency_consistent() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation a1 = annotation(1L, 1L, "SUBMITTED", "phone");
        a1.setAnnotatedBy(1L);
        a1.setRowIndex(0);
        Annotation a2 = annotation(2L, 1L, "SUBMITTED", "phone");
        a2.setAnnotatedBy(2L);
        a2.setRowIndex(0);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(a1, a2));
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        Map<String, Object> result = service.checkConsistency(1L);
        assertEquals(1, result.get("consistentRows"));
        assertEquals(0, result.get("inconsistentRows"));
    }

    @Test
    @DisplayName("一致性检查 - 不一致")
    void checkConsistency_inconsistent() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation a1 = annotation(1L, 1L, "SUBMITTED", "phone");
        a1.setAnnotatedBy(1L);
        a1.setRowIndex(0);
        Annotation a2 = annotation(2L, 1L, "SUBMITTED", "email");
        a2.setAnnotatedBy(2L);
        a2.setRowIndex(0);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(a1, a2));
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        Map<String, Object> result = service.checkConsistency(1L);
        assertEquals(1, result.get("inconsistentRows"));
        assertNotNull(result.get("inconsistencies"));
    }

    // ---- Annotator Performance ----

    @Test
    @DisplayName("获取标注员绩效 - 任务不存在")
    void getAnnotatorPerformance_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getAnnotatorPerformance(1L));
    }

    @Test
    @DisplayName("获取标注员绩效 - 有审核记录")
    void getAnnotatorPerformance_withReviewed() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation a1 = annotation(1L, 1L, "APPROVED", "phone");
        a1.setAnnotatedBy(1L);
        a1.setReviewedAt(LocalDateTime.now());
        a1.setAnnotatedAt(LocalDateTime.now());
        Annotation a2 = annotation(2L, 1L, "REJECTED", "phone");
        a2.setAnnotatedBy(1L);
        a2.setAnnotatedAt(LocalDateTime.now());
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(a1, a2));

        List<Map<String, Object>> result = service.getAnnotatorPerformance(1L);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).get("annotatorId"));
    }

    @Test
    @DisplayName("获取标注员绩效 - 无审核记录")
    void getAnnotatorPerformance_noReviewed() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Annotation a1 = annotation(1L, 1L, "SUBMITTED", "phone");
        a1.setAnnotatedBy(1L);
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(a1));

        List<Map<String, Object>> result = service.getAnnotatorPerformance(1L);
        assertEquals(0.0, result.get(0).get("approvalRate"));
    }

    // ---- Auto Validate ----

    @Test
    @DisplayName("自动校验 - 任务不存在")
    void autoValidate_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.autoValidate(1L));
    }

    @Test
    @DisplayName("自动校验 - 跳过已发布/合法/非法")
    void autoValidate_mixed() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        // quality rules: ensureDefault skipped, return LABEL_IN_SCHEMA + MIN_CONFIDENCE
        when(qualityRuleMapper.selectCount(any())).thenReturn(2L);
        AnnotationQualityRule labelRule = new AnnotationQualityRule();
        labelRule.setRuleType("LABEL_IN_SCHEMA");
        labelRule.setEnabled(true);
        AnnotationQualityRule confRule = new AnnotationQualityRule();
        confRule.setRuleType("MIN_CONFIDENCE");
        confRule.setThreshold(0.70);
        confRule.setEnabled(true);
        when(qualityRuleMapper.selectList(any())).thenReturn(List.of(labelRule, confRule));
        when(labelSchemaMapper.selectByParentId(1L)).thenReturn(List.of(label(2L, "phone", "手机", 1L)));

        Annotation published = annotation(1L, 1L, "PUBLISHED", "phone");
        Annotation valid = annotation(2L, 1L, "SUBMITTED", "phone");
        valid.setConfidence(new BigDecimal("0.90"));
        Annotation invalid = annotation(3L, 1L, "SUBMITTED", "phone");
        invalid.setConfidence(new BigDecimal("0.50"));
        when(annotationMapper.selectByTaskId(1L)).thenReturn(List.of(published, valid, invalid));
        when(annotationMapper.updateById(any())).thenReturn(1);
        when(annotationTaskMapper.updateById(any())).thenReturn(1);

        Map<String, Object> result = service.autoValidate(1L);
        assertEquals(2, result.get("checked"));
        assertEquals(1, result.get("passed"));
        assertEquals(1, result.get("routedToReview"));
        assertEquals("IN_REVIEW", t.getStatus());
    }

    // ---- Export ----

    @Test
    @DisplayName("导出标注 - 任务不存在")
    void exportAnnotations_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.exportAnnotations(1L, authentication));
    }

    @Test
    @DisplayName("导出标注 - 数据集不存在")
    void exportAnnotations_datasetNotFound() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.exportAnnotations(1L, authentication));
    }

    @Test
    @DisplayName("导出标注 - 存储服务未配置")
    void exportAnnotations_noLocalStorage() {
        AnnotationService svc = serviceWithoutAssignee(); // localStorageService not set
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        assertThrows(BusinessException.class, () -> svc.exportAnnotations(1L, authentication));
    }

    @Test
    @DisplayName("导出标注 - 成功")
    void exportAnnotations_success() {
        AnnotationTask t = task(1L, 1L, 1L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(t);
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("ds");
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);

        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", "Alice");
        row.put("phone", "13812345678");
        rows.add(row);
        when(dataReader.readRows(dataset)).thenReturn(rows);

        Annotation a = annotation(1L, 1L, "APPROVED", "phone");
        a.setRowIndex(0);
        a.setLabelName("手机号");
        when(annotationMapper.selectList(any())).thenReturn(List.of(a));
        when(localStorageService.writeFile(any(byte[].class), anyString())).thenReturn("file.csv");
        when(localStorageService.fileUrl("file.csv")).thenReturn("http://localhost/file.csv");

        Map<String, Object> result = service.exportAnnotations(1L, authentication);
        assertEquals(1L, result.get("taskId"));
        assertEquals(1, result.get("outputRows"));
        assertEquals(1, result.get("annotatedRows"));
        assertNotNull(result.get("fileUrl"));
    }
}
