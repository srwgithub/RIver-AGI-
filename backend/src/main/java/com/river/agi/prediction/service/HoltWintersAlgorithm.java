package com.river.agi.prediction.service;

import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
public class HoltWintersAlgorithm implements PredictionAlgorithm {
    
    private final ObjectMapper objectMapper;
    
    private static final double DEFAULT_ALPHA = 0.3;
    private static final double DEFAULT_BETA = 0.1;
    private static final double DEFAULT_GAMMA = 0.1;
    private static final int SEASONAL_PERIOD = 7;
    
    public HoltWintersAlgorithm(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getAlgorithmName() {
        return "Holt-Winters季节性预测";
    }
    
    @Override
    public String getAlgorithmType() {
        return "HOLT_WINTERS";
    }
    
    @Override
    public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("demand_prediction_hw_" + task.getDatasetId());
        modelVersion.setModelType(getAlgorithmType());
        modelVersion.setAlgorithmType(getAlgorithmType());
        modelVersion.setTaskType("TIME_SERIES");
        modelVersion.setVersionNumber(1);
        modelVersion.setStatus("ACTIVE");
        modelVersion.setTrainingSamples((long) series.size());
        modelVersion.setPredictionTaskId(task.getId());
        
        int n = series.size();
        if (n < 2 * SEASONAL_PERIOD) {
            log.warn("Insufficient data for seasonal decomposition (need > {} points, got {})", 2 * SEASONAL_PERIOD, n);
            return trainSimpleExponential(task, series, modelVersion);
        }
        
        double alpha = DEFAULT_ALPHA;
        double beta = DEFAULT_BETA;
        double gamma = DEFAULT_GAMMA;
        
        double[] levelArr = new double[n];
        double[] trendArr = new double[n];
        double[] seasonal = new double[n];
        double[] smoothed = new double[n];
        
        double seasonalInit = 0;
        for (int i = 0; i < SEASONAL_PERIOD && i < n; i++) {
            seasonalInit += series.get(i).value();
        }
        seasonalInit = SEASONAL_PERIOD > 0 ? seasonalInit / SEASONAL_PERIOD : 0;
        
        levelArr[0] = series.get(0).value();
        trendArr[0] = n > 1 ? (series.get(n - 1).value() - series.get(0).value()) / n : 0;
        seasonal[0] = series.get(0).value() - seasonalInit;
        smoothed[0] = series.get(0).value();
        
        for (int i = 1; i < n; i++) {
            double s = seasonal[(i - SEASONAL_PERIOD + n) % n];
            
            levelArr[i] = alpha * (series.get(i).value() - s) + (1 - alpha) * (levelArr[i - 1] + trendArr[i - 1]);
            trendArr[i] = beta * (levelArr[i] - levelArr[i - 1]) + (1 - beta) * trendArr[i - 1];
            seasonal[i] = gamma * (series.get(i).value() - levelArr[i]) + (1 - gamma) * s;
            smoothed[i] = levelArr[i] + trendArr[i] + seasonal[(i - SEASONAL_PERIOD + n) % n];
        }
        
        double mae = 0, mse = 0, mape = 0;
        for (int i = 1; i < n; i++) {
            double actual = series.get(i).value();
            double predicted = smoothed[i - 1];
            double error = actual - predicted;
            mae += Math.abs(error);
            mse += error * error;
            if (actual != 0) mape += Math.abs(error / actual);
        }
        
        int validCount = n - 1;
        if (validCount > 0) {
            mae /= validCount;
            double rmse = Math.sqrt(mse / validCount);
            double mapePct = (mape / validCount) * 100;
            
            modelVersion.setMae(round(mae));
            modelVersion.setRmse(round(rmse));
            modelVersion.setMape(round(mapePct));
        } else {
            modelVersion.setMae(0.0);
            modelVersion.setRmse(0.0);
            modelVersion.setMape(0.0);
        }
        
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("mae", modelVersion.getMae());
        metrics.put("rmse", modelVersion.getRmse());
        metrics.put("mape", modelVersion.getMape());
        metrics.put("algorithm", getAlgorithmType());
        metrics.put("alpha", round(alpha));
        metrics.put("beta", round(beta));
        metrics.put("gamma", round(gamma));
        metrics.put("seasonalPeriod", SEASONAL_PERIOD);
        modelVersion.setTrainingMetricsJson(safeJson(metrics));
        
        Map<String, Double> featureImportance = new LinkedHashMap<>();
        featureImportance.put("trend", 0.4);
        featureImportance.put("seasonality", 0.6);
        modelVersion.setFeatureImportanceJson(safeJson(featureImportance));
        
        modelVersion.setCreatedAt(java.time.LocalDateTime.now());
        return modelVersion;
    }
    
