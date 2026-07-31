package com.river.agi.prediction.controller;

import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.service.AsyncTaskService;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.entity.PredictionEvaluation;
import com.river.agi.prediction.service.PredictionService;
import com.river.agi.prediction.service.EnhancedPredictionService;
import com.river.agi.prediction.service.PredictionClassificationService;
import com.river.agi.prediction.service.DeepLearningPredictionClient;
import com.river.agi.prediction.service.DeepLearningPredictionClient.DeepLearningTrainRequest;
import com.river.agi.prediction.service.DeepLearningPredictionClient.DeepLearningTrainResponse;
import com.river.agi.prediction.service.DeepLearningPredictionClient.DeepLearningPredictRequest;
import com.river.agi.prediction.service.DeepLearningPredictionClient.DeepLearningPredictResponse;
import com.river.agi.prediction.service.DeepLearningPredictionClient.DeepLearningModelInfo;
import com.river.agi.prediction.service.DeepLearningPredictionClient.DeepLearningAlgorithm;
import com.river.agi.prediction.service.DeepLearningPredictionClient.DeepLearningCrossValidateRequest;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.security.entity.AuditLog;
import com.river.agi.security.mapper.AuditLogMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

@RestController
@RequestMapping({"/api/v1/predictions", "/v1/predictions"})
@RequiredArgsConstructor
@Tag(name = "Prediction", description = "Demand prediction APIs (Time Series / Regression / Classification / Deep Learning)")
public class PredictionController {
    
    private final PredictionService predictionService;
    
    @Autowired(required = false)
    private EnhancedPredictionService enhancedPredictionService;

    @Autowired(required = false)
    private PredictionClassificationService classificationService;

    @Autowired(required = false)
    private DeepLearningPredictionClient dlClient;

    @Autowired
    private DatasetDataReaderService dataReader;

    @Autowired
    private ModelVersionMapper modelVersionMapper;

    @Autowired(required = false)
    private AsyncTaskService asyncTaskService;

    @Autowired(required = false)
    private AuditLogMapper auditLogMapper;
    
    @PostMapping
    @Operation(summary = "Create prediction", description = "Create a new prediction task and run it with best algorithm")
    public ApiResponse<PredictionTask> createPrediction(
            @RequestBody PredictionTask task,
            Authentication authentication) {
        PredictionTask created = predictionService.createPredictionTask(task, authentication);
        String algorithmType = task.getModelType();
        
        PredictionTask result;
        if (enhancedPredictionService != null && (algorithmType == null || algorithmType.isEmpty() || "AUTO".equalsIgnoreCase(algorithmType))) {
            task.setModelType("AUTO_SELECT");
            predictionService.updatePredictionType(created.getId(), "AUTO_SELECT");
            result = enhancedPredictionService.runPrediction(created.getId());
        } else {
            result = predictionService.runPrediction(created.getId());
        }
        return ApiResponse.ok(result);
    }
    
