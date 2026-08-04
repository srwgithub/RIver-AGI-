package com.river.agi.prediction.service;

import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionTask;

import java.util.List;
import java.util.Map;

public interface ClassificationAlgorithm extends PredictionAlgorithm {

    Map<String, Object> evaluateClassifier(PredictionTask task, List<PredictionData.SeriesPoint> series, ModelVersion modelVersion);

    List<Map<String, Object>> getFeatureImportance(PredictionTask task, ModelVersion modelVersion);
}