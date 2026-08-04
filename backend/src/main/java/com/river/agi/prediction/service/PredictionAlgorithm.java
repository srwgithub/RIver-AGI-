package com.river.agi.prediction.service;

import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;

import java.util.List;
import java.util.Map;

public interface PredictionAlgorithm {
    
    String getAlgorithmName();
    
    String getAlgorithmType();
    
    ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series);
    
    List<PredictionResult> predict(
            PredictionTask task, 
            List<PredictionData.SeriesPoint> series, 
            ModelVersion modelVersion,
            int forecastDays
    );
    
    Map<String, Object> getAlgorithmParams(PredictionTask task);
    
    default ModelVersion trainClassification(PredictionTask task, List<double[]> features, List<Integer> labels) {
        throw new UnsupportedOperationException("Classification not supported by this algorithm");
    }
    
    default List<Map<String, Object>> predictClassification(PredictionTask task, List<double[]> features, ModelVersion modelVersion) {
        throw new UnsupportedOperationException("Classification not supported by this algorithm");
    }
    
    default boolean supportsClassification() {
        return false;
    }
}