    @Override
    public List<PredictionResult> predict(PredictionTask task,
                                           List<PredictionData.SeriesPoint> series,
                                           ModelVersion modelVersion,
                                           int forecastDays) {
        int n = series.size();
        Map<String, Object> metrics = parseMetrics(modelVersion.getTrainingMetricsJson());
        
        double alpha = metrics.containsKey("alpha") ? 
                ((Number) metrics.get("alpha")).doubleValue() : DEFAULT_ALPHA;
        double beta = metrics.containsKey("beta") ? 
                ((Number) metrics.get("beta")).doubleValue() : DEFAULT_BETA;
        double gamma = metrics.containsKey("gamma") ? 
                ((Number) metrics.get("gamma")).doubleValue() : DEFAULT_GAMMA;
        int period = metrics.containsKey("seasonalPeriod") ? 
                ((Number) metrics.get("seasonalPeriod")).intValue() : SEASONAL_PERIOD;
        
        double level = series.get(0).value();
        double trend = n > 1 ? (series.get(n - 1).value() - series.get(0).value()) / n : 0;
        
        double[] seasonalFactors = new double[period];
        double seasonalInit = 0;
        for (int i = 0; i < period && i < n; i++) {
            seasonalInit += series.get(i).value();
        }
        seasonalInit = period > 0 ? seasonalInit / period : 0;
        
        for (int i = 0; i < period; i++) {
            seasonalFactors[i] = i < n ? series.get(i).value() - seasonalInit : 0;
        }
        
        for (int i = 1; i < n; i++) {
            double s = seasonalFactors[i % period];
            double newLevel = alpha * (series.get(i).value() - s) + (1 - alpha) * (level + trend);
            double newTrend = beta * (newLevel - level) + (1 - beta) * trend;
            seasonalFactors[i % period] = gamma * (series.get(i).value() - newLevel) + (1 - gamma) * s;
            level = newLevel;
            trend = newTrend;
        }
        
        double variance = calculateVariance(series, seasonalFactors);
        double stdDev = Math.sqrt(variance);
        
        LocalDate lastDate = series.get(n - 1).date();
        List<PredictionResult> results = new ArrayList<>();
        
        for (int i = 1; i <= forecastDays; i++) {
            int seasonalIdx = (n - 1 + i) % period;
            double predictedValue = Math.max(0, level + i * trend + seasonalFactors[seasonalIdx]);
            
            PredictionResult result = new PredictionResult();
            result.setTaskId(task.getId());
            result.setPredictionDate(lastDate.plusDays(i).toString());
            result.setPredictedValue(round(predictedValue));
            result.setLowerBound(round(Math.max(0, predictedValue - 1.96 * stdDev)));
            result.setUpperBound(round(predictedValue + 1.96 * stdDev));
            result.setConfidence(stdDev == 0 ? 1.0 : 0.95);
            result.setCreatedAt(java.time.LocalDateTime.now());
            results.add(result);
        }
        
        return results;
    }
    
    @Override
    public Map<String, Object> getAlgorithmParams(PredictionTask task) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("algorithm", getAlgorithmType());
        params.put("description", "考虑趋势和季节性的 Holt-Winters 预测");
        params.put("seasonalPeriod", "7 (weekly)");
        return params;
    }
    
    private ModelVersion trainSimpleExponential(PredictionTask task, 
                                                  List<PredictionData.SeriesPoint> series,
                                                  ModelVersion modelVersion) {
        int n = series.size();
        double level = series.get(0).value();
        double trend = n > 1 ? series.get(1).value() - series.get(0).value() : 0;
        
        double mae = 0, mse = 0, mape = 0;
        for (int i = 1; i < n; i++) {
            double predicted = level + trend;
            double actual = series.get(i).value();
            double error = actual - predicted;
            mae += Math.abs(error);
            mse += error * error;
            if (actual != 0) mape += Math.abs(error / actual);
            
            double newLevel = DEFAULT_ALPHA * actual + (1 - DEFAULT_ALPHA) * (level + trend);
            double newTrend = DEFAULT_BETA * (newLevel - level) + (1 - DEFAULT_BETA) * trend;
            level = newLevel;
            trend = newTrend;
        }
        
        int validCount = n - 1;
        if (validCount > 0) {
            mae /= validCount;
            double rmse = Math.sqrt(mse / validCount);
            double mapePct = (mape / validCount) * 100;
            
            modelVersion.setMae(round(mae));
            modelVersion.setRmse(round(rmse));
            modelVersion.setMape(round(mapePct));
        } else {
            modelVersion.setMae(0.0);
            modelVersion.setRmse(0.0);
            modelVersion.setMape(0.0);
        }
        
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("mae", modelVersion.getMae());
        metrics.put("rmse", modelVersion.getRmse());
        metrics.put("mape", modelVersion.getMape());
        metrics.put("algorithm", getAlgorithmType());
        metrics.put("fallback", true);
        metrics.put("reason", "Insufficient seasonal data points");
        modelVersion.setTrainingMetricsJson(safeJson(metrics));
        
        Map<String, Double> featureImportance = new LinkedHashMap<>();
        featureImportance.put("trend", 1.0);
        modelVersion.setFeatureImportanceJson(safeJson(featureImportance));
        
        return modelVersion;
    }
    
    private double calculateVariance(List<PredictionData.SeriesPoint> series,
                                       double[] seasonalFactors) {
        int n = series.size();
        double sumSquaredError = 0;
        double level = series.get(0).value();
        double trend = n > 1 ? (series.get(n - 1).value() - series.get(0).value()) / n : 0;
        
        for (int i = 1; i < n; i++) {
            double predicted = level + trend + seasonalFactors[i % seasonalFactors.length];
            double error = series.get(i).value() - predicted;
            sumSquaredError += error * error;
            
            double s = seasonalFactors[i % seasonalFactors.length];
            double newLevel = DEFAULT_ALPHA * (series.get(i).value() - s) + (1 - DEFAULT_ALPHA) * (level + trend);
            double newTrend = DEFAULT_BETA * (newLevel - level) + (1 - DEFAULT_BETA) * trend;
            level = newLevel;
            trend = newTrend;
        }
        
        return n > 1 ? sumSquaredError / (n - 1) : 0;
    }
    
    private Map<String, Object> parseMetrics(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
    
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
    
    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
