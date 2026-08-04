package com.river.agi.prediction.service;

import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HoltWintersAlgorithmTest {

    private HoltWintersAlgorithm algorithm;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        algorithm = new HoltWintersAlgorithm(objectMapper);
    }

    @Test
    @DisplayName("Should return correct algorithm name and type")
    void getAlgorithmInfo() {
        assertEquals("Holt-Winters季节性预测", algorithm.getAlgorithmName());
        assertEquals("HOLT_WINTERS", algorithm.getAlgorithmType());
    }

    @Test
    @DisplayName("Should train model with sufficient data")
    void train_sufficientData() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 5 + Math.sin(i / 7.0) * 20));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertEquals("HOLT_WINTERS", version.getModelType());
        assertEquals("demand_prediction_hw_1", version.getModelName());
        assertNotNull(version.getMae());
        assertNotNull(version.getRmse());
        assertNotNull(version.getMape());
        assertTrue(version.getTrainingMetricsJson().contains("HOLT_WINTERS"));
    }

    @Test
    @DisplayName("Should fallback to simple exponential with insufficient data")
    void train_insufficientData() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(2L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(10);
        for (int i = 0; i < 10; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 2));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertNotNull(version.getTrainingMetricsJson());
    }

    @Test
    @DisplayName("Should generate predictions")
    void predict() throws Exception {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 5 + Math.sin(i / 7.0) * 20));
        }

        ModelVersion version = algorithm.train(task, series);
        List<PredictionResult> predictions = algorithm.predict(task, series, version, 7);

        assertNotNull(predictions);
        assertEquals(7, predictions.size());
        for (PredictionResult p : predictions) {
            assertNotNull(p.getPredictionDate());
            assertNotNull(p.getPredictedValue());
            assertNotNull(p.getLowerBound());
            assertNotNull(p.getUpperBound());
            assertNotNull(p.getConfidence());
            assertTrue(p.getPredictedValue() >= 0);
            assertTrue(p.getLowerBound() <= p.getPredictedValue());
            assertTrue(p.getUpperBound() >= p.getPredictedValue());
        }
    }

    @Test
    @DisplayName("Should return algorithm params")
    void getAlgorithmParams() {
        PredictionTask task = new PredictionTask();
        Map<String, Object> params = algorithm.getAlgorithmParams(task);

        assertNotNull(params);
        assertEquals("HOLT_WINTERS", params.get("algorithm"));
        assertEquals("考虑趋势和季节性的 Holt-Winters 预测", params.get("description"));
    }

    @Test
    @DisplayName("Should handle empty metrics gracefully")
    void predict_emptyMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = new ModelVersion();
        version.setTrainingMetricsJson(null);
        version.setModelType("HOLT_WINTERS");

        List<PredictionResult> predictions = algorithm.predict(task, series, version, 5);
        assertNotNull(predictions);
        assertEquals(5, predictions.size());
    }

    @Test
    @DisplayName("Should handle invalid metrics JSON gracefully")
    void predict_invalidMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = new ModelVersion();
        version.setTrainingMetricsJson("invalid-json");
        version.setModelType("HOLT_WINTERS");

        assertDoesNotThrow(() -> algorithm.predict(task, series, version, 5));
    }

    @Test
    @DisplayName("Should produce metrics in training output")
    void train_producesMetrics() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i * 3));
        }

        ModelVersion version = algorithm.train(task, series);

        assertTrue(version.getMae() >= 0);
        assertTrue(version.getRmse() >= 0);
        assertTrue(version.getMape() >= 0);
        assertNotNull(version.getFeatureImportanceJson());
    }
}