    @PostMapping("/{id}/run")
    @Operation(summary = "Run prediction", description = "Run a pending prediction task")
    public ApiResponse<PredictionTask> runPrediction(@Parameter(description = "Prediction ID") @PathVariable Long id) {
        return ApiResponse.ok(predictionService.runPrediction(id));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get prediction", description = "Get prediction task by ID")
    public ApiResponse<PredictionTask> getPrediction(@Parameter(description = "Prediction ID") @PathVariable Long id) {
        return ApiResponse.ok(predictionService.getPredictionTask(id));
    }
    
    @GetMapping("/{id}/results")
    @Operation(summary = "Get prediction results", description = "Get prediction results for a task")
    public ApiResponse<List<PredictionResult>> getPredictionResults(@Parameter(description = "Prediction ID") @PathVariable Long id) {
        return ApiResponse.ok(predictionService.getPredictionResults(id));
    }
    
    @PostMapping("/{id}/retrain")
    @Operation(summary = "Retrain prediction", description = "Retrain the model for a prediction task with selected algorithm")
    public ApiResponse<PredictionTask> retrainPrediction(@Parameter(description = "Prediction ID") @PathVariable Long id) {
        if (enhancedPredictionService != null) {
            return ApiResponse.ok(enhancedPredictionService.retrainPrediction(id));
        }
        return ApiResponse.ok(predictionService.retrainPrediction(id));
    }
    
    @GetMapping
    @Operation(summary = "List predictions", description = "Get paginated list of prediction tasks")
    public ApiResponse<PageResult<PredictionTask>> getPredictions(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(predictionService.getPredictionTasks(page, size));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete prediction", description = "Delete prediction task by ID")
    public ApiResponse<Void> deletePrediction(@Parameter(description = "Prediction ID") @PathVariable Long id) {
        predictionService.deletePredictionTask(id);
        return ApiResponse.ok(null);
    }
    
    @GetMapping("/models/{modelVersionId}")
    @Operation(summary = "Get model version", description = "Get model version details")
    public ApiResponse<ModelVersion> getModelVersion(@Parameter(description = "Model version ID") @PathVariable Long modelVersionId) {
        return ApiResponse.ok(predictionService.getModelVersion(modelVersionId));
    }
    
    @GetMapping("/models")
    @Operation(summary = "List model versions", description = "Get model versions by model name")
    public ApiResponse<List<ModelVersion>> getModelVersions(@Parameter(description = "Model name") @RequestParam(required = false) String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return ApiResponse.ok(modelVersionMapper.selectList(new LambdaQueryWrapper<ModelVersion>()
                    .orderByDesc(ModelVersion::getCreatedAt)));
        }
        return ApiResponse.ok(predictionService.getModelVersions(modelName));
    }

    @PostMapping("/models/{id}/set-production")
    @Operation(summary = "Set production model", description = "Set a model version as production and demote other versions")
    public ApiResponse<ModelVersion> setProductionModel(@PathVariable Long id) {
        Long targetId = id;
        ModelVersion target = modelVersionMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException("模型版本不存在: " + targetId);
        }
        if (target.getModelName() != null) {
            modelVersionMapper.update(null, new LambdaUpdateWrapper<ModelVersion>()
                    .eq(ModelVersion::getModelName, target.getModelName())
                    .set(ModelVersion::getIsProduction, false)
                    .set(ModelVersion::getStatus, "ACTIVE"));
        }
        target.setIsProduction(true);
        target.setStatus("PRODUCTION");
        target.setUpdatedAt(LocalDateTime.now());
        modelVersionMapper.updateById(target);

        if (auditLogMapper != null) {
            try {
                AuditLog log = new AuditLog();
                log.setActionType("SET_PRODUCTION_MODEL");
                log.setResourceType("MODEL_VERSION");
                log.setResourceId(targetId);
                log.setResourceName(target.getModelName() + " v" + target.getVersionNumber());
                log.setResult("SUCCESS");
                log.setOperationDetails("{\"modelVersionId\":" + targetId + ",\"modelName\":\"" + target.getModelName() + "\"}");
                log.setCreatedAt(LocalDateTime.now());
                auditLogMapper.insert(log);
            } catch (Exception e) {
                // Audit log failure should not block the main operation
            }
        }

        return ApiResponse.ok(target);
    }
    
    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get prediction metrics", description = "Get prediction model evaluation metrics including MAE, RMSE, MAPE, R2")
    public ApiResponse<Map<String, Object>> getPredictionMetrics(@Parameter(description = "Prediction ID") @PathVariable Long id) {
        return ApiResponse.ok(predictionService.getPredictionMetrics(id));
    }

    @GetMapping("/{id}/evaluation-history")
    @Operation(summary = "Get evaluation history", description = "Get model evaluation history for monitoring")
    public ApiResponse<List<PredictionEvaluation>> getEvaluationHistory(@PathVariable Long id) {
        if (enhancedPredictionService == null) throw new BusinessException("Enhanced prediction service not available");
        return ApiResponse.ok(enhancedPredictionService.getEvaluationHistory(id));
    }

    @PostMapping("/{id}/evaluate")
    @Operation(summary = "Evaluate prediction", description = "Evaluate current model and record a traceable snapshot")
    public ApiResponse<PredictionEvaluation> evaluatePrediction(@PathVariable Long id) {
        if (enhancedPredictionService == null) throw new BusinessException("Enhanced prediction service not available");
        return ApiResponse.ok(enhancedPredictionService.evaluateAndRecord(id));
    }

