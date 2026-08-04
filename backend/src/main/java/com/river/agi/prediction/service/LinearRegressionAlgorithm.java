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
public class LinearRegressionAlgorithm implements PredictionAlgorithm {
    
    private final ObjectMapper objectMapper;
    
    public LinearRegressionAlgorithm(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getAlgorithmName() {
        return "线性回归预测";
    }
    
    @Override
    public String getAlgorithmType() {
        return "LINEAR_REGRESSION";
    }
    
    @Override
    public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("demand_prediction_" + task.getDatasetId());
        modelVersion.setModelType(getAlgorithmType());
        modelVersion.setAlgorithmType(getAlgorithmType());
        modelVersion.setTaskType("TIME_SERIES");
        modelVersion.setVersionNumber(1);
        modelVersion.setStatus("ACTIVE");
        modelVersion.setTrainingSamples((long) series.size());
        modelVersion.setPredictionTaskId(task.getId());
        
        double[] coefficients = calculateRegression(series);
        double mae = 0, mse = 0, mape = 0;
        
        for (int i = 0; i < series.size(); i++) {
            double actual = series.get(i).value();
            double predicted = coefficients[0] + coefficients[1] * i;
            double error = predicted - actual;
            mae += Math.abs(error);
            mse += error * error;
            if (actual != 0) mape += Math.abs(error / actual);
        }
        
        mae /= series.size();
        double rmse = Math.sqrt(mse / series.size());
        double mapePct = (mape / series.size()) * 100;
        
        modelVersion.setMae(round(mae));
        modelVersion.setRmse(round(rmse));
        modelVersion.setMape(round(mapePct));
        
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("mae", modelVersion.getMae());
        metrics.put("rmse", modelVersion.getRmse());
        metrics.put("mape", modelVersion.getMape());
        metrics.put("r2", round(calculateR2(series, coefficients)));
        metrics.put("algorithm", getAlgorithmType());
        modelVersion.setTrainingMetricsJson(safeJson(metrics));
        
        Map<String, Double> featureImportance = new LinkedHashMap<>();
        featureImportance.put(task.getTimeField(), 1.0);
        modelVersion.setFeatureImportanceJson(safeJson(featureImportance));
        
        modelVersion.setCreatedAt(java.time.LocalDateTime.now());
        return modelVersion;
    }
    
    @Override
    public List<PredictionResult> predict(PredictionTask task, 
                                            List<PredictionData.SeriesPoint> series, 
                                            ModelVersion modelVersion,
                                            int forecastDays) {
        double[] coefficients = calculateRegression(series);
        LocalDate lastDate = series.get(series.size() - 1).date();
        double residual = calculateResidualStdDev(series, coefficients);
        
        List<PredictionResult> results = new ArrayList<>();
        for (int i = 1; i <= forecastDays; i++) {
            PredictionResult result = new PredictionResult();
            result.setTaskId(task.getId());
            result.setPredictionDate(lastDate.plusDays(i).toString());
            
            double predictedValue = Math.max(0, 
                coefficients[0] + coefficients[1] * (series.size() - 1 + i));
            result.setPredictedValue(round(predictedValue));
            result.setLowerBound(round(Math.max(0, predictedValue - 1.96 * residual)));
            result.setUpperBound(round(predictedValue + 1.96 * residual));
            result.setConfidence(residual == 0 ? 1.0 : 0.95);
            result.setCreatedAt(java.time.LocalDateTime.now());
            results.add(result);
        }
        return results;
    }
    
    @Override
    public Map<String, Object> getAlgorithmParams(PredictionTask task) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("algorithm", getAlgorithmType());
        params.put("description", "基于历史数据的线性趋势预测");
        return params;
    }
    
    private double[] calculateRegression(List<PredictionData.SeriesPoint> points) {
        double meanX = (points.size() - 1) / 2.0;
        double meanY = points.stream().mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        double numerator = 0, denominator = 0;
        for (int i = 0; i < points.size(); i++) {
            numerator += (i - meanX) * (points.get(i).value() - meanY);
            denominator += (i - meanX) * (i - meanX);
        }
        double slope = denominator == 0 ? 0 : numerator / denominator;
        return new double[]{meanY - slope * meanX, slope};
    }
    
    private double calculateR2(List<PredictionData.SeriesPoint> points, double[] coefficients) {
        double mean = points.stream().mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        double total = 0, residual = 0;
        for (int i = 0; i < points.size(); i++) {
            total += Math.pow(points.get(i).value() - mean, 2);
            residual += Math.pow(points.get(i).value() - (coefficients[0] + coefficients[1] * i), 2);
        }
        return total == 0 ? 1.0 : Math.max(0, 1 - residual / total);
    }
    
    private double calculateResidualStdDev(List<PredictionData.SeriesPoint> points, double[] coefficients) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            sum += Math.pow(points.get(i).value() - (coefficients[0] + coefficients[1] * i), 2);
        }
        return Math.sqrt(sum / Math.max(1, points.size() - 2));
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
