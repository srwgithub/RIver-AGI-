package com.river.agi.prediction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RandomForestClassifierTest {

    private RandomForestClassifier algorithm;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        algorithm = new RandomForestClassifier(objectMapper);
        setField(algorithm, "numTrees", 3);
        setField(algorithm, "maxDepth", 4);
        setField(algorithm, "minSamplesSplit", 2);
        setField(algorithm, "minSamplesLeaf", 1);
        setField(algorithm, "defaultWindowSize", 3);
        setField(algorithm, "randomSeed", 42L);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private List<PredictionData.SeriesPoint> buildSeries(double[] values) {
        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate base = LocalDate.now().minusDays(values.length);
        for (int i = 0; i < values.length; i++) {
            series.add(new PredictionData.SeriesPoint(base.plusDays(i), values[i]));
        }
        return series;
    }

    private PredictionTask task(long id, long datasetId) {
        PredictionTask t = new PredictionTask();
        t.setId(id);
        t.setDatasetId(datasetId);
        return t;
    }

    @Test
    @DisplayName("Algorithm metadata")
    void metadata() {
        assertEquals("随机森林分类器", algorithm.getAlgorithmName());
        assertEquals("RANDOM_FOREST_CLASSIFIER", algorithm.getAlgorithmType());
        assertTrue(algorithm.supportsClassification());
    }

    @Test
    @DisplayName("getAlgorithmParams returns configured hyperparameters")
    void getAlgorithmParams() {
        PredictionTask t = task(1L, 10L);
        Map<String, Object> params = algorithm.getAlgorithmParams(t);
        assertEquals("RANDOM_FOREST_CLASSIFIER", params.get("algorithm"));
        assertEquals(3, params.get("numTrees"));
        assertEquals(4, params.get("maxDepth"));
        assertEquals(3, params.get("defaultWindowSize"));
        assertNotNull(params.get("description"));
    }

    @Test
    @DisplayName("train with binary-class series produces model version with metrics")
    void train_binaryClass() {
        PredictionTask t = task(1L, 10L);
        double[] vals = new double[15];
        for (int i = 0; i < vals.length; i++) vals[i] = (i % 2 == 0) ? 1.0 : 2.0;
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = algorithm.train(t, series);

        assertNotNull(mv);
        assertEquals("RANDOM_FOREST_CLASSIFIER", mv.getModelType());
        assertEquals("CLASSIFICATION", mv.getTaskType());
        assertEquals("ACTIVE", mv.getStatus());
        assertEquals(1L, mv.getPredictionTaskId());
        assertNotNull(mv.getTrainingMetricsJson());
        assertTrue(mv.getTrainingMetricsJson().contains("trees"));
        assertTrue(mv.getTrainingMetricsJson().contains("metrics"));
        assertNotNull(mv.getFeatureImportanceJson());
        assertNotNull(mv.getMae());
        assertNotNull(mv.getRmse());
        assertNotNull(mv.getMape());
    }

    @Test
    @DisplayName("train with multi-class series (3 classes) exercises multiclass metric paths")
    void train_multiClass() {
        PredictionTask t = task(2L, 20L);
        double[] vals = new double[15];
        for (int i = 0; i < vals.length; i++) vals[i] = 10.0 * (i + 1);
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = algorithm.train(t, series);

        assertNotNull(mv);
        assertTrue(mv.getTrainingMetricsJson().contains("numClasses"));
        assertTrue(mv.getMae() >= 0);
    }

    @Test
    @DisplayName("train with null series throws IllegalArgumentException")
    void train_nullSeries() {
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.train(task(1L, 1L), null));
    }

    @Test
    @DisplayName("train with insufficient data throws IllegalArgumentException")
    void train_insufficientData() {
        List<PredictionData.SeriesPoint> series = buildSeries(new double[]{1.0, 2.0});
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.train(task(1L, 1L), series));
    }

    @Test
    @DisplayName("train honors explicit task window size")
    void train_customWindowSize() {
        PredictionTask t = task(3L, 30L);
        t.setWindowSize(4);
        double[] vals = new double[20];
        for (int i = 0; i < vals.length; i++) vals[i] = 10.0 * (i + 1);
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = algorithm.train(t, series);
        assertNotNull(mv);
        assertTrue(mv.getTrainingMetricsJson().contains("\"windowSize\":4"));
    }

    @Test
    @DisplayName("predict after train returns forecast results")
    void predict_afterTrain() {
        PredictionTask t = task(1L, 10L);
        double[] vals = new double[15];
        for (int i = 0; i < vals.length; i++) vals[i] = 10.0 * (i + 1);
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = algorithm.train(t, series);
        List<PredictionResult> results = algorithm.predict(t, series, mv, 7);

        assertNotNull(results);
        assertEquals(7, results.size());
        for (PredictionResult r : results) {
            assertNotNull(r.getPredictionDate());
            assertNotNull(r.getPredictedValue());
            assertNotNull(r.getConfidence());
            assertNotNull(r.getLowerBound());
            assertNotNull(r.getUpperBound());
            assertEquals(1L, r.getTaskId());
            assertTrue(r.getLowerBound() >= 0);
            assertTrue(r.getUpperBound() <= 1);
            assertTrue(r.getLowerBound() <= r.getUpperBound());
        }
    }

    @Test
    @DisplayName("predict caps forecast days to 30")
    void predict_capsForecastDays() {
        PredictionTask t = task(1L, 10L);
        double[] vals = new double[15];
        for (int i = 0; i < vals.length; i++) vals[i] = 10.0 * (i + 1);
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = algorithm.train(t, series);
        List<PredictionResult> results = algorithm.predict(t, series, mv, 50);
        assertEquals(30, results.size());
    }

    @Test
    @DisplayName("predict with null metrics json falls back to defaults gracefully")
    void predict_emptyMetrics() {
        PredictionTask t = task(1L, 10L);
        double[] vals = new double[10];
        for (int i = 0; i < vals.length; i++) vals[i] = (i % 2 == 0) ? 1.0 : 2.0;
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = new ModelVersion();
        mv.setTrainingMetricsJson(null);

        List<PredictionResult> results = algorithm.predict(t, series, mv, 5);
        assertNotNull(results);
        assertEquals(5, results.size());
    }

    @Test
    @DisplayName("predict with invalid metrics json falls back to defaults gracefully")
    void predict_invalidMetrics() {
        PredictionTask t = task(1L, 10L);
        double[] vals = new double[10];
        for (int i = 0; i < vals.length; i++) vals[i] = (i % 2 == 0) ? 1.0 : 2.0;
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = new ModelVersion();
        mv.setTrainingMetricsJson("not-valid-json");

        assertDoesNotThrow(() -> algorithm.predict(t, series, mv, 5));
    }

    @Test
    @DisplayName("evaluateClassifier with empty series returns error map")
    void evaluateClassifier_emptySeries() {
        PredictionTask t = task(1L, 10L);
        ModelVersion mv = new ModelVersion();
        Map<String, Object> result = algorithm.evaluateClassifier(t, new ArrayList<>(), mv);
        assertEquals(0.0, result.get("accuracy"));
        assertEquals("No data provided", result.get("error"));
    }

    @Test
    @DisplayName("evaluateClassifier with null series returns error map")
    void evaluateClassifier_nullSeries() {
        PredictionTask t = task(1L, 10L);
        ModelVersion mv = new ModelVersion();
        Map<String, Object> result = algorithm.evaluateClassifier(t, null, mv);
        assertEquals(0.0, result.get("accuracy"));
        assertEquals("No data provided", result.get("error"));
    }

    @Test
    @DisplayName("evaluateClassifier after train returns metrics")
    void evaluateClassifier_afterTrain() {
        PredictionTask t = task(1L, 10L);
        double[] vals = new double[15];
        for (int i = 0; i < vals.length; i++) vals[i] = (i % 2 == 0) ? 1.0 : 2.0;
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = algorithm.train(t, series);
        Map<String, Object> metrics = algorithm.evaluateClassifier(t, series, mv);

        assertNotNull(metrics);
        assertTrue(metrics.containsKey("accuracy"));
        assertTrue(metrics.containsKey("precision"));
        assertTrue(metrics.containsKey("recall"));
        assertTrue(metrics.containsKey("f1"));
        assertTrue(metrics.containsKey("auc"));
        assertTrue(metrics.containsKey("numSamples"));
    }

    @Test
    @DisplayName("getFeatureImportance after train returns per-feature importance")
    void getFeatureImportance_afterTrain() {
        PredictionTask t = task(1L, 10L);
        double[] vals = new double[15];
        for (int i = 0; i < vals.length; i++) vals[i] = 10.0 * (i + 1);
        List<PredictionData.SeriesPoint> series = buildSeries(vals);

        ModelVersion mv = algorithm.train(t, series);
        List<Map<String, Object>> importance = algorithm.getFeatureImportance(t, mv);

        assertNotNull(importance);
        assertFalse(importance.isEmpty());
        for (Map<String, Object> e : importance) {
            assertNotNull(e.get("featureIndex"));
            assertNotNull(e.get("featureName"));
            assertNotNull(e.get("importance"));
        }
    }

    @Test
    @DisplayName("trainClassification with explicit features and labels")
    void trainClassification_explicit() {
        PredictionTask t = task(5L, 50L);
        List<double[]> features = new ArrayList<>();
        features.add(new double[]{1.0, 2.0, 3.0});
        features.add(new double[]{2.0, 3.0, 4.0});
        features.add(new double[]{8.0, 9.0, 10.0});
        features.add(new double[]{9.0, 10.0, 11.0});
        features.add(new double[]{1.5, 2.5, 3.5});
        features.add(new double[]{8.5, 9.5, 10.5});
        List<Integer> labels = List.of(0, 0, 1, 1, 0, 1);

        ModelVersion mv = algorithm.trainClassification(t, features, labels);

        assertNotNull(mv);
        assertEquals("RANDOM_FOREST_CLASSIFIER", mv.getAlgorithmType());
        assertTrue(mv.getTrainingMetricsJson().contains("trees"));
        assertNotNull(mv.getFeatureImportanceJson());
    }

    @Test
    @DisplayName("predictClassification with explicit features returns label/probability per sample")
    void predictClassification_explicit() {
        PredictionTask t = task(5L, 50L);
        List<double[]> features = new ArrayList<>();
        features.add(new double[]{1.0, 2.0, 3.0});
        features.add(new double[]{2.0, 3.0, 4.0});
        features.add(new double[]{8.0, 9.0, 10.0});
        features.add(new double[]{9.0, 10.0, 11.0});
        features.add(new double[]{1.5, 2.5, 3.5});
        features.add(new double[]{8.5, 9.5, 10.5});
        List<Integer> labels = List.of(0, 0, 1, 1, 0, 1);

        ModelVersion mv = algorithm.trainClassification(t, features, labels);

        List<double[]> predictFeatures = new ArrayList<>();
        predictFeatures.add(new double[]{1.2, 2.2, 3.2});
        predictFeatures.add(new double[]{8.8, 9.8, 10.8});
        List<Map<String, Object>> results = algorithm.predictClassification(t, predictFeatures, mv);

        assertNotNull(results);
        assertEquals(2, results.size());
        for (Map<String, Object> r : results) {
            assertNotNull(r.get("label"));
            assertNotNull(r.get("probability"));
            assertNotNull(r.get("confidence"));
        }
    }
}
