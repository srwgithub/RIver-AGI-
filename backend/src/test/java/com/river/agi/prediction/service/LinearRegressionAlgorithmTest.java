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

class LinearRegressionAlgorithmTest {

    private LinearRegressionAlgorithm algorithm;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        algorithm = new LinearRegressionAlgorithm(objectMapper);
    }

    @Test
    @DisplayName("Should return correct algorithm name and type")
    void getAlgorithmInfo() {
        assertEquals("线性回归预测", algorithm.getAlgorithmName());
        assertEquals("LINEAR_REGRESSION", algorithm.getAlgorithmType());
    }

    @Test
    @DisplayName("Should return algorithm params")
    void getAlgorithmParams() {
        PredictionTask task = new PredictionTask();
        task.setTimeField("date");
        Map<String, Object> params = algorithm.getAlgorithmParams(task);

        assertNotNull(params);
        assertEquals("LINEAR_REGRESSION", params.get("algorithm"));
        assertEquals("基于历史数据的线性趋势预测", params.get("description"));
    }

    @Test
    @DisplayName("Should train model with sufficient data and compute metrics")
    void train_sufficientData() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            // near-perfect linear trend so r2 stays positive
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 5));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertEquals("LINEAR_REGRESSION", version.getModelType());
        assertEquals("LINEAR_REGRESSION", version.getAlgorithmType());
        assertEquals("TIME_SERIES", version.getTaskType());
        assertEquals("demand_prediction_10", version.getModelName());
        assertEquals(1, version.getVersionNumber());
        assertEquals("ACTIVE", version.getStatus());
        assertEquals(30L, version.getTrainingSamples());
        assertEquals(1L, version.getPredictionTaskId());
        assertNotNull(version.getMae());
        assertNotNull(version.getRmse());
        assertNotNull(version.getMape());
        assertTrue(version.getMae() >= 0);
        assertTrue(version.getRmse() >= 0);
        assertTrue(version.getMape() >= 0);
        assertTrue(version.getTrainingMetricsJson().contains("LINEAR_REGRESSION"));
        assertTrue(version.getTrainingMetricsJson().contains("r2"));
        assertNotNull(version.getFeatureImportanceJson());
        assertTrue(version.getFeatureImportanceJson().contains("date"));
        assertNotNull(version.getCreatedAt());
    }

    @Test
    @DisplayName("Should handle series with zero actual values (mape branch)")
    void train_zeroActualValues() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(4L);
        task.setTimeField("date");

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(10);
        for (int i = 0; i < 10; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), i == 5 ? 0.0 : 100.0 + i));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertTrue(version.getMape() >= 0);
    }

    @Test
    @DisplayName("Should produce r2 = 1.0 for constant series (total = 0 branch)")
    void train_constantSeries_r2One() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(5L);
        task.setTimeField("date");

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(10);
        for (int i = 0; i < 10; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        // total variance = 0 => r2 = 1.0
        assertTrue(version.getTrainingMetricsJson().contains("\"r2\":1.0"));
    }

    @Test
    @DisplayName("Should generate predictions with trained model")
    void predict_withTrainedModel() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(1L);
        task.setTimeField("date");

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 5));
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
            assertTrue(p.getConfidence() > 0 && p.getConfidence() <= 1.0);
            assertEquals(1L, p.getTaskId());
            assertNotNull(p.getCreatedAt());
        }
    }

    @Test
    @DisplayName("Should produce confidence 1.0 for constant series (zero residual)")
    void predict_constantSeries_confidenceOne() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setTimeField("date");

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(20);
        for (int i = 0; i < 20; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0));
        }

        ModelVersion version = algorithm.train(task, series);
        List<PredictionResult> predictions = algorithm.predict(task, series, version, 3);

        assertNotNull(predictions);
        assertEquals(3, predictions.size());
        for (PredictionResult p : predictions) {
            assertEquals(1.0, p.getConfidence());
        }
    }

    @Test
    @DisplayName("Should clamp negative predicted values to zero")
    void predict_negativePredictedClampedToZero() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setTimeField("date");

        // Strongly decreasing trend so future predictions go negative
        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(20);
        for (int i = 0; i < 20; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 50 - i * 10));
        }

        ModelVersion version = algorithm.train(task, series);
        List<PredictionResult> predictions = algorithm.predict(task, series, version, 5);

        assertNotNull(predictions);
        assertEquals(5, predictions.size());
        for (PredictionResult p : predictions) {
            assertTrue(p.getPredictedValue() >= 0);
            assertTrue(p.getLowerBound() >= 0);
        }
    }

    @Test
    @DisplayName("Should produce metrics in training output")
    void train_producesMetrics() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(1L);
        task.setTimeField("date");

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
