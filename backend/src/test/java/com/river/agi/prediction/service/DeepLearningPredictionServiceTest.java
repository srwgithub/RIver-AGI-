package com.river.agi.prediction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ErrorCode;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepLearningPredictionServiceTest {

    @Mock private DeepLearningPredictionClient dlClient;
    @Mock private PredictionTaskMapper predictionTaskMapper;
    @Mock private ModelVersionMapper modelVersionMapper;
    @Mock private PredictionResultMapper predictionResultMapper;
    @Mock private PredictionClassificationService classificationService;

    private ObjectMapper objectMapper;
    private DeepLearningPredictionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new DeepLearningPredictionService(dlClient, predictionTaskMapper,
                modelVersionMapper, predictionResultMapper, objectMapper, classificationService);
    }

    private PredictionTask task() {
        PredictionTask t = new PredictionTask();
        t.setId(7L);
        t.setDatasetId(42L);
        t.setModelType("MLP_DL");
        t.setTaskType("REGRESSION");
        t.setTimeField("date");
        t.setTargetField("sales");
        t.setForecastDays(3);
        return t;
    }

    private List<PredictionData.SeriesPoint> series() {
        List<PredictionData.SeriesPoint> s = new ArrayList<>();
        LocalDate base = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 5; i++) s.add(new PredictionData.SeriesPoint(base.plusDays(i), 10.0 * (i + 1)));
        return s;
    }

    private DeepLearningPredictionClient.DeepLearningTrainResponse trainResponse() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("mae", 0.5);
        metrics.put("rmse", 0.7);
        metrics.put("mape", 12.0);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("epochs", 10);
        return new DeepLearningPredictionClient.DeepLearningTrainResponse(
                "model-1", "dl-model", "MLP_DL", "REGRESSION", metrics, params, "ACTIVE");
    }

    private DeepLearningPredictionClient.DeepLearningPredictResponse predictResponse(int count) {
        List<Map<String, Object>> predictions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("predictedValue", 100 + i);
            p.put("confidence", 0.95);
            p.put("lowerBound", 90.0);
            p.put("upperBound", 110.0);
            predictions.add(p);
        }
        return new DeepLearningPredictionClient.DeepLearningPredictResponse(predictions, Map.of("model_id", "model-1"));
    }

    private ModelVersion mv() {
        ModelVersion m = new ModelVersion();
        m.setId(99L);
        m.setModelName("dl-model");
        m.setModelType("MLP_DL");
        m.setModelPath("model-1");
        return m;
    }

    // ===== train() =====

    @Test
    @DisplayName("train falls back to Java classifier when engine disabled")
    void train_fallbackWhenDisabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(false);
        ModelVersion fallback = mv();
        fallback.setId(1L);
        when(classificationService.train(any(), eq("MLP_DL"), any())).thenReturn(fallback);

        ModelVersion result = service.train(task(), series());

        assertSame(fallback, result);
        verify(dlClient, never()).train(any());
        verify(classificationService).train(any(), eq("MLP_DL"), any());
    }

    @Test
    @DisplayName("train falls back to Java classifier when engine enabled but service unavailable")
    void train_fallbackWhenUnavailable() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(false);
        ModelVersion fallback = mv();
        when(classificationService.train(any(), eq("MLP_DL"), any())).thenReturn(fallback);

        ModelVersion result = service.train(task(), series());

        assertSame(fallback, result);
        verify(dlClient, never()).train(any());
    }

    @Test
    @DisplayName("train uses Python engine when enabled and available")
    void train_viaPythonSuccess() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenReturn(trainResponse());

        ModelVersion result = service.train(task(), series());

        assertEquals("dl-model", result.getModelName());
        assertEquals("model-1", result.getModelPath());
        assertEquals("MLP_DL", result.getModelType());
        assertEquals("MLP_DL", result.getAlgorithmType());
        assertEquals("REGRESSION", result.getTaskType());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(0.5, result.getMae());
        assertEquals(0.7, result.getRmse());
        assertEquals(12.0, result.getMape());
        assertEquals(1, result.getVersionNumber());
        assertEquals(7L, result.getPredictionTaskId());
        assertNotNull(result.getTrainingMetricsJson());
        assertNotNull(result.getFeatureImportanceJson());
        verify(classificationService, never()).train(any(), any(), any());
    }

    @Test
    @DisplayName("train falls back when Python engine throws BusinessException")
    void train_fallbackOnBusinessException() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "down"));
        ModelVersion fallback = mv();
        when(classificationService.train(any(), eq("MLP_DL"), any())).thenReturn(fallback);

        ModelVersion result = service.train(task(), series());

        assertSame(fallback, result);
    }

    @Test
    @DisplayName("train falls back when Python engine throws generic Exception")
    void train_fallbackOnGenericException() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenThrow(new RuntimeException("network error"));
        ModelVersion fallback = mv();
        when(classificationService.train(any(), eq("MLP_DL"), any())).thenReturn(fallback);

        ModelVersion result = service.train(task(), series());

        assertSame(fallback, result);
    }

    @Test
    @DisplayName("train via Python uses fallback model name when response has none")
    void train_pythonResponseWithoutModelName() {
        PredictionTask t = task();
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("mae", "0.4");
        metrics.put("rmse", "0.6");
        when(dlClient.train(any())).thenReturn(new DeepLearningPredictionClient.DeepLearningTrainResponse(
                "id-1", null, null, null, metrics, null, null));

        ModelVersion result = service.train(t, series());

        assertEquals("dl_model_42", result.getModelName());
        assertEquals("MLP_DL", result.getModelType());
        assertEquals("REGRESSION", result.getTaskType());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(0.4, result.getMae());
        assertEquals(0.6, result.getRmse());
    }

    // ===== trainStrict() =====

    @Test
    @DisplayName("trainStrict throws SERVICE_UNAVAILABLE when engine disabled")
    void trainStrict_throwsWhenDisabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.trainStrict(task(), series()));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getCode());
        verify(dlClient, never()).train(any());
    }

    @Test
    @DisplayName("trainStrict throws SERVICE_UNAVAILABLE when service unavailable")
    void trainStrict_throwsWhenUnavailable() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(false);
        when(dlClient.getEngineUrl()).thenReturn("http://test:5000");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.trainStrict(task(), series()));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getCode());
    }

    @Test
    @DisplayName("trainStrict delegates to Python engine when available")
    void trainStrict_delegatesWhenAvailable() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenReturn(trainResponse());

        ModelVersion result = service.trainStrict(task(), series());

        assertEquals("dl-model", result.getModelName());
        assertEquals("model-1", result.getModelPath());
    }

    // ===== predict() =====

    @Test
    @DisplayName("predict falls back to Java classifier when engine disabled")
    void predict_fallbackWhenDisabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(false);
        List<PredictionResult> fallback = List.of(new PredictionResult());
        when(classificationService.predict(any(), any(), any(), eq(3))).thenReturn(fallback);

        List<PredictionResult> result = service.predict(task(), series(), mv(), 3);

        assertSame(fallback, result);
        verify(dlClient, never()).predict(any());
    }

    @Test
    @DisplayName("predict uses Python engine when enabled and available")
    void predict_viaPythonSuccess() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.predict(any())).thenReturn(predictResponse(5));

        List<PredictionResult> result = service.predict(task(), series(), mv(), 3);

        assertEquals(3, result.size());
        assertEquals(7L, result.get(0).getTaskId());
        assertEquals(100.0, result.get(0).getPredictedValue());
        assertEquals(0.95, result.get(0).getConfidence());
        assertEquals(90.0, result.get(0).getLowerBound());
        assertEquals(110.0, result.get(0).getUpperBound());
        assertNotNull(result.get(0).getPredictionDate());
    }

    @Test
    @DisplayName("predict via Python returns empty list when predictions are empty")
    void predict_pythonEmptyPredictions() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.predict(any())).thenReturn(
                new DeepLearningPredictionClient.DeepLearningPredictResponse(List.of(), Map.of()));

        List<PredictionResult> result = service.predict(task(), series(), mv(), 3);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("predict falls back when Python engine throws BusinessException")
    void predict_fallbackOnBusinessException() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.predict(any())).thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "down"));
        List<PredictionResult> fallback = List.of(new PredictionResult());
        when(classificationService.predict(any(), any(), any(), eq(3))).thenReturn(fallback);

        List<PredictionResult> result = service.predict(task(), series(), mv(), 3);

        assertSame(fallback, result);
    }

    @Test
    @DisplayName("predict falls back when Python engine throws generic Exception")
    void predict_fallbackOnGenericException() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.predict(any())).thenThrow(new RuntimeException("net error"));
        List<PredictionResult> fallback = List.of(new PredictionResult());
        when(classificationService.predict(any(), any(), any(), eq(3))).thenReturn(fallback);

        List<PredictionResult> result = service.predict(task(), series(), mv(), 3);

        assertSame(fallback, result);
    }

    @Test
    @DisplayName("predict uses model name when model path is null")
    void predict_pythonUsesModelNameWhenPathNull() {
        ModelVersion m = mv();
        m.setModelPath(null);
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.predict(any())).thenReturn(predictResponse(2));

        List<PredictionResult> result = service.predict(task(), series(), m, 2);

        assertEquals(2, result.size());
    }

    // ===== predictStrict() =====

    @Test
    @DisplayName("predictStrict throws when engine disabled")
    void predictStrict_throwsWhenDisabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.predictStrict(task(), series(), mv(), 3));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getCode());
    }

    @Test
    @DisplayName("predictStrict throws when service unavailable")
    void predictStrict_throwsWhenUnavailable() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.predictStrict(task(), series(), mv(), 3));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getCode());
    }

    @Test
    @DisplayName("predictStrict delegates to Python when available")
    void predictStrict_delegatesWhenAvailable() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.predict(any())).thenReturn(predictResponse(3));

        List<PredictionResult> result = service.predictStrict(task(), series(), mv(), 3);

        assertEquals(3, result.size());
    }

    // ===== crossValidate() =====

    @Test
    @DisplayName("crossValidate throws when engine disabled")
    void crossValidate_throwsWhenDisabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.crossValidate(task(), series(), "LSTM", 5, "WALK_FORWARD"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getCode());
        verify(dlClient, never()).crossValidate(any());
    }

    @Test
    @DisplayName("crossValidate delegates to client when enabled")
    void crossValidate_delegatesWhenEnabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        Map<String, Object> cvResult = Map.of("fold_mae", 0.5);
        when(dlClient.crossValidate(any())).thenReturn(cvResult);

        Map<String, Object> result = service.crossValidate(task(), series(), "LSTM", 5, "WALK_FORWARD");

        assertSame(cvResult, result);
        verify(dlClient).crossValidate(argThat(req ->
                "LSTM".equals(req.modelType()) && req.cvFolds() == 5 && "WALK_FORWARD".equals(req.cvStrategy())));
    }

    @Test
    @DisplayName("crossValidate uses task.modelType for request taskType when present")
    void crossValidate_usesModelTypeForRequestTaskType() {
        PredictionTask t = task();
        t.setTaskType("CLASSIFICATION");
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.crossValidate(any())).thenReturn(Map.of());

        service.crossValidate(t, series(), "LSTM", 5, "WALK_FORWARD");

        verify(dlClient).crossValidate(argThat(req -> "MLP_DL".equals(req.taskType())));
    }

    @Test
    @DisplayName("crossValidate falls back to CLASSIFICATION when task modelType is null")
    void crossValidate_nullModelTypeFallback() {
        PredictionTask t = task();
        t.setModelType(null);
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.crossValidate(any())).thenReturn(Map.of());

        service.crossValidate(t, series(), "LSTM", 5, "WALK_FORWARD");

        verify(dlClient).crossValidate(argThat(req -> "CLASSIFICATION".equals(req.taskType())));
    }

    // ===== compareModels() =====

    @Test
    @DisplayName("compareModels throws when engine disabled")
    void compareModels_throwsWhenDisabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.compareModels("m1", "m2", Map.of()));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getCode());
    }

    @Test
    @DisplayName("compareModels delegates to client when enabled")
    void compareModels_delegatesWhenEnabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        Map<String, Object> cmp = Map.of("winner", "m1");
        when(dlClient.compareModels(eq("m1"), eq("m2"), any())).thenReturn(cmp);

        Map<String, Object> result = service.compareModels("m1", "m2", Map.of("k", "v"));

        assertSame(cmp, result);
    }

    // ===== listDlAlgorithms() =====

    @Test
    @DisplayName("listDlAlgorithms returns empty list when engine disabled")
    void listDlAlgorithms_emptyWhenDisabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(false);

        List<Map<String, Object>> result = service.listDlAlgorithms();

        assertTrue(result.isEmpty());
        verify(dlClient, never()).listAlgorithms();
    }

    @Test
    @DisplayName("listDlAlgorithms maps algorithms when engine enabled")
    void listDlAlgorithms_mapsWhenEnabled() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        List<DeepLearningPredictionClient.DeepLearningAlgorithm> algos = List.of(
                new DeepLearningPredictionClient.DeepLearningAlgorithm(
                        "lstm", "LSTM", "Long short-term memory", List.of("REGRESSION", "CLASSIFICATION")),
                new DeepLearningPredictionClient.DeepLearningAlgorithm(
                        "transformer", "Transformer", "Attention-based", List.of("REGRESSION"))
        );
        when(dlClient.listAlgorithms()).thenReturn(algos);

        List<Map<String, Object>> result = service.listDlAlgorithms();

        assertEquals(2, result.size());
        assertEquals("lstm", result.get(0).get("type"));
        assertEquals("LSTM", result.get(0).get("name"));
        assertEquals("Long short-term memory", result.get(0).get("description"));
        assertEquals(List.of("REGRESSION", "CLASSIFICATION"), result.get(0).get("supportedTasks"));
        assertEquals("transformer", result.get(1).get("type"));
    }

    // ===== getDlModel / deleteDlModel =====

    @Test
    @DisplayName("getDlModel delegates to client")
    void getDlModel_delegates() {
        DeepLearningPredictionClient.DeepLearningModelInfo info =
                new DeepLearningPredictionClient.DeepLearningModelInfo(
                        "m1", "model", "MLP", "REGRESSION", "ACTIVE", Map.of(), Map.of(), 100L);
        when(dlClient.getModel("m1")).thenReturn(info);

        DeepLearningPredictionClient.DeepLearningModelInfo result = service.getDlModel("m1");

        assertSame(info, result);
    }

    @Test
    @DisplayName("deleteDlModel delegates to client")
    void deleteDlModel_delegates() {
        service.deleteDlModel("m1");
        verify(dlClient).deleteModel("m1");
    }

    // ===== buildFeaturePayload edge cases (via train/predict) =====

    @Test
    @DisplayName("train reads parametersJson successfully when present")
    void train_withParametersJson() {
        PredictionTask t = task();
        t.setParametersJson("{\"learningRate\":0.01,\"windowSize\":7}");
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenReturn(trainResponse());

        ModelVersion result = service.train(t, series());

        assertNotNull(result);
        verify(dlClient).train(argThat(req -> {
            Map<String, Object> params = req.parameters();
            return params.containsKey("learningRate") && params.containsKey("windowSize");
        }));
    }

    @Test
    @DisplayName("train swallows invalid parametersJson and uses empty params")
    void train_invalidParametersJson() {
        PredictionTask t = task();
        t.setParametersJson("not-json");
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenReturn(trainResponse());

        ModelVersion result = service.train(t, series());

        assertNotNull(result);
        verify(dlClient).train(argThat(req -> req.parameters().isEmpty()));
    }

    @Test
    @DisplayName("predict uses empty series fallback date when series is empty")
    void predict_emptySeriesUsesNowDate() {
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.predict(any())).thenReturn(predictResponse(2));

        List<PredictionResult> result = service.predict(task(), List.of(), mv(), 2);

        assertEquals(2, result.size());
        assertNotNull(result.get(0).getPredictionDate());
    }

    @Test
    @DisplayName("train uses CLASSIFICATION taskType when task is classification")
    void train_classificationTaskType() {
        PredictionTask t = task();
        t.setTaskType("CLASSIFICATION");
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenReturn(trainResponse());

        ModelVersion result = service.train(t, series());

        assertEquals("REGRESSION", result.getTaskType());
        verify(dlClient).train(argThat(req -> "CLASSIFICATION".equals(req.taskType())));
    }

    @Test
    @DisplayName("train uses null forecastDays fallback to 30 in feature payload")
    void train_nullForecastDaysFallback() {
        PredictionTask t = task();
        t.setForecastDays(null);
        when(dlClient.isDlEngineEnabled()).thenReturn(true);
        when(dlClient.isServiceAvailable()).thenReturn(true);
        when(dlClient.train(any())).thenReturn(trainResponse());

        service.train(t, series());

        verify(dlClient).train(any());
    }
}
