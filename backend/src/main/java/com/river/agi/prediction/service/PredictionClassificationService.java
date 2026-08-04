package com.river.agi.prediction.service;

import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PredictionClassificationService {

    private final PredictionTaskMapper predictionTaskMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final ObjectMapper objectMapper;
    private final List<ClassificationAlgorithm> classificationAlgorithms;
    private final DatasetDataReaderService dataReader;

    public PredictionClassificationService(PredictionTaskMapper predictionTaskMapper,
                                           ModelVersionMapper modelVersionMapper,
                                           PredictionResultMapper predictionResultMapper,
                                           ObjectMapper objectMapper,
                                           List<ClassificationAlgorithm> classificationAlgorithms,
                                           DatasetDataReaderService dataReader) {
        this.predictionTaskMapper = predictionTaskMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.predictionResultMapper = predictionResultMapper;
        this.objectMapper = objectMapper;
        this.classificationAlgorithms = classificationAlgorithms != null
                ? List.copyOf(classificationAlgorithms)
                : List.of();
        this.dataReader = dataReader;
    }

    public ModelVersion train(PredictionTask task, String algorithmType,
                              List<PredictionData.SeriesPoint> series) {
        ClassificationAlgorithm algorithm = selectAlgorithm(algorithmType);
        if (algorithm == null) {
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "没有可用的分类算法实现");
        }
        log.info("使用分类算法 {} 训练任务 {}", algorithm.getAlgorithmType(), task.getId());
        ModelVersion modelVersion = algorithm.train(task, series);
        modelVersionMapper.insert(modelVersion);
        return modelVersion;
    }

    public List<PredictionResult> predict(PredictionTask task,
                                          List<PredictionData.SeriesPoint> series,
                                          ModelVersion modelVersion,
                                          int forecastDays) {
        ClassificationAlgorithm algorithm = findAlgorithm(modelVersion.getModelType());
        if (algorithm == null) {
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "找不到对应的分类算法: " + modelVersion.getModelType());
        }
        return algorithm.predict(task, series, modelVersion, forecastDays);
    }

    public Map<String, Object> evaluate(PredictionTask task,
                                        List<PredictionData.SeriesPoint> series,
                                        ModelVersion modelVersion) {
        ClassificationAlgorithm algorithm = findAlgorithm(modelVersion.getModelType());
        if (algorithm == null) {
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "找不到对应的分类算法: " + modelVersion.getModelType());
        }
        Map<String, Object> evaluation = algorithm.evaluateClassifier(task, series, modelVersion);
        evaluation.put("algorithm", algorithm.getAlgorithmType());
        evaluation.put("algorithmName", algorithm.getAlgorithmName());
        return evaluation;
    }

    public Map<String, Object> evaluate(Long taskId, Long modelVersionId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        ModelVersion mv = resolveModelVersion(task, modelVersionId);
        List<PredictionData.SeriesPoint> series = loadSeriesForTask(task);
        return evaluate(task, series, mv);
    }

    public List<Map<String, Object>> getFeatureImportance(Long taskId, Long modelVersionId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        ModelVersion mv = resolveModelVersion(task, modelVersionId);
        return getFeatureImportance(task, mv);
    }

    private ModelVersion resolveModelVersion(PredictionTask task, Long modelVersionId) {
        Long id = modelVersionId != null ? modelVersionId : task.getModelVersionId();
        if (id == null) throw new BusinessException(ErrorCode.NOT_FOUND, "没有可用的模型版本");
        ModelVersion mv = modelVersionMapper.selectById(id);
        if (mv == null) throw new BusinessException(ErrorCode.NOT_FOUND, "模型版本不存在");
        return mv;
    }

    private List<PredictionData.SeriesPoint> loadSeriesForTask(PredictionTask task) {
        if (task.getDatasetId() == null || dataReader == null) return List.of();
        try {
            return dataReader.loadSeriesData(task.getDatasetId(), task.getTimeField(), task.getTargetField());
        } catch (Exception e) {
            log.warn("加载数据集序列失败: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getFeatureImportance(PredictionTask task,
                                                          ModelVersion modelVersion) {
        ClassificationAlgorithm algorithm = findAlgorithm(modelVersion.getModelType());
        if (algorithm == null) {
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "找不到对应的分类算法: " + modelVersion.getModelType());
        }
        return algorithm.getFeatureImportance(task, modelVersion);
    }

    public Map<String, Object> trainAndPredict(PredictionTask task,
                                               String algorithmType,
                                               List<PredictionData.SeriesPoint> series,
                                               int forecastDays) {
        ModelVersion modelVersion = train(task, algorithmType, series);
        task.setModelVersionId(modelVersion.getId());
        task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);

        List<PredictionResult> results = predict(task, series, modelVersion, forecastDays);
        for (PredictionResult result : results) {
            predictionResultMapper.insert(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("modelVersionId", modelVersion.getId());
        response.put("algorithm", modelVersion.getModelType());
        response.put("algorithmName", findAlgorithm(modelVersion.getModelType()) != null
                ? findAlgorithm(modelVersion.getModelType()).getAlgorithmName()
                : "");
        response.put("mae", modelVersion.getMae());
        response.put("rmse", modelVersion.getRmse());
        response.put("mape", modelVersion.getMape());
        response.put("results", results);
        return response;
    }

    public List<Map<String, Object>> getAvailableClassificationAlgorithms() {
        return classificationAlgorithms.stream()
                .map(algo -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("type", algo.getAlgorithmType());
                    info.put("name", algo.getAlgorithmName());
                    info.put("params", algo.getAlgorithmParams(null));
                    return info;
                })
                .collect(Collectors.toList());
    }

    public List<ModelVersion> listModelVersions(String modelName) {
        return modelVersionMapper.selectByModelName(modelName);
    }

    public ModelVersion getModelVersion(Long modelVersionId) {
        ModelVersion model = modelVersionMapper.selectById(modelVersionId);
        if (model == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型版本不存在: " + modelVersionId);
        }
        return model;
    }

    public Map<String, Object> compareClassificationAlgorithms(
            PredictionTask task, List<PredictionData.SeriesPoint> series) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        ClassificationAlgorithm best = null;
        ModelVersion bestModel = null;
        double bestAccuracy = -1;

        for (ClassificationAlgorithm algorithm : classificationAlgorithms) {
            try {
                ModelVersion model = algorithm.train(task, series);
                double accuracy = computeAccuracyFromModel(model);
                Map<String, Object> candidate = new LinkedHashMap<>();
                candidate.put("algorithm", algorithm.getAlgorithmType());
                candidate.put("name", algorithm.getAlgorithmName());
                candidate.put("mae", model.getMae());
                candidate.put("rmse", model.getRmse());
                candidate.put("mape", model.getMape());
                candidate.put("accuracy", accuracy);
                candidates.add(candidate);

                if (accuracy > bestAccuracy) {
                    bestAccuracy = accuracy;
                    best = algorithm;
                    bestModel = model;
                }
            } catch (Exception e) {
                log.warn("分类算法 {} 训练失败: {}", algorithm.getAlgorithmType(), e.getMessage());
            }
        }

        if (best == null) {
            throw new BusinessException(ErrorCode.MODEL_TRAINING_FAILED, "没有可用的分类算法");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedAlgorithm", best.getAlgorithmType());
        result.put("selectedModelVersionId", bestModel.getId());
        result.put("candidates", candidates);
        result.put("status", "OPTIMIZED");
        return result;
    }

    private ClassificationAlgorithm selectAlgorithm(String requestedType) {
        if (requestedType != null && !requestedType.isBlank()) {
            return classificationAlgorithms.stream()
                    .filter(a -> a.getAlgorithmType().equalsIgnoreCase(requestedType))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                            "未知的分类算法: " + requestedType));
        }
        return classificationAlgorithms.stream()
                .filter(a -> "LOGISTIC_REGRESSION_CLASSIFIER".equals(a.getAlgorithmType()))
                .findFirst()
                .orElse(classificationAlgorithms.isEmpty()
                        ? null
                        : classificationAlgorithms.get(0));
    }

    private ClassificationAlgorithm findAlgorithm(String algorithmType) {
        if (algorithmType == null) return null;
        return classificationAlgorithms.stream()
                .filter(a -> a.getAlgorithmType().equalsIgnoreCase(algorithmType))
                .findFirst()
                .orElse(null);
    }

    private double computeAccuracyFromModel(ModelVersion model) {
        if (model.getMae() != null) {
            return Math.max(0, 1.0 - model.getMae());
        }
        if (model.getMape() != null) {
            return Math.max(0, 1.0 - model.getMape() / 100.0);
        }
        return 0.0;
    }
}