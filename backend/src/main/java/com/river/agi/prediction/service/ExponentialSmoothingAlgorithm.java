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
public class ExponentialSmoothingAlgorithm implements PredictionAlgorithm {
    
    private final ObjectMapper objectMapper;
    
    private static final double DEFAULT_ALPHA = 0.3;
    private static final double DEFAULT_BETA = 0.1;
    private static final double DEFAULT_SEASONAL_PERIOD = 7.0;
    
    public ExponentialSmoothingAlgorithm(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getAlgorithmName() {
        return "指数平滑预测";
    }
    
    @Override
    public String getAlgorithmType() {
        return "EXPONENTIAL_SMOOTHING";
    }
    
    @Override
    public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("demand_prediction_ets_" + task.getDatasetId());
        modelVersion.setModelType(getAlgorithmType());
        modelVersion.setAlgorithmType(getAlgorithmType());
        modelVersion.setTaskType("TIME_SERIES");
        modelVersion.setVersionNumber(1);
        modelVersion.setStatus("ACTIVE");
        modelVersion.setTrainingSamples((long) series.size());
        modelVersion.setPredictionTaskId(task.getId());
        
        double alpha = optimizeAlpha(series);
        double[] smoothed = exponentialSmoothing(series, alpha);
        
        double mae = 0, mse = 0, mape = 0;
        for (int i = 1; i < series.size(); i++) {
            double actual = series.get(i).value();
            double predicted = smoothed[i - 1];
            double error = actual - predicted;
            mae += Math.abs(error);
            mse += error * error;
            if (actual != 0) mape += Math.abs(error / actual);
        }
        
        int validCount = series.size() - 1;
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
        metrics.put("seasonalPeriod", (int) DEFAULT_SEASONAL_PERIOD);
        modelVersion.setTrainingMetricsJson(safeJson(metrics));
        
        Map<String, Double> featureImportance = new LinkedHashMap<>();
        featureImportance.put("exponential_smoothing", 1.0);
        modelVersion.setFeatureImportanceJson(safeJson(featureImportance));
        
        modelVersion.setCreatedAt(java.time.LocalDateTime.now());
        return modelVersion;
    }
    
    @Override
    public List<PredictionResult> predict(PredictionTask task, 
                                            List<PredictionData.SeriesPoint> series, 
                                            ModelVersion modelVersion,
                                            int forecastDays) {
        Map<String, Object> metrics = parseMetrics(modelVersion.getTrainingMetricsJson());
        double alpha = metrics.containsKey("alpha") ? 
                ((Number) metrics.get("alpha")).doubleValue() : DEFAULT_ALPHA;
        
        LocalDate lastDate = series.get(series.size() - 1).date();
        double[] smoothed = exponentialSmoothing(series, alpha);
        double lastSmoothed = smoothed[smoothed.length - 1];
        
        double variance = calculateSmoothedVariance(series, smoothed);
        double stdDev = Math.sqrt(variance);
        
        List<PredictionResult> results = new ArrayList<>();
        for (int i = 1; i <= forecastDays; i++) {
            PredictionResult result = new PredictionResult();
            result.setTaskId(task.getId());
            result.setPredictionDate(lastDate.plusDays(i).toString());
            
            double decayFactor = Math.pow(1 - alpha, i);
            double predictedValue = Math.max(0, lastSmoothed * decayFactor + lastSmoothed * (1 - decayFactor));
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
        params.put("description", "考虑近期数据权重的平滑预测");
        params.put("alpha", "自动优化 (默认0.3)");
        return params;
    }
    
    private double optimizeAlpha(List<PredictionData.SeriesPoint> series) {
        double bestAlpha = DEFAULT_ALPHA;
        double bestError = Double.MAX_VALUE;
        
        for (double alpha = 0.1; alpha <= 0.9; alpha += 0.1) {
            double[] smoothed = exponentialSmoothing(series, alpha);
            double error = 0;
            for (int i = 1; i < series.size(); i++) {
                error += Math.pow(series.get(i).value() - smoothed[i - 1], 2);
            }
            if (error < bestError) {
                bestError = error;
                bestAlpha = alpha;
            }
        }
        return bestAlpha;
    }
    
    private double[] exponentialSmoothing(List<PredictionData.SeriesPoint> series, double alpha) {
        double[] smoothed = new double[series.size()];
        smoothed[0] = series.get(0).value();
        
        for (int i = 1; i < series.size(); i++) {
            smoothed[i] = alpha * series.get(i).value() + (1 - alpha) * smoothed[i - 1];
        }
        return smoothed;
    }
    
    private double calculateSmoothedVariance(List<PredictionData.SeriesPoint> series, 
                                               double[] smoothed) {
        double sumSquaredError = 0;
        for (int i = 1; i < series.size(); i++) {
            double error = series.get(i).value() - smoothed[i - 1];
            sumSquaredError += error * error;
        }
        int count = series.size() - 1;
        return count > 0 ? sumSquaredError / count : 0;
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
