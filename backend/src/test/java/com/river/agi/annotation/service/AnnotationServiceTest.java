package com.river.agi.annotation.service;

import com.river.agi.annotation.entity.Annotation;
import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.entity.LabelSchema;
import com.river.agi.annotation.mapper.AnnotationMapper;
import com.river.agi.annotation.mapper.AnnotationTaskMapper;
import com.river.agi.annotation.mapper.LabelSchemaMapper;
import com.river.agi.auth.mapper.UserMapper;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.dataset.service.LocalStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("标注服务测试")
class AnnotationServiceTest {
    
    @Mock
    private LabelSchemaMapper labelSchemaMapper;
    
    @Mock
    private AnnotationTaskMapper annotationTaskMapper;
    
    @Mock
    private AnnotationMapper annotationMapper;
    
    @Mock
    private DatasetMapper datasetMapper;
    
    @Mock
    private UserMapper userMapper;
    
    private AnnotationService annotationService;
    private ObjectMapper objectMapper;
    private SecurityUtils securityUtils;
    private DatasetDataReaderService dataReader;
    private LocalStorageService localStorageService;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        localStorageService = new LocalStorageService();
        dataReader = new DatasetDataReaderService(localStorageService, objectMapper);
        securityUtils = new SecurityUtils(userMapper);
        
        annotationService = new AnnotationService(
            labelSchemaMapper,
            annotationTaskMapper,
            annotationMapper,
            datasetMapper,
            securityUtils,
            objectMapper,
            dataReader
        );
    }
    
    @Test
    @DisplayName("创建标注任务 - 数据集不存在")
    void createAnnotationTask_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        
        assertThrows(Exception.class, () -> 
            annotationService.createAnnotationTask(new AnnotationTask(), null)
        );
    }
    
    @Test
    @DisplayName("创建标注任务 - 未认证用户")
    void createAnnotationTask_userNotAuthenticated() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setRowCount(100);
        
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        
        AnnotationTask task = new AnnotationTask();
        task.setDatasetId(1L);
        task.setName("test_task");
        
        assertThrows(Exception.class, () -> 
            annotationService.createAnnotationTask(task, null)
        );
    }
    
    @Test
    @DisplayName("预标注 - 任务不存在")
    void preAnnotate_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        
        assertThrows(Exception.class, () -> 
            annotationService.preAnnotate(1L)
        );
    }
    
    @Test
    @DisplayName("预标注 - 数据集不存在")
    void preAnnotate_datasetNotFound() {
        AnnotationTask task = new AnnotationTask();
        task.setId(1L);
        task.setDatasetId(1L);
        
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task);
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        
        assertThrows(Exception.class, () -> 
            annotationService.preAnnotate(1L)
        );
    }
    
    @Test
    @DisplayName("获取标注质量指标 - 任务不存在")
    void getAnnotationQualityMetrics_taskNotFound() {
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(null);
        
        assertThrows(Exception.class, () -> 
            annotationService.getAnnotationQualityMetrics(1L)
        );
    }
    
    @Test
    @DisplayName("获取标注质量指标 - 计算正确")
    void getAnnotationQualityMetrics_calculation() {
        AnnotationTask task = new AnnotationTask();
        task.setId(1L);
        
        List<Annotation> annotations = new ArrayList<>();
        Annotation a1 = new Annotation();
        a1.setStatus("APPROVED");
        a1.setConfidence(new BigDecimal("0.95"));
        a1.setIsCorrected(false);
        annotations.add(a1);
        
        Annotation a2 = new Annotation();
        a2.setStatus("REJECTED");
        a2.setConfidence(new BigDecimal("0.85"));
        a2.setIsCorrected(true);
        annotations.add(a2);
        
        when(annotationTaskMapper.selectById(anyLong())).thenReturn(task);
        when(annotationMapper.selectByTaskId(anyLong())).thenReturn(annotations);
        
        Map<String, Object> metrics = annotationService.getAnnotationQualityMetrics(1L);
        
        assertNotNull(metrics);
        assertEquals(2, metrics.get("totalAnnotations"));
    }
    
    @Test
    @DisplayName("获取标注列表")
    void getAnnotations_success() {
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(new Annotation());
        
        when(annotationMapper.selectByTaskId(anyLong())).thenReturn(annotations);
        
        List<Annotation> result = annotationService.getAnnotations(1L);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    @DisplayName("删除标注任务")
    void deleteAnnotationTask_success() {
        when(annotationMapper.delete(any())).thenReturn(1);
        when(annotationTaskMapper.deleteById(anyLong())).thenReturn(1);
        
        assertDoesNotThrow(() -> annotationService.deleteAnnotationTask(1L));
    }
}
