package com.river.agi.common;

import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.mapper.AnnotationTaskMapper;
import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.chart.entity.ChartConfig;
import com.river.agi.chart.entity.Report;
import com.river.agi.chart.mapper.ChartConfigMapper;
import com.river.agi.chart.mapper.ReportMapper;
import com.river.agi.chat.entity.ChatSession;
import com.river.agi.chat.mapper.ChatSessionMapper;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.mapper.AsyncTaskMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceAccessValidatorTest {

    @Mock private DatasetMapper datasetMapper;
    @Mock private SecurityScanTaskMapper securityScanTaskMapper;
    @Mock private PredictionTaskMapper predictionTaskMapper;
    @Mock private ModelVersionMapper modelVersionMapper;
    @Mock private AnnotationTaskMapper annotationTaskMapper;
    @Mock private ChatSessionMapper chatSessionMapper;
    @Mock private ChartConfigMapper chartConfigMapper;
    @Mock private ReportMapper reportMapper;
    @Mock private AsyncTaskMapper asyncTaskMapper;
    @Mock private RoleMapper roleMapper;

    private ResourceAccessValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResourceAccessValidator(datasetMapper, securityScanTaskMapper, predictionTaskMapper,
                modelVersionMapper, annotationTaskMapper, chatSessionMapper, chartConfigMapper, reportMapper,
                asyncTaskMapper, roleMapper);
    }

    // ===== validateDatasetAccess =====

    @Test
    @DisplayName("validateDatasetAccess: null dataset throws")
    void datasetAccess_nullDataset() {
        when(datasetMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateDatasetAccess(1L, 5L));
        assertTrue(ex.getMessage().contains("数据集不存在"));
    }

    @Test
    @DisplayName("validateDatasetAccess: owner equals user passes")
    void datasetAccess_ownerIsUser() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(5L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertDoesNotThrow(() -> validator.validateDatasetAccess(1L, 5L));
        verify(roleMapper, never()).selectCodesByUserId(any());
    }

    @Test
    @DisplayName("validateDatasetAccess: null owner passes")
    void datasetAccess_nullOwner() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(null);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertDoesNotThrow(() -> validator.validateDatasetAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateDatasetAccess: non-owner non-admin throws")
    void datasetAccess_nonOwnerNonAdmin() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateDatasetAccess(1L, 5L));
        assertTrue(ex.getMessage().contains("无权访问此数据集"));
    }

    @Test
    @DisplayName("validateDatasetAccess: non-owner admin passes")
    void datasetAccess_nonOwnerAdmin() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("ADMIN"));
        assertDoesNotThrow(() -> validator.validateDatasetAccess(1L, 5L));
    }

    @Test
    @DisplayName("isAdminUser: null userId returns false (no role lookup)")
    void datasetAccess_nullUserId() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        // userId null -> isAdminUser returns false early without calling roleMapper -> throws
        assertThrows(BusinessException.class, () -> validator.validateDatasetAccess(1L, null));
        verify(roleMapper, never()).selectCodesByUserId(any());
    }

    @Test
    @DisplayName("isAdminUser: roleMapper exception returns false")
    void datasetAccess_roleMapperThrows() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenThrow(new RuntimeException("db down"));
        assertThrows(BusinessException.class, () -> validator.validateDatasetAccess(1L, 5L));
    }

    // ===== validateSecurityScanAccess =====

    @Test
    @DisplayName("validateSecurityScanAccess: null task throws")
    void securityScanAccess_nullTask() {
        when(securityScanTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateSecurityScanAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateSecurityScanAccess: dataset null passes")
    void securityScanAccess_datasetNull() {
        SecurityScanTask task = new SecurityScanTask();
        task.setDatasetId(2L);
        when(securityScanTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(2L)).thenReturn(null);
        assertDoesNotThrow(() -> validator.validateSecurityScanAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateSecurityScanAccess: non-owner throws")
    void securityScanAccess_nonOwner() {
        SecurityScanTask task = new SecurityScanTask();
        task.setDatasetId(2L);
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(securityScanTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(2L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateSecurityScanAccess(1L, 5L));
    }

    // ===== validatePredictionAccess =====

    @Test
    @DisplayName("validatePredictionAccess: null task throws")
    void predictionAccess_nullTask() {
        when(predictionTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validatePredictionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validatePredictionAccess: dataset null passes")
    void predictionAccess_datasetNull() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(2L);
        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(2L)).thenReturn(null);
        assertDoesNotThrow(() -> validator.validatePredictionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validatePredictionAccess: non-owner throws")
    void predictionAccess_nonOwner() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(2L);
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(2L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validatePredictionAccess(1L, 5L));
    }

    // ===== validateModelVersionAccess =====

    @Test
    @DisplayName("validateModelVersionAccess: null model version throws")
    void modelVersionAccess_null() {
        when(modelVersionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateModelVersionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateModelVersionAccess: with predictionTaskId and accessible dataset passes")
    void modelVersionAccess_withTaskId_accessible() {
        ModelVersion mv = new ModelVersion();
        mv.setPredictionTaskId(2L);
        PredictionTask task = new PredictionTask();
        task.setDatasetId(3L);
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(5L);
        when(modelVersionMapper.selectById(1L)).thenReturn(mv);
        when(predictionTaskMapper.selectById(2L)).thenReturn(task);
        when(datasetMapper.selectById(3L)).thenReturn(dataset);
        assertDoesNotThrow(() -> validator.validateModelVersionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateModelVersionAccess: with predictionTaskId and null task passes")
    void modelVersionAccess_withTaskId_nullTask() {
        ModelVersion mv = new ModelVersion();
        mv.setPredictionTaskId(2L);
        when(modelVersionMapper.selectById(1L)).thenReturn(mv);
        when(predictionTaskMapper.selectById(2L)).thenReturn(null);
        assertDoesNotThrow(() -> validator.validateModelVersionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateModelVersionAccess: with predictionTaskId and non-owner throws")
    void modelVersionAccess_withTaskId_nonOwner() {
        ModelVersion mv = new ModelVersion();
        mv.setPredictionTaskId(2L);
        PredictionTask task = new PredictionTask();
        task.setDatasetId(3L);
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(modelVersionMapper.selectById(1L)).thenReturn(mv);
        when(predictionTaskMapper.selectById(2L)).thenReturn(task);
        when(datasetMapper.selectById(3L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateModelVersionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateModelVersionAccess: null predictionTaskId, createdBy equals user passes")
    void modelVersionAccess_noTaskId_owner() {
        ModelVersion mv = new ModelVersion();
        mv.setPredictionTaskId(null);
        mv.setCreatedBy(5L);
        when(modelVersionMapper.selectById(1L)).thenReturn(mv);
        assertDoesNotThrow(() -> validator.validateModelVersionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateModelVersionAccess: null predictionTaskId, non-owner throws")
    void modelVersionAccess_noTaskId_nonOwner() {
        ModelVersion mv = new ModelVersion();
        mv.setPredictionTaskId(null);
        mv.setCreatedBy(99L);
        when(modelVersionMapper.selectById(1L)).thenReturn(mv);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateModelVersionAccess(1L, 5L));
    }

    // ===== validateAnnotationAccess =====

    @Test
    @DisplayName("validateAnnotationAccess: null task throws")
    void annotationAccess_null() {
        when(annotationTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateAnnotationAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateAnnotationAccess: owner passes")
    void annotationAccess_owner() {
        AnnotationTask task = new AnnotationTask();
        task.setCreatedBy(5L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(task);
        assertDoesNotThrow(() -> validator.validateAnnotationAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateAnnotationAccess: non-owner non-admin throws")
    void annotationAccess_nonOwner() {
        AnnotationTask task = new AnnotationTask();
        task.setCreatedBy(99L);
        when(annotationTaskMapper.selectById(1L)).thenReturn(task);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateAnnotationAccess(1L, 5L));
    }

    // ===== validateChatSessionAccess =====

    @Test
    @DisplayName("validateChatSessionAccess: null session throws")
    void chatSessionAccess_null() {
        when(chatSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateChatSessionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateChatSessionAccess: session owner passes")
    void chatSessionAccess_owner() {
        ChatSession session = new ChatSession();
        session.setUserId(5L);
        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        assertDoesNotThrow(() -> validator.validateChatSessionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateChatSessionAccess: non-owner non-admin throws")
    void chatSessionAccess_nonOwner() {
        ChatSession session = new ChatSession();
        session.setUserId(99L);
        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateChatSessionAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateChatSessionAccess: non-owner admin passes")
    void chatSessionAccess_admin() {
        ChatSession session = new ChatSession();
        session.setUserId(99L);
        when(chatSessionMapper.selectById(1L)).thenReturn(session);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("ADMIN"));
        assertDoesNotThrow(() -> validator.validateChatSessionAccess(1L, 5L));
    }

    // ===== validateChartConfigAccess =====

    @Test
    @DisplayName("validateChartConfigAccess: null config throws")
    void chartConfigAccess_null() {
        when(chartConfigMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateChartConfigAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateChartConfigAccess: null datasetId passes")
    void chartConfigAccess_nullDatasetId() {
        ChartConfig config = new ChartConfig();
        config.setDatasetId(null);
        when(chartConfigMapper.selectById(1L)).thenReturn(config);
        assertDoesNotThrow(() -> validator.validateChartConfigAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateChartConfigAccess: dataset null passes")
    void chartConfigAccess_datasetNull() {
        ChartConfig config = new ChartConfig();
        config.setDatasetId(2L);
        when(chartConfigMapper.selectById(1L)).thenReturn(config);
        when(datasetMapper.selectById(2L)).thenReturn(null);
        assertDoesNotThrow(() -> validator.validateChartConfigAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateChartConfigAccess: non-owner throws")
    void chartConfigAccess_nonOwner() {
        ChartConfig config = new ChartConfig();
        config.setDatasetId(2L);
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(chartConfigMapper.selectById(1L)).thenReturn(config);
        when(datasetMapper.selectById(2L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateChartConfigAccess(1L, 5L));
    }

    // ===== validateReportAccess =====

    @Test
    @DisplayName("validateReportAccess: null report throws")
    void reportAccess_null() {
        when(reportMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateReportAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateReportAccess: null datasetId passes")
    void reportAccess_nullDatasetId() {
        Report report = new Report();
        report.setDatasetId(null);
        when(reportMapper.selectById(1L)).thenReturn(report);
        assertDoesNotThrow(() -> validator.validateReportAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateReportAccess: dataset null passes")
    void reportAccess_datasetNull() {
        Report report = new Report();
        report.setDatasetId(2L);
        when(reportMapper.selectById(1L)).thenReturn(report);
        when(datasetMapper.selectById(2L)).thenReturn(null);
        assertDoesNotThrow(() -> validator.validateReportAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateReportAccess: non-owner throws")
    void reportAccess_nonOwner() {
        Report report = new Report();
        report.setDatasetId(2L);
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(reportMapper.selectById(1L)).thenReturn(report);
        when(datasetMapper.selectById(2L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateReportAccess(1L, 5L));
    }

    // ===== validateAsyncTaskAccess =====

    @Test
    @DisplayName("validateAsyncTaskAccess: null task throws")
    void asyncTaskAccess_null() {
        when(asyncTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateAsyncTaskAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateAsyncTaskAccess: owner passes")
    void asyncTaskAccess_owner() {
        AsyncTask task = new AsyncTask();
        task.setCreatedBy(5L);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);
        assertDoesNotThrow(() -> validator.validateAsyncTaskAccess(1L, 5L));
    }

    @Test
    @DisplayName("validateAsyncTaskAccess: non-owner non-admin throws")
    void asyncTaskAccess_nonOwner() {
        AsyncTask task = new AsyncTask();
        task.setCreatedBy(99L);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateAsyncTaskAccess(1L, 5L));
    }

    // ===== validateDatasetOwnership =====

    @Test
    @DisplayName("validateDatasetOwnership: null dataset throws")
    void datasetOwnership_null() {
        when(datasetMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> validator.validateDatasetOwnership(1L, 5L));
    }

    @Test
    @DisplayName("validateDatasetOwnership: null createdBy passes")
    void datasetOwnership_nullCreatedBy() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(null);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertDoesNotThrow(() -> validator.validateDatasetOwnership(1L, 5L));
    }

    @Test
    @DisplayName("validateDatasetOwnership: owner passes")
    void datasetOwnership_owner() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(5L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertDoesNotThrow(() -> validator.validateDatasetOwnership(1L, 5L));
    }

    @Test
    @DisplayName("validateDatasetOwnership: non-owner non-admin throws")
    void datasetOwnership_nonOwner() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("USER"));
        assertThrows(BusinessException.class, () -> validator.validateDatasetOwnership(1L, 5L));
    }

    @Test
    @DisplayName("validateDatasetOwnership: non-owner admin passes")
    void datasetOwnership_admin() {
        Dataset dataset = new Dataset();
        dataset.setCreatedBy(99L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(roleMapper.selectCodesByUserId(5L)).thenReturn(List.of("ADMIN"));
        assertDoesNotThrow(() -> validator.validateDatasetOwnership(1L, 5L));
    }
}