    @PostMapping("/{id}/auto-tune")
    @Operation(summary = "Auto tune model", description = "Train candidate algorithms and select the best model")
    public ApiResponse<Map<String, Object>> autoTune(@PathVariable Long id) {
        if (enhancedPredictionService == null) throw new BusinessException("Enhanced prediction service not available");
        return ApiResponse.ok(enhancedPredictionService.autoTune(id));
    }
    
    @GetMapping("/{id}/comparison")
    @Operation(summary = "Get prediction comparison", description = "Compare prediction results with actual values and analyze deviations")
    public ApiResponse<Map<String, Object>> getPredictionComparison(@Parameter(description = "Prediction ID") @PathVariable Long id) {
        return ApiResponse.ok(predictionService.getPredictionComparison(id));
    }
    
    @GetMapping("/count")
    @Operation(summary = "Count prediction tasks", description = "Get total number of prediction tasks")
    public ApiResponse<Long> countPredictionTasks() {
        return ApiResponse.ok(predictionService.getPredictionTaskCount());
    }
    
    @GetMapping("/algorithms")
    @Operation(summary = "List available algorithms", description = "Get all available prediction algorithms")
    public ApiResponse<List<Map<String, Object>>> getAvailableAlgorithms() {
        if (enhancedPredictionService != null) {
            return ApiResponse.ok(enhancedPredictionService.getAvailableAlgorithms());
        }
        return ApiResponse.ok(List.of(
                Map.of("type", "LINEAR_REGRESSION", "name", "线性回归", "description", "基于历史数据的线性趋势预测")
        ));
    }
    
    @PostMapping("/models/compare")
    @Operation(summary = "Compare model versions", description = "Compare two model versions and recommend the better one")
    public ApiResponse<Map<String, Object>> compareModels(@RequestBody Map<String, Object> request) {
        Long modelId1 = request.get("modelId1") != null ? Long.valueOf(request.get("modelId1").toString()) : null;
        Long modelId2 = request.get("modelId2") != null ? Long.valueOf(request.get("modelId2").toString()) : null;

        if (modelId1 == null && request.get("championId") != null) {
            modelId1 = Long.valueOf(request.get("championId").toString());
        }
        if (modelId2 == null && request.get("challengerId") != null) {
            modelId2 = Long.valueOf(request.get("challengerId").toString());
        }

        if (enhancedPredictionService != null) {
            return ApiResponse.ok(enhancedPredictionService.compareModelVersions(modelId1, modelId2));
        }
        throw new BusinessException("Enhanced prediction service not available");
    }
    
    @PostMapping("/{id}/bias-detection")
    @Operation(summary = "Detect prediction bias", description = "Detect bias in prediction results compared to actual data")
    public ApiResponse<Map<String, Object>> detectBias(@Parameter(description = "Task ID") @PathVariable Long id) {
        if (enhancedPredictionService != null) {
            return ApiResponse.ok(enhancedPredictionService.detectPredictionBias(id));
        }
        throw new BusinessException("Enhanced prediction service not available");
    }
    
    @PostMapping("/{id}/rollback")
    @Operation(summary = "Rollback model version", description = "Rollback to a previous model version")
    public ApiResponse<?> rollbackModel(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        if (enhancedPredictionService != null) {
            return ApiResponse.ok(enhancedPredictionService.rollbackToModelVersion(id, request.get("modelVersionId")));
        }
        throw new BusinessException("Enhanced prediction service not available");
    }
    
