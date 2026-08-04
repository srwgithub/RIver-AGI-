package com.river.agi.prediction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DeepLearningPredictionService {

    private final DeepLearningPredictionClient dlClient;
    private final PredictionTaskMapper predictionTaskMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final ObjectMapper objectMapper;
    private final PredictionClassificationService classificationService;

    public DeepLearningPredictionService(DeepLearningPredictionClient dlClient,
                                         PredictionTaskMapper predictionTaskMapper,
                                         ModelVersionMapper modelVersionMapper,
                                         PredictionResultMapper predictionResultMapper,
                                         ObjectMapper objectMapper,
                                         PredictionClassificationService classificationService) {
        this.dlClient = dlClient;
        this.predictionTaskMapper = predictionTaskMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.predictionResultMapper = predictionResultMapper;
        this.objectMapper = objectMapper;
        this.classificationService = classificationService;
    }

    public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
        if (dlClient.isDlEngineEnabled() && dlClient.isServiceAvailable()) {
            try {
                return trainViaPython(task, series);
            } catch (BusinessException e) {
                log.warn("Python 深度学习引擎不可用，回退到 Java 分类算法: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("Python 深度学习训练异常，回退到 Java 分类算法: {}", e.getMessage());
            }
        } else {
            log.info("Python 深度学习引擎未启用或不可用，使用 Java 分类算法回退");
        }
        return trainViaJavaFallback(task, series);
    }

    /**
     * Train a task explicitly selected for the Python engine.
     * Unlike the legacy compatibility method above, this method never falls
     * back to a Java algorithm: a successful response must be a real Python
     * model, otherwise the task must be reported as failed.
     */
    public ModelVersion trainStrict(PredictionTask task, List<PredictionData.SeriesPoint> series) {
        if (!dlClient.isDlEngineEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用，请设置 DL_ENGINE_ENABLED=true");
        }
        if (!dlClient.isServiceAvailable()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎不可达: " + dlClient.getEngineUrl());
        }
        return trainViaPython(task, series);
    }

    public List<PredictionResult> predict(PredictionTask task,
                                           List<PredictionData.SeriesPoint> series,
                                           ModelVersion modelVersion,
                                           int forecastDays) {
        if (dlClient.isDlEngineEnabled() && dlClient.isServiceAvailable()) {
            try {
                return predictViaPython(task, series, modelVersion, forecastDays);
            } catch (BusinessException e) {
                log.warn("Python 预测服务不可用，回退到 Java 分类算法: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("Python 预测异常，回退到 Java 分类算法: {}", e.getMessage());
            }
        }
        return predictViaJavaFallback(task, series, modelVersion, forecastDays);
    }

    /** Predict with the persisted Python model without silently switching engines. */
    public List<PredictionResult> predictStrict(PredictionTask task,
                                                 List<PredictionData.SeriesPoint> series,
                                                 ModelVersion modelVersion,
                                                 int forecastDays) {
        if (!dlClient.isDlEngineEnabled() || !dlClient.isServiceAvailable()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎不可用，无法执行真实深度学习预测");
        }
        return predictViaPython(task, series, modelVersion, forecastDays);
    }

    public Map<String, Object> crossValidate(PredictionTask task,
                                             List<PredictionData.SeriesPoint> series,
                                             String modelType, int cvFolds, String cvStrategy) {
        if (!dlClient.isDlEngineEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用，交叉验证不可用");
        }

        Map<String, Object> features = buildFeaturePayload(series, task);
        DeepLearningPredictionClient.DeepLearningCrossValidateRequest request =
                new DeepLearningPredictionClient.DeepLearningCrossValidateRequest(
                        modelType, task.getModelType() != null ? task.getModelType() : "CLASSIFICATION",
                        features, cvFolds, cvStrategy, null
                );
        return dlClient.crossValidate(request);
    }

    public Map<String, Object> compareModels(String modelId1, String modelId2, Map<String, Object> params) {
        if (!dlClient.isDlEngineEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用，模型对比不可用");
        }
        return dlClient.compareModels(modelId1, modelId2, params);
    }

    public List<Map<String, Object>> listDlAlgorithms() {
        if (!dlClient.isDlEngineEnabled()) {
            return List.of();
        }
        return dlClient.listAlgorithms().stream()
                .map(algo -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("type", algo.type());
                    info.put("name", algo.name());
                    info.put("description", algo.description());
                    info.put("supportedTasks", algo.supportedTasks());
                    return info;
                })
                .toList();
    }

    public DeepLearningPredictionClient.DeepLearningModelInfo getDlModel(String modelId) {
        return dlClient.getModel(modelId);
    }

    public void deleteDlModel(String modelId) {
        dlClient.deleteModel(modelId);
    }

    private ModelVersion trainViaPython(PredictionTask task,
                                         List<PredictionData.SeriesPoint> series) {
        Map<String, Object> features = buildFeaturePayload(series, task);
        Map<String, Object> parameters = parseParameters(task.getParametersJson());

        DeepLearningPredictionClient.DeepLearningTrainRequest request =
                new DeepLearningPredictionClient.DeepLearningTrainRequest(
                        task.getModelType(),
                        resolveTaskType(task),
                        features,
                        parameters
                );

        DeepLearningPredictionClient.DeepLearningTrainResponse response = dlClient.train(request);

        ModelVersion modelVersion = convertTrainResponseToModelVersion(response, task);
        log.info("Python 深度学习模型训练成功: modelId={}, versionId={}",
                response.modelId(), modelVersion.getId());
        return modelVersion;
    }

    private List<PredictionResult> predictViaPython(PredictionTask task,
                                                    List<PredictionData.SeriesPoint> series,
                                                    ModelVersion modelVersion,
                                                    int forecastDays) {
        Map<String, Object> features = buildFeaturePayload(series, task);

        DeepLearningPredictionClient.DeepLearningPredictRequest request =
                new DeepLearningPredictionClient.DeepLearningPredictRequest(
                        modelVersion.getModelPath() != null
                                ? modelVersion.getModelPath() : modelVersion.getModelName(),
                        features
                );

        DeepLearningPredictionClient.DeepLearningPredictResponse response = dlClient.predict(request);
        return convertPredictResponseToResults(response, task, series, forecastDays);
    }

    private ModelVersion trainViaJavaFallback(PredictionTask task,
                                               List<PredictionData.SeriesPoint> series) {
        log.info("执行 Java 分类算法回退训练, 任务ID: {}", task.getId());
        return classificationService.train(task, task.getModelType(), series);
    }

    private List<PredictionResult> predictViaJavaFallback(PredictionTask task,
                                                           List<PredictionData.SeriesPoint> series,
                                                           ModelVersion modelVersion,
                                                           int forecastDays) {
        return classificationService.predict(task, series, modelVersion, forecastDays);
    }

    private ModelVersion convertTrainResponseToModelVersion(
            DeepLearningPredictionClient.DeepLearningTrainResponse response,
            PredictionTask task) {
        ModelVersion mv = new ModelVersion();
        mv.setModelName(response.modelName() != null
                ? response.modelName()
                : "dl_model_" + task.getDatasetId());
        mv.setModelType(response.modelType() != null
                ? response.modelType()
                : task.getModelType());
        mv.setAlgorithmType(response.modelType() != null
                ? response.modelType()
                : task.getModelType());
        mv.setTaskType(response.taskType() != null
                ? response.taskType()
                : resolveTaskType(task));
        mv.setModelPath(response.modelId());
        mv.setVersionNumber(1);
        mv.setPredictionTaskId(task.getId());
        mv.setStatus(response.status() != null ? response.status() : "ACTIVE");

        Map<String, Object> metrics = response.metrics();
        if (metrics != null) {
            mv.setMae(asDouble(metrics.get("mae")));
            mv.setRmse(asDouble(metrics.get("rmse")));
            mv.setMape(asDouble(metrics.get("mape")));
        }

        Map<String, Object> trainingData = new LinkedHashMap<>();
        trainingData.put("modelId", response.modelId());
        trainingData.put("modelName", response.modelName());
        trainingData.put("modelType", response.modelType());
        trainingData.put("taskType", response.taskType());
        trainingData.put("metrics", response.metrics());
        trainingData.put("parameters", response.parameters());
        trainingData.put("engine", "PYTHON_DL");
        mv.setTrainingMetricsJson(toJson(trainingData));

        Map<String, Object> featureImportance = new LinkedHashMap<>();
        featureImportance.put("note", "特征重要性由 Python 引擎计算");
        mv.setFeatureImportanceJson(toJson(featureImportance));

        mv.setCreatedAt(LocalDateTime.now());
        return mv;
    }

    private List<PredictionResult> convertPredictResponseToResults(
            DeepLearningPredictionClient.DeepLearningPredictResponse response,
            PredictionTask task,
            List<PredictionData.SeriesPoint> series,
            int forecastDays) {

        List<PredictionResult> results = new ArrayList<>();
        if (response.predictions() == null || response.predictions().isEmpty()) {
            return results;
        }

        LocalDate lastDate = series.isEmpty() ? LocalDate.now() : series.get(series.size() - 1).date();
        int count = Math.min(response.predictions().size(), forecastDays);

        for (int i = 0; i < count; i++) {
            Map<String, Object> prediction = response.predictions().get(i);
            PredictionResult result = new PredictionResult();
            result.setTaskId(task.getId());
            result.setPredictionDate(lastDate.plusDays(i + 1).toString());
            result.setPredictedValue(asDouble(prediction.get("predictedValue")));
            result.setConfidence(asDouble(prediction.get("confidence")));
            result.setLowerBound(asDouble(prediction.get("lowerBound")));
            result.setUpperBound(asDouble(prediction.get("upperBound")));
            result.setCreatedAt(LocalDateTime.now());
            results.add(result);

        }

        return results;
    }

    private Map<String, Object> buildFeaturePayload(List<PredictionData.SeriesPoint> series,
                                                     PredictionTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();

        List<String> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (PredictionData.SeriesPoint point : series) {
            dates.add(point.date().toString());
            values.add(point.value());
        }
        payload.put("dates", dates);
        payload.put("values", values);
        payload.put("targetField", task.getTargetField());
        payload.put("timeField", task.getTimeField());
        payload.put("forecastDays", task.getForecastDays() != null ? task.getForecastDays() : 30);

        if (task.getParametersJson() != null && !task.getParametersJson().isBlank()) {
            try {
                Map<String, Object> params = objectMapper.readValue(
                        task.getParametersJson(), new TypeReference<>() {});
                payload.put("parameters", params);
            } catch (Exception e) {
                log.warn("解析预测参数失败: {}", e.getMessage());
            }
        }

        return payload;
    }

    private String resolveTaskType(PredictionTask task) {
        String modelType = task.getModelType();
        String requestedTask = task.getTaskType();
        return "CLASSIFICATION".equalsIgnoreCase(requestedTask)
                ? "CLASSIFICATION" : "REGRESSION";
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析参数失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
