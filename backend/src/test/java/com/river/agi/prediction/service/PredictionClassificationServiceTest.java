package com.river.agi.prediction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.service.DatasetDataReaderService;
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
class PredictionClassificationServiceTest {

    @Mock private PredictionTaskMapper predictionTaskMapper;
    @Mock private ModelVersionMapper modelVersionMapper;
    @Mock private PredictionResultMapper predictionResultMapper;
    @Mock private DatasetDataReaderService dataReader;

    private ObjectMapper objectMapper;

    private static class StubAlgorithm implements ClassificationAlgorithm {
        final String type;
        final String name;
        final double mae;
        StubAlgorithm(String type, String name, double mae) { this.type = type; this.name = name; this.mae = mae; }

        public String getAlgorithmName() { return name; }
        public String getAlgorithmType() { return type; }
        public boolean supportsClassification() { return true; }
        public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
            ModelVersion mv = new ModelVersion();
            mv.setId(100L);
            mv.setModelType(type);
            mv.setAlgorithmType(type);
            mv.setMae(mae);
            mv.setRmse(mae * 2);
            mv.setMape(mae * 100);
            mv.setTrainingMetricsJson("{\"numClasses\":2}");
            return mv;
        }
        public List<PredictionResult> predict(PredictionTask task, List<PredictionData.SeriesPoint> series,
                                               ModelVersion mv, int forecastDays) {
            List<PredictionResult> results = new ArrayList<>();
            for (int i = 1; i <= forecastDays; i++) {
                PredictionResult r = new PredictionResult();
                r.setTaskId(task.getId());
                r.setPredictedValue((double) i);
                results.add(r);
            }
            return results;
        }
        public Map<String, Object> evaluateClassifier(PredictionTask task, List<PredictionData.SeriesPoint> series,
                                                      ModelVersion mv) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (series == null || series.isEmpty()) {
                m.put("accuracy", 0.0);
                m.put("error", "No data provided");
                return m;
            }
            m.put("accuracy", 0.9);
            return m;
        }
        public List<Map<String, Object>> getFeatureImportance(PredictionTask task, ModelVersion mv) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("featureIndex", 0);
            e.put("importance", 1.0);
            return List.of(e);
        }
        public Map<String, Object> getAlgorithmParams(PredictionTask task) {
            return Map.of("algorithm", type);
        }
    }

    private StubAlgorithm logisticAlgo;
    private StubAlgorithm otherAlgo;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        logisticAlgo = new StubAlgorithm("LOGISTIC_REGRESSION_CLASSIFIER", "逻辑回归", 0.1);
        otherAlgo = new StubAlgorithm("DECISION_TREE_CLASSIFIER", "决策树", 0.3);
    }

    private PredictionClassificationService service(List<ClassificationAlgorithm> algos) {
        return new PredictionClassificationService(predictionTaskMapper, modelVersionMapper,
                predictionResultMapper, objectMapper, algos, dataReader);
    }

    private PredictionTask task(long id) {
        PredictionTask t = new PredictionTask();
        t.setId(id);
        t.setDatasetId(10L);
        t.setModelVersionId(100L);
        return t;
    }

    private List<PredictionData.SeriesPoint> series() {
        List<PredictionData.SeriesPoint> s = new ArrayList<>();
        LocalDate base = LocalDate.now().minusDays(5);
        for (int i = 0; i < 5; i++) s.add(new PredictionData.SeriesPoint(base.plusDays(i), 10.0 * (i + 1)));
        return s;
    }

    @Test
    @DisplayName("train delegates to selected algorithm and inserts model version")
    void train_delegatesAndInserts() {
        PredictionClassificationService svc = service(List.of(logisticAlgo, otherAlgo));
        PredictionTask t = task(1L);

        ModelVersion mv = svc.train(t, "DECISION_TREE_CLASSIFIER", series());

        assertNotNull(mv);
        assertEquals("DECISION_TREE_CLASSIFIER", mv.getAlgorithmType());
        verify(modelVersionMapper).insert(mv);
    }

    @Test
    @DisplayName("train with null algorithm type selects logistic regression by default")
    void train_defaultSelectsLogistic() {
        PredictionClassificationService svc = service(List.of(otherAlgo, logisticAlgo));
        ModelVersion mv = svc.train(task(1L), null, series());
        assertEquals("LOGISTIC_REGRESSION_CLASSIFIER", mv.getAlgorithmType());
    }

    @Test
    @DisplayName("train with blank algorithm type falls back to first algorithm when no logistic")
    void train_blankType_fallsBackToFirst() {
        PredictionClassificationService svc = service(List.of(otherAlgo));
        ModelVersion mv = svc.train(task(1L), "  ", series());
        assertEquals("DECISION_TREE_CLASSIFIER", mv.getAlgorithmType());
    }

    @Test
    @DisplayName("train with unknown algorithm type throws BusinessException")
    void train_unknownAlgorithm() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        assertThrows(BusinessException.class,
                () -> svc.train(task(1L), "UNKNOWN", series()));
    }

    @Test
    @DisplayName("train with no algorithms throws BusinessException")
    void train_noAlgorithms() {
        PredictionClassificationService svc = service(List.of());
        assertThrows(BusinessException.class,
                () -> svc.train(task(1L), null, series()));
    }

    @Test
    @DisplayName("predict delegates to algorithm matching model type")
    void predict_delegates() {
        PredictionClassificationService svc = service(List.of(logisticAlgo, otherAlgo));
        ModelVersion mv = new ModelVersion();
        mv.setModelType("LOGISTIC_REGRESSION_CLASSIFIER");

        List<PredictionResult> results = svc.predict(task(1L), series(), mv, 3);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("predict with unknown model type throws BusinessException")
    void predict_unknownModelType() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        ModelVersion mv = new ModelVersion();
        mv.setModelType("UNKNOWN");
        assertThrows(BusinessException.class, () -> svc.predict(task(1L), series(), mv, 3));
    }

    @Test
    @DisplayName("evaluate enriches result with algorithm metadata")
    void evaluate_enrichesResult() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        ModelVersion mv = new ModelVersion();
        mv.setModelType("LOGISTIC_REGRESSION_CLASSIFIER");

        Map<String, Object> result = svc.evaluate(task(1L), series(), mv);
        assertEquals(0.9, result.get("accuracy"));
        assertEquals("LOGISTIC_REGRESSION_CLASSIFIER", result.get("algorithm"));
        assertEquals("逻辑回归", result.get("algorithmName"));
    }

    @Test
    @DisplayName("evaluate by taskId loads task, model version and series")
    void evaluate_byTaskId() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        PredictionTask t = task(1L);
        ModelVersion mv = new ModelVersion();
        mv.setModelType("LOGISTIC_REGRESSION_CLASSIFIER");

        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(100L)).thenReturn(mv);
        when(dataReader.loadSeriesData(10L, null, null)).thenReturn(series());

        Map<String, Object> result = svc.evaluate(1L, 100L);
        assertEquals(0.9, result.get("accuracy"));
    }

    @Test
    @DisplayName("evaluate by taskId with null modelVersionId uses task's modelVersionId")
    void evaluate_byTaskId_defaultModelVersion() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        PredictionTask t = task(1L);
        ModelVersion mv = new ModelVersion();
        mv.setModelType("LOGISTIC_REGRESSION_CLASSIFIER");

        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(100L)).thenReturn(mv);
        when(dataReader.loadSeriesData(10L, null, null)).thenReturn(series());

        assertNotNull(svc.evaluate(1L, null));
    }

    @Test
    @DisplayName("evaluate by taskId throws when task not found")
    void evaluate_byTaskId_taskNotFound() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        when(predictionTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.evaluate(1L, 100L));
    }

    @Test
    @DisplayName("evaluate by taskId throws when model version not found")
    void evaluate_byTaskId_modelNotFound() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        PredictionTask t = task(1L);
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.evaluate(1L, 100L));
    }

    @Test
    @DisplayName("evaluate by taskId throws when no model version id available")
    void evaluate_byTaskId_noModelVersionId() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        PredictionTask t = new PredictionTask();
        t.setId(1L);
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        assertThrows(BusinessException.class, () -> svc.evaluate(1L, null));
    }

    @Test
    @DisplayName("evaluate by taskId returns empty series when dataset id is null")
    void evaluate_byTaskId_nullDataset() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        PredictionTask t = new PredictionTask();
        t.setId(1L);
        t.setModelVersionId(100L);
        ModelVersion mv = new ModelVersion();
        mv.setModelType("LOGISTIC_REGRESSION_CLASSIFIER");

        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(100L)).thenReturn(mv);

        Map<String, Object> result = svc.evaluate(1L, 100L);
        assertEquals(0.0, result.get("accuracy"));
        assertEquals("No data provided", result.get("error"));
    }

    @Test
    @DisplayName("getFeatureImportance by taskId loads task and model")
    void getFeatureImportance_byTaskId() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        PredictionTask t = task(1L);
        ModelVersion mv = new ModelVersion();
        mv.setModelType("LOGISTIC_REGRESSION_CLASSIFIER");

        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(100L)).thenReturn(mv);

        List<Map<String, Object>> imp = svc.getFeatureImportance(1L, 100L);
        assertEquals(1, imp.size());
    }

    @Test
    @DisplayName("getFeatureImportance by taskId throws when task not found")
    void getFeatureImportance_taskNotFound() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        when(predictionTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.getFeatureImportance(1L, 100L));
    }

    @Test
    @DisplayName("getFeatureImportance with unknown model type throws")
    void getFeatureImportance_unknownModelType() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        ModelVersion mv = new ModelVersion();
        mv.setModelType("UNKNOWN");
        assertThrows(BusinessException.class, () -> svc.getFeatureImportance(task(1L), mv));
    }

    @Test
    @DisplayName("trainAndPredict trains, updates task, predicts and inserts results")
    void trainAndPredict() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        PredictionTask t = task(1L);

        Map<String, Object> response = svc.trainAndPredict(t, "LOGISTIC_REGRESSION_CLASSIFIER", series(), 4);

        assertNotNull(response);
        assertEquals("LOGISTIC_REGRESSION_CLASSIFIER", response.get("algorithm"));
        assertEquals("逻辑回归", response.get("algorithmName"));
        assertNotNull(response.get("results"));
        assertEquals(4, ((List<?>) response.get("results")).size());
        verify(predictionTaskMapper).updateById(t);
        verify(predictionResultMapper, times(4)).insert(any(PredictionResult.class));
    }

    @Test
    @DisplayName("getAvailableClassificationAlgorithms returns info for each algorithm")
    void getAvailableClassificationAlgorithms() {
        PredictionClassificationService svc = service(List.of(logisticAlgo, otherAlgo));
        List<Map<String, Object>> list = svc.getAvailableClassificationAlgorithms();
        assertEquals(2, list.size());
        assertNotNull(list.get(0).get("type"));
        assertNotNull(list.get(0).get("name"));
        assertNotNull(list.get(0).get("params"));
    }

    @Test
    @DisplayName("listModelVersions delegates to mapper")
    void listModelVersions() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        ModelVersion mv = new ModelVersion();
        mv.setId(1L);
        when(modelVersionMapper.selectByModelName("model")).thenReturn(List.of(mv));

        List<ModelVersion> result = svc.listModelVersions("model");
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getModelVersion returns model")
    void getModelVersion() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        ModelVersion mv = new ModelVersion();
        mv.setId(1L);
        when(modelVersionMapper.selectById(1L)).thenReturn(mv);

        assertNotNull(svc.getModelVersion(1L));
    }

    @Test
    @DisplayName("getModelVersion throws when not found")
    void getModelVersion_notFound() {
        PredictionClassificationService svc = service(List.of(logisticAlgo));
        when(modelVersionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.getModelVersion(1L));
    }

    @Test
    @DisplayName("compareClassificationAlgorithms trains all and picks best by accuracy")
    void compareClassificationAlgorithms() {
        PredictionClassificationService svc = service(List.of(otherAlgo, logisticAlgo));
        PredictionTask t = task(1L);

        Map<String, Object> result = svc.compareClassificationAlgorithms(t, series());

        assertNotNull(result);
        assertEquals("OPTIMIZED", result.get("status"));
        assertEquals("LOGISTIC_REGRESSION_CLASSIFIER", result.get("selectedAlgorithm"));
        List<?> candidates = (List<?>) result.get("candidates");
        assertEquals(2, candidates.size());
    }

    @Test
    @DisplayName("compareClassificationAlgorithms throws when all algorithms fail")
    void compareClassificationAlgorithms_allFail() {
        ClassificationAlgorithm failing = new StubAlgorithm("FAIL_CLF", "失败", 0.1) {
            @Override
            public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
                throw new RuntimeException("boom");
            }
        };
        PredictionClassificationService svc = service(List.of(failing));
        assertThrows(BusinessException.class, () -> svc.compareClassificationAlgorithms(task(1L), series()));
    }
}
