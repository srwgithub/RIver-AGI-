package com.river.agi.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.analysis.entity.AnalysisTask;
import com.river.agi.analysis.service.AnalysisService;
import com.river.agi.annotation.service.AnnotationService;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.service.AsyncTaskService;
import com.river.agi.dataset.service.DatasetParserService;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.service.PredictionService;
import com.river.agi.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("异步任务处理器测试")
class AsyncTaskHandlersTest {

    @Mock private AsyncTaskService asyncTaskService;
    @Mock private DatasetParserService datasetParserService;
    @Mock private AnalysisService analysisService;
    @Mock private SecurityService securityService;
    @Mock private PredictionService predictionService;
    @Mock private AnnotationService annotationService;

    private AsyncTaskHandlers handlers;
    private Map<String, Consumer<AsyncTask>> registered;

    @BeforeEach
    void setUp() {
        handlers = new AsyncTaskHandlers(asyncTaskService, datasetParserService,
                analysisService, securityService, predictionService, annotationService,
                new ObjectMapper());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<AsyncTask>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        handlers.registerHandlers();
        verify(asyncTaskService, times(5)).registerHandler(typeCaptor.capture(), consumerCaptor.capture());

        registered = new java.util.HashMap<>();
        for (int i = 0; i < typeCaptor.getAllValues().size(); i++) {
            registered.put(typeCaptor.getAllValues().get(i), consumerCaptor.getAllValues().get(i));
        }
    }

    private AsyncTask task(String type, Long resourceId, String paramsJson) {
        AsyncTask t = new AsyncTask();
        t.setId(1L);
        t.setTaskType(type);
        t.setTaskName("t-" + type);
        t.setResourceId(resourceId);
        t.setParamsJson(paramsJson);
        t.setStatus("RUNNING");
        return t;
    }

    @Test
    @DisplayName("注册全部 5 个处理器")
    void registerHandlers_registersAll() {
        assertTrue(registered.containsKey(AsyncTask.TaskType.DATASET_PARSE.name()));
        assertTrue(registered.containsKey(AsyncTask.TaskType.QUALITY_ANALYSIS.name()));
        assertTrue(registered.containsKey(AsyncTask.TaskType.SECURITY_SCAN.name()));
        assertTrue(registered.containsKey(AsyncTask.TaskType.PREDICTION.name()));
        assertTrue(registered.containsKey(AsyncTask.TaskType.PRE_ANNOTATE.name()));
    }

    @Test
    @DisplayName("数据集解析处理器 - 成功(含参数解析)")
    void datasetParseHandler_success() {
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.DATASET_PARSE.name());
        handler.accept(task(AsyncTask.TaskType.DATASET_PARSE.name(), 10L, "{\"k\":\"v\"}"));
        verify(datasetParserService).parseDataset(10L);
    }

    @Test
    @DisplayName("数据集解析处理器 - 参数为空/非法")
    void datasetParseHandler_paramsBranches() {
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.DATASET_PARSE.name());
        handler.accept(task(AsyncTask.TaskType.DATASET_PARSE.name(), 10L, null));
        handler.accept(task(AsyncTask.TaskType.DATASET_PARSE.name(), 10L, "  "));
        handler.accept(task(AsyncTask.TaskType.DATASET_PARSE.name(), 10L, "not-json"));
        verify(datasetParserService, times(3)).parseDataset(10L);
    }

    @Test
    @DisplayName("数据集解析处理器 - 异常抛出 RuntimeException")
    void datasetParseHandler_exception() {
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.DATASET_PARSE.name());
        doThrow(new RuntimeException("io")).when(datasetParserService).parseDataset(anyLong());
        assertThrows(RuntimeException.class, () -> handler.accept(task(AsyncTask.TaskType.DATASET_PARSE.name(), 10L, null)));
    }

    @Test
    @DisplayName("质量分析处理器 - 成功")
    void qualityAnalysisHandler_success() {
        AnalysisTask at = new AnalysisTask();
        at.setId(7L);
        when(analysisService.runAnalysis(anyLong(), eq("QUALITY"))).thenReturn(at);
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.QUALITY_ANALYSIS.name());
        handler.accept(task(AsyncTask.TaskType.QUALITY_ANALYSIS.name(), 10L, null));
        verify(analysisService).runAnalysis(10L, "QUALITY");
    }

    @Test
    @DisplayName("质量分析处理器 - 异常")
    void qualityAnalysisHandler_exception() {
        when(analysisService.runAnalysis(anyLong(), anyString())).thenThrow(new RuntimeException("err"));
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.QUALITY_ANALYSIS.name());
        assertThrows(RuntimeException.class, () -> handler.accept(task(AsyncTask.TaskType.QUALITY_ANALYSIS.name(), 10L, null)));
    }

    @Test
    @DisplayName("安全扫描处理器 - 成功")
    void securityScanHandler_success() {
        when(securityService.scanSensitiveData(anyLong(), any())).thenReturn(Map.of("id", 1));
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.SECURITY_SCAN.name());
        handler.accept(task(AsyncTask.TaskType.SECURITY_SCAN.name(), 10L, null));
        verify(securityService).scanSensitiveData(eq(10L), isNull());
    }

    @Test
    @DisplayName("安全扫描处理器 - 异常")
    void securityScanHandler_exception() {
        when(securityService.scanSensitiveData(anyLong(), any())).thenThrow(new RuntimeException("err"));
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.SECURITY_SCAN.name());
        assertThrows(RuntimeException.class, () -> handler.accept(task(AsyncTask.TaskType.SECURITY_SCAN.name(), 10L, null)));
    }

    @Test
    @DisplayName("预测训练处理器 - 成功")
    void predictionTrainHandler_success() {
        PredictionTask pt = new PredictionTask();
        pt.setId(9L);
        pt.setModelVersionId(99L);
        when(predictionService.runPrediction(anyLong())).thenReturn(pt);
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.PREDICTION.name());
        handler.accept(task(AsyncTask.TaskType.PREDICTION.name(), 10L, null));
        verify(predictionService).runPrediction(10L);
    }

    @Test
    @DisplayName("预测训练处理器 - 异常")
    void predictionTrainHandler_exception() {
        when(predictionService.runPrediction(anyLong())).thenThrow(new RuntimeException("err"));
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.PREDICTION.name());
        assertThrows(RuntimeException.class, () -> handler.accept(task(AsyncTask.TaskType.PREDICTION.name(), 10L, null)));
    }

    @Test
    @DisplayName("预标注处理器 - 成功")
    void preAnnotationHandler_success() {
        doNothing().when(annotationService).preAnnotate(anyLong());
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.PRE_ANNOTATE.name());
        handler.accept(task(AsyncTask.TaskType.PRE_ANNOTATE.name(), 10L, null));
        verify(annotationService).preAnnotate(10L);
    }

    @Test
    @DisplayName("预标注处理器 - 异常")
    void preAnnotationHandler_exception() {
        doThrow(new RuntimeException("err")).when(annotationService).preAnnotate(anyLong());
        Consumer<AsyncTask> handler = registered.get(AsyncTask.TaskType.PRE_ANNOTATE.name());
        assertThrows(RuntimeException.class, () -> handler.accept(task(AsyncTask.TaskType.PRE_ANNOTATE.name(), 10L, null)));
    }
}
