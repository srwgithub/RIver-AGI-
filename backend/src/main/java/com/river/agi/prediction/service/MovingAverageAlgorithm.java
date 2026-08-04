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
public class MovingAverageAlgorithm implements PredictionAlgorithm {
    
    private final ObjectMapper objectMapper;
    
    public MovingAverageAlgorithm(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getAlgorithmName() {
        return "移动平均预测";
    }
    
    @Override
    public String getAlgorithmType() {
        return "MOVING_AVERAGE";
    }
    
    @Override
    public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("demand_prediction_ma_" + task.getDatasetId());
        modelVersion.setModelType(getAlgorithmType());
        modelVersion.setAlgorithmType(getAlgorithmType());
        modelVersion.setTaskType("TIME_SERIES");
        modelVersion.setVersionNumber(1);
        modelVersion.setStatus("ACTIVE");
        modelVersion.setTrainingSamples((long) series.size());
        modelVersion.setPredictionTaskId(task.getId());
        
        int windowSize = calculateWindowSize(series.size());
        double[] predictions = calculateMovingAverage(series, windowSize);
        
        double mae = 0, mse = 0, mape = 0;
        int validCount = 0;
        
        for (int i = windowSize; i < series.size(); i++) {
            double actual = series.get(i).value();
            double predicted = predictions[i];
            double error = predicted - actual;
            mae += Math.abs(error);
            mse += error * error;
            if (actual != 0) mape += Math.abs(error / actual);
            validCount++;
        }
        
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
        metrics.put("windowSize", windowSize);
        modelVersion.setTrainingMetricsJson(safeJson(metrics));
        
        Map<String, Double> featureImportance = new LinkedHashMap<>();
        featureImportance.put("moving_average", 1.0);
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
        int windowSize = metrics.containsKey("windowSize") ? ((Number) metrics.get("windowSize")).intValue() : 7;
        
        LocalDate lastDate = series.get(series.size() - 1).date();
        double[] predictions = calculateMovingAverage(series, windowSize);
        double lastPrediction = predictions[predictions.length - 1];
        
        List<PredictionResult> results = new ArrayList<>();
        for (int i = 1; i <= forecastDays; i++) {
            PredictionResult result = new PredictionResult();
            result.setTaskId(task.getId());
            result.setPredictionDate(lastDate.plusDays(i).toString());
            
            double predictedValue = Math.max(0, lastPrediction);
            result.setPredictedValue(round(predictedValue));
            
            double variance = calculateVariance(series, predictions, windowSize);
            double stdDev = Math.sqrt(variance);
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
        params.put("description", "基于历史平均值的平稳趋势预测");
        params.put("windowSize", "自动计算 (建议7-30天)");
        return params;
    }
    
    private int calculateWindowSize(int dataSize) {
        if (dataSize < 7) return Math.max(1, dataSize / 2);
        if (dataSize < 30) return 7;
        if (dataSize < 90) return 14;
        return 30;
    }
    
    private double[] calculateMovingAverage(List<PredictionData.SeriesPoint> series, int windowSize) {
        double[] result = new double[series.size()];
        double sum = 0;
        
        for (int i = 0; i < series.size(); i++) {
            sum += series.get(i).value();
            if (i >= windowSize) {
                sum -= series.get(i - windowSize).value();
            }
            result[i] = i >= windowSize - 1 ? sum / windowSize : series.get(i).value();
        }
        return result;
    }
    
    private double calculateVariance(List<PredictionData.SeriesPoint> series, 
                                       double[] predictions, int windowSize) {
        double sumSquaredError = 0;
        int count = 0;
        for (int i = windowSize; i < series.size(); i++) {
            double error = series.get(i).value() - predictions[i];
            sumSquaredError += error * error;
            count++;
        }
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