    @PostMapping("/{id}/auto-retrain")
    @Operation(summary = "Auto retrain on bias", description = "Automatically retrain if bias is detected")
    public ApiResponse<Map<String, Object>> autoRetrainOnBias(
            @Parameter(description = "Task ID") @PathVariable Long id) {
        if (enhancedPredictionService != null) {
            enhancedPredictionService.autoRetrainOnBias(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", id);
            result.put("message", "Auto-retrain triggered if bias detected");
            return ApiResponse.ok(result);
        }
        throw new BusinessException("Enhanced prediction service not available");
    }

    // ==================== 分类预测 ====================

    @PostMapping("/{id}/classification/{algorithm}")
    @Operation(summary = "Classification train", description = "Train a classification model (LOGISTIC_REGRESSION_CLASSIFIER / DECISION_TREE_CLASSIFIER / RANDOM_FOREST_CLASSIFIER)")
    public ApiResponse<Map<String, Object>> trainClassification(
            @PathVariable Long id,
            @PathVariable String algorithm,
            Authentication authentication) {
        if (classificationService == null) {
            throw new BusinessException("Classification service not available");
        }
        PredictionTask task = predictionService.getPredictionTask(id);
        ModelVersion mv = classificationService.train(task, algorithm,
                dataReader.loadSeriesData(task.getDatasetId(), task.getTimeField(), task.getTargetField()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelVersionId", mv.getId());
        result.put("algorithm", algorithm);
        result.put("metrics", mv.getTrainingMetricsJson());
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}/classification/evaluate")
    @Operation(summary = "Evaluate classification", description = "Evaluate classification model metrics (accuracy/precision/recall/F1)")
    public ApiResponse<Map<String, Object>> evaluateClassification(
            @PathVariable Long id,
            @RequestParam(required = false) Long modelVersionId) {
        if (classificationService == null) {
            throw new BusinessException("Classification service not available");
        }
        return ApiResponse.ok(classificationService.evaluate(id, modelVersionId));
    }

    @GetMapping("/{id}/classification/feature-importance")
    @Operation(summary = "Feature importance", description = "Get feature importance ranking for classification model")
    public ApiResponse<List<Map<String, Object>>> getFeatureImportance(
            @PathVariable Long id,
            @RequestParam(required = false) Long modelVersionId) {
        if (classificationService == null) {
            throw new BusinessException("Classification service not available");
        }
        return ApiResponse.ok(classificationService.getFeatureImportance(id, modelVersionId));
    }

    // ==================== 深度学习预测（调用 Python 微服务） ====================

    @PostMapping("/deep-learning/train")
    @Operation(summary = "Deep learning train", description = "Train a model via Python DL engine (LSTM / Transformer / MLP / RandomForest / GBDT / SVM)")
    public ApiResponse<DeepLearningTrainResponse> trainDeepLearning(@RequestBody DeepLearningTrainRequest request) {
        if (dlClient == null || !dlClient.isDlEngineEnabled()) {
            throw new BusinessException("Python 深度学习引擎未启用，请设置 DL_ENGINE_ENABLED=true");
        }
        return ApiResponse.ok(dlClient.train(request));
    }

    @PostMapping("/deep-learning/predict")
    @Operation(summary = "Deep learning predict", description = "Predict using a trained deep learning model")
    public ApiResponse<DeepLearningPredictResponse> predictDeepLearning(@RequestBody DeepLearningPredictRequest request) {
        if (dlClient == null || !dlClient.isDlEngineEnabled()) {
            throw new BusinessException("Python 深度学习引擎未启用");
        }
        return ApiResponse.ok(dlClient.predict(request));
    }

    @GetMapping("/deep-learning/models")
    @Operation(summary = "List DL models", description = "List all deep learning models (from local DB + Python engine if enabled)")
    public ApiResponse<List<DeepLearningModelInfo>> listDeepLearningModels() {
        List<DeepLearningModelInfo> models = new ArrayList<>();

        // 1. 从本地 model_version 表获取已训练的模型（始终可用）
        List<ModelVersion> localModels = modelVersionMapper.selectList(
                new LambdaQueryWrapper<ModelVersion>()
                        .isNotNull(ModelVersion::getVersionNumber)
                        .orderByDesc(ModelVersion::getCreatedAt)
        );
        for (ModelVersion mv : localModels) {
            Map<String, Object> metrics = new HashMap<>();
            if (mv.getMae() != null) metrics.put("mae", mv.getMae());
            if (mv.getRmse() != null) metrics.put("rmse", mv.getRmse());
            if (mv.getMape() != null) metrics.put("mape", mv.getMape());
            String trainingMetrics = mv.getTrainingMetricsJson();
            if (trainingMetrics != null && !trainingMetrics.isBlank()) {
                try {
                    Map<String, Object> extra = new com.fasterxml.jackson.databind.ObjectMapper().readValue(trainingMetrics, Map.class);
                    metrics.putAll(extra);
                } catch (Exception ignored) {}
            }
            Map<String, Object> params = new HashMap<>();
            if (mv.getAlgorithmParams() != null) {
                try {
                    params = new com.fasterxml.jackson.databind.ObjectMapper().readValue(mv.getAlgorithmParams(), Map.class);
                } catch (Exception ignored) {}
            }
            models.add(new DeepLearningModelInfo(
                    "local-" + mv.getId(),
                    mv.getModelName() != null ? mv.getModelName() : ("Model-v" + mv.getVersionNumber()),
                    mv.getAlgorithmType() != null ? mv.getAlgorithmType() : "UNKNOWN",
                    mv.getTaskType() != null ? mv.getTaskType() : "TIME_SERIES",
                    mv.getStatus() != null ? mv.getStatus() : "ACTIVE",
                    metrics,
                    params,
                    mv.getTrainingSamples()
            ));
        }

        // 2. 如果Python引擎启用且可达，合并Python端模型
        if (dlClient != null && dlClient.isDlEngineEnabled() && dlClient.isServiceAvailable()) {
            try {
                List<DeepLearningModelInfo> pyModels = dlClient.listModels();
                for (DeepLearningModelInfo py : pyModels) {
                    // 避免重复（以modelId前缀区分）
                    boolean dup = models.stream().anyMatch(m -> m.modelId().equals(py.modelId()));
                    if (!dup) models.add(py);
                }
            } catch (Exception e) {
                // Python引擎调用失败不影响本地模型列表返回
                // 前端可通过 /deep-learning/status 感知引擎状态
            }
        }

        return ApiResponse.ok(models);
    }

    @GetMapping("/deep-learning/models/{modelId}")
    @Operation(summary = "Get DL model", description = "Get deep learning model details")
    public ApiResponse<DeepLearningModelInfo> getDeepLearningModel(@PathVariable String modelId) {
        if (dlClient == null || !dlClient.isDlEngineEnabled()) {
            throw new BusinessException("Python 深度学习引擎未启用");
        }
        return ApiResponse.ok(dlClient.getModel(modelId));
    }

    @DeleteMapping("/deep-learning/models/{modelId}")
    @Operation(summary = "Delete DL model", description = "Delete a deep learning model")
    public ApiResponse<Void> deleteDeepLearningModel(@PathVariable String modelId) {
        if (dlClient == null || !dlClient.isDlEngineEnabled()) {
            throw new BusinessException("Python 深度学习引擎未启用");
        }
        dlClient.deleteModel(modelId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/deep-learning/cross-validate")
    @Operation(summary = "Cross validation", description = "Walk-forward cross validation via Python DL engine")
    public ApiResponse<Map<String, Object>> crossValidateDeepLearning(@RequestBody DeepLearningCrossValidateRequest request) {
        if (dlClient == null || !dlClient.isDlEngineEnabled()) {
            throw new BusinessException("Python 深度学习引擎未启用");
        }
        return ApiResponse.ok(dlClient.crossValidate(request));
    }

    @PostMapping("/deep-learning/models/compare")
    @Operation(summary = "Compare DL models", description = "Compare two deep learning models")
    public ApiResponse<Map<String, Object>> compareDeepLearningModels(
            @RequestBody Map<String, Object> request) {
        if (dlClient == null || !dlClient.isDlEngineEnabled()) {
            throw new BusinessException("Python 深度学习引擎未启用");
        }
        String modelId1 = (String) request.get("modelId1");
        String modelId2 = (String) request.get("modelId2");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        return ApiResponse.ok(dlClient.compareModels(modelId1, modelId2, params));
    }

    @GetMapping("/deep-learning/algorithms")
    @Operation(summary = "List DL algorithms", description = "List all deep learning algorithms available")
    public ApiResponse<List<DeepLearningAlgorithm>> listDeepLearningAlgorithms() {
        if (dlClient == null || !dlClient.isDlEngineEnabled()) {
            return ApiResponse.ok(java.util.List.of(
                    new DeepLearningAlgorithm("LSTM", "LSTM 序列预测", "PyTorch LSTM 时间序列预测", java.util.List.of("REGRESSION", "TIME_SERIES")),
                    new DeepLearningAlgorithm("TRANSFORMER", "Transformer 序列预测", "PyTorch Transformer 序列预测", java.util.List.of("REGRESSION", "TIME_SERIES")),
                    new DeepLearningAlgorithm("MLP", "MLP 多层感知机", "TensorFlow/Keras MLP 回归分类", java.util.List.of("REGRESSION", "CLASSIFICATION")),
                    new DeepLearningAlgorithm("RANDOM_FOREST_DL", "随机森林", "scikit-learn RandomForest", java.util.List.of("REGRESSION", "CLASSIFICATION")),
                    new DeepLearningAlgorithm("GRADIENT_BOOOSTING", "梯度提升", "scikit-learn GradientBoosting", java.util.List.of("REGRESSION", "CLASSIFICATION")),
                    new DeepLearningAlgorithm("SVM", "支持向量机", "scikit-learn SVM", java.util.List.of("REGRESSION", "CLASSIFICATION"))
            ));
        }
        return ApiResponse.ok(dlClient.listAlgorithms());
    }

    @GetMapping("/deep-learning/status")
    @Operation(summary = "DL engine status", description = "Check if Python deep learning engine is reachable")
    public ApiResponse<Map<String, Object>> getDlEngineStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean enabled = dlClient != null && dlClient.isDlEngineEnabled();
        boolean reachable = enabled && dlClient.isServiceAvailable();
        status.put("enabled", enabled);
        status.put("reachable", reachable);
        status.put("engineUrl", dlClient != null ? dlClient.isDlEngineEnabled() ? "http://localhost:5000" : "disabled" : "not_configured");
        return ApiResponse.ok(status);
    }

    @GetMapping("/deep-learning/train/{taskId}")
    @Operation(summary = "Get DL training status", description = "Get deep learning model training status by task ID")
    public ApiResponse<Map<String, Object>> getDeepLearningTrainStatus(@PathVariable Long taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);

        String status = "PENDING";
        int progress = 0;
        List<String> logs = new ArrayList<>();
        String modelId = null;
        String errorMessage = null;
        String finishedAt = null;

        if (asyncTaskService != null) {
            try {
                List<AsyncTask> tasks = asyncTaskService.getTasksByResource("MODEL_TRAIN", taskId);
                if (!tasks.isEmpty()) {
                    AsyncTask task = tasks.get(0);
                    status = task.getStatus();
                    progress = task.getProgress() != null ? task.getProgress() : 0;
                    errorMessage = task.getErrorMessage();
                    if (task.getCompletedAt() != null) {
                        finishedAt = task.getCompletedAt().toString();
                    }
                    if (task.getResultJson() != null) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            Map<String, Object> resultMap = mapper.readValue(task.getResultJson(), Map.class);
                            if (resultMap != null && resultMap.get("modelId") != null) {
                                modelId = resultMap.get("modelId").toString();
                            }
                            if (resultMap != null && resultMap.get("logs") != null) {
                                @SuppressWarnings("unchecked")
                                List<String> taskLogs = (List<String>) resultMap.get("logs");
                                if (taskLogs != null) logs = taskLogs;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                // If async task lookup fails, try other approaches
            }
        }

        if (dlClient != null && dlClient.isDlEngineEnabled() && dlClient.isServiceAvailable()) {
            try {
                String url = dlClient.toString(); // Can't easily get training status from Python without an endpoint, fall back to async task info
                logs.add("Python DL engine is available");
            } catch (Exception ignored) {}
        }

        if ("COMPLETED".equals(status) && modelId == null) {
            PredictionTask predTask = predictionService.getPredictionTask(taskId);
            if (predTask != null && predTask.getDlModelId() != null) {
                modelId = predTask.getDlModelId();
            }
        }

        result.put("status", status);
        result.put("progress", progress);
        result.put("logs", logs);
        result.put("modelId", modelId);
        result.put("errorMessage", errorMessage);
        result.put("finishedAt", finishedAt);

        return ApiResponse.ok(result);
    }
}
