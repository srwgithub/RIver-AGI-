package com.river.agi.prediction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.entity.PredictionEvaluation;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.prediction.mapper.PredictionEvaluationMapper;
import org.springframework.beans.factory.annotation.Value;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.annotation.AuditOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class EnhancedPredictionService {
    
    private final PredictionTaskMapper predictionTaskMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final DatasetMapper datasetMapper;
    private final ObjectMapper objectMapper;
    private final SecurityUtils securityUtils;
    private final ResourceAccessValidator accessValidator;
    private final DatasetDataReaderService dataReader;
    private final List<PredictionAlgorithm> algorithms;
    private final PredictionEvaluationMapper evaluationMapper;
    private Executor taskExecutor;
    private RuntimeMonitoringService runtimeMonitoringService;
    private DeepLearningPredictionService deepLearningPredictionService;
    
    private static final int DEFAULT_FORECAST_DAYS = 30;
    private static final int MAX_FORECAST_DAYS = 365;
    private static final long DEFAULT_RETRAIN_COOLDOWN_MINUTES = 60;

    // Compatibility constructor retained for existing unit tests.
    public EnhancedPredictionService(PredictionTaskMapper predictionTaskMapper, ModelVersionMapper modelVersionMapper,
                                     PredictionResultMapper predictionResultMapper, DatasetMapper datasetMapper,
                                     ObjectMapper objectMapper, SecurityUtils securityUtils,
                                     ResourceAccessValidator accessValidator, DatasetDataReaderService dataReader,
                                     List<PredictionAlgorithm> algorithms) {
        this(predictionTaskMapper, modelVersionMapper, predictionResultMapper, datasetMapper, objectMapper,
                securityUtils, accessValidator, dataReader, algorithms, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EnhancedPredictionService(PredictionTaskMapper predictionTaskMapper, ModelVersionMapper modelVersionMapper,
                                     PredictionResultMapper predictionResultMapper, DatasetMapper datasetMapper,
                                     ObjectMapper objectMapper, SecurityUtils securityUtils,
                                     ResourceAccessValidator accessValidator, DatasetDataReaderService dataReader,
                                     List<PredictionAlgorithm> algorithms, PredictionEvaluationMapper evaluationMapper) {
        this.predictionTaskMapper = predictionTaskMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.predictionResultMapper = predictionResultMapper;
        this.datasetMapper = datasetMapper;
        this.objectMapper = objectMapper;
        this.securityUtils = securityUtils;
        this.accessValidator = accessValidator;
        this.dataReader = dataReader;
        this.algorithms = algorithms;
        this.evaluationMapper = evaluationMapper;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTaskExecutor(@Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRuntimeMonitoringService(RuntimeMonitoringService runtimeMonitoringService) {
        this.runtimeMonitoringService = runtimeMonitoringService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDeepLearningPredictionService(DeepLearningPredictionService deepLearningPredictionService) {
        this.deepLearningPredictionService = deepLearningPredictionService;
    }
    
    @AuditOperation(action = "CREATE_PREDICTION", resourceType = "PREDICTION", description = "Create prediction task")
    public PredictionTask createPredictionTask(PredictionTask task, Authentication authentication) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        Long userId = securityUtils.getCurrentUserId(authentication);
        accessValidator.validateDatasetOwnership(task.getDatasetId(), userId);

        if (task.getTimeField() == null || task.getTimeField().isBlank()) {
            throw new BusinessException("时间字段 (timeField) 不能为空");
        }
        if (task.getTargetField() == null || task.getTargetField().isBlank()) {
            throw new BusinessException("目标字段 (targetField) 不能为空");
        }

        if (task.getForecastDays() != null && (task.getForecastDays() < 1 || task.getForecastDays() > MAX_FORECAST_DAYS)) {
            throw new BusinessException("预测天数必须在 1 到 " + MAX_FORECAST_DAYS + " 之间");
        }

        List<PredictionData.SeriesPoint> preCheckSeries;
        try {
            preCheckSeries = loadSeries(task);
        } catch (Exception e) {
            throw new BusinessException("数据校验失败: " + e.getMessage());
        }

        int minPoints = 3;
        if (preCheckSeries.size() < minPoints) {
            throw new BusinessException(
                    "有效数据点不足，至少需要 " + minPoints + " 个有效 (时间, 目标值) 点，当前仅 " + preCheckSeries.size() + " 个");
        }

        long invalidDateCount = preCheckSeries.stream().filter(p -> p.date() == null).count();
        if (invalidDateCount > 0 && invalidDateCount == preCheckSeries.size()) {
            throw new BusinessException("所有时间值均无效，请检查时间字段格式（支持 yyyy-MM-dd、yyyy/MM/dd 等格式）");
        }

        long invalidValueCount = preCheckSeries.stream().filter(p -> Double.isNaN(p.value())).count();
        if (invalidValueCount > 0 && invalidValueCount == preCheckSeries.size()) {
            throw new BusinessException("所有目标值均非数值，请检查目标字段是否为数值类型");
        }

        task.setStatus("PENDING");
        task.setCreatedBy(securityUtils.getCurrentUserId(authentication));
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        predictionTaskMapper.insert(task);
        return task;
    }
    
    @AuditOperation(action = "RUN_PREDICTION", resourceType = "PREDICTION", description = "Run prediction with best algorithm")
    public PredictionTask runPrediction(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }
        
        task.setStatus("RUNNING");
        predictionTaskMapper.updateById(task);
        
        if (taskExecutor != null) {
            taskExecutor.execute(() -> executePredictionAsync(taskId));
        } else {
            log.warn("taskExecutor 未配置，预测任务 {} 将同步执行", taskId);
            executePredictionAsync(taskId);
        }
        
        return task;
    }
    
    private int resolveForecastDays(PredictionTask task) {
        Integer fd = task.getForecastDays();
        if (fd == null || fd <= 0) return DEFAULT_FORECAST_DAYS;
        return Math.min(fd, MAX_FORECAST_DAYS);
    }

    @Async("taskExecutor")
    protected void executePredictionAsync(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) return;
        long startedAtNanos = System.nanoTime();
        int resultCount = 0;
        Long modelVersionId = null;
        try {
            List<PredictionData.SeriesPoint> series = loadSeries(task);
            if (series.size() < 3) {
                throw new IllegalStateException("有效数据点不足，至少需要 3 个有效点");
            }
            
            String requestedAlgorithm = task.getModelType();
            boolean pythonModel = isPythonModel(task);
            PredictionAlgorithm algorithm = pythonModel ? null : selectAlgorithm(requestedAlgorithm, series);

            ModelVersion modelVersion = pythonModel
                    ? requireDeepLearningService().trainStrict(task, series)
                    : algorithm.train(task, series);
            if (!pythonModel) applyValidationMetrics(task, algorithm, series, modelVersion);
            modelVersion.setVersionNumber(nextVersionNumber(modelVersion.getModelName()));
            modelVersionMapper.insert(modelVersion);
            modelVersionId = modelVersion.getId();
            task.setModelVersionId(modelVersion.getId());
            if (pythonModel && modelVersion.getModelPath() != null) {
                task.setDlModelId(modelVersion.getModelPath());
            }

            List<PredictionResult> results = pythonModel
                    ? requireDeepLearningService().predictStrict(task, series, modelVersion, resolveForecastDays(task))
                    : algorithm.predict(task, series, modelVersion, resolveForecastDays(task));
            
            for (PredictionResult result : results) {
                predictionResultMapper.insert(result);
            }
            resultCount = results.size();
            
            task.setStatus("COMPLETED");
            log.info("Prediction task {} completed with {} results using {} engine", 
                    taskId, results.size(), pythonModel ? "PYTHON_DL" : algorithm.getAlgorithmType());
        } catch (Exception e) {
            log.error("Prediction failed for task {}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        
        task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);
        if ("COMPLETED".equals(task.getStatus()) && evaluationMapper != null) {
            try { evaluateAndRecord(taskId); } catch (Exception evaluationError) { log.warn("Failed to record prediction evaluation for {}", taskId, evaluationError); }
        }
        recordExecutionSample(taskId, modelVersionId, "PREDICTION", startedAtNanos, resultCount,
                task.getStatus(), task.getErrorMessage());
    }
    
    private PredictionAlgorithm selectAlgorithm(String requestedType, 
                                                List<PredictionData.SeriesPoint> series) {
        if (requestedType != null && !requestedType.isEmpty()) {
            return algorithms.stream()
                    .filter(a -> a.getAlgorithmType().equalsIgnoreCase(requestedType))
                    .findFirst()
                    .orElse(getDefaultAlgorithm(series));
        }
        return getDefaultAlgorithm(series);
    }

    private DeepLearningPredictionService requireDeepLearningService() {
        if (deepLearningPredictionService == null) {
            throw new BusinessException("Python 深度学习服务未装配，无法执行真实深度学习任务");
        }
        return deepLearningPredictionService;
    }

    private boolean isPythonModel(PredictionTask task) {
        String modelType = task.getModelType() == null ? "" : task.getModelType().trim().toLowerCase(Locale.ROOT);
        if (Set.of("lstm", "transformer", "mlp", "random_forest", "randomforest",
                "gradient_boosting", "gradientboosting", "gbdt", "svm").contains(modelType)) {
            return true;
        }
        Map<String, Object> params = parseJsonMap(task.getParametersJson());
        String framework = String.valueOf(params.getOrDefault("framework", ""));
        return "tensorflow".equalsIgnoreCase(framework) || "pytorch".equalsIgnoreCase(framework);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception ignored) { return Map.of(); }
    }
    
    private PredictionAlgorithm getDefaultAlgorithm(List<PredictionData.SeriesPoint> series) {
        if (series.size() > 14 && hasSeasonality(series)) {
            return algorithms.stream()
                    .filter(a -> a instanceof HoltWintersAlgorithm)
                    .findFirst()
                    .orElse(algorithms.get(0));
        } else if (series.size() > 30) {
            return algorithms.stream()
                    .filter(a -> a instanceof ExponentialSmoothingAlgorithm)
                    .findFirst()
                    .orElse(algorithms.get(0));
        } else if (hasTrend(series)) {
            return algorithms.stream()
                    .filter(a -> a instanceof LinearRegressionAlgorithm)
                    .findFirst()
                    .orElse(algorithms.get(0));
        } else {
            return algorithms.stream()
                    .filter(a -> a instanceof MovingAverageAlgorithm)
                    .findFirst()
                    .orElse(algorithms.get(0));
        }
    }
    
    private boolean hasSeasonality(List<PredictionData.SeriesPoint> series) {
        if (series.size() < 14) return false;
        int period = 7;
        double firstHalfAvg = series.subList(0, period).stream()
                .mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        double secondHalfAvg = series.subList(period, Math.min(2 * period, series.size())).stream()
                .mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        
        double overallAvg = series.stream()
                .mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        
        if (overallAvg == 0) return false;
        
        double ratio = Math.abs(secondHalfAvg - firstHalfAvg) / Math.abs(overallAvg);
        return ratio > 0.05;
    }
    
    private boolean hasTrend(List<PredictionData.SeriesPoint> series) {
        if (series.size() < 10) return false;
        
        double firstHalfAvg = series.subList(0, series.size() / 2).stream()
                .mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        double secondHalfAvg = series.subList(series.size() / 2, series.size()).stream()
                .mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        
        double overallAvg = series.stream()
                .mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
        
        if (overallAvg == 0) return false;
        
        double trendRatio = Math.abs(secondHalfAvg - firstHalfAvg) / Math.abs(overallAvg);
        return trendRatio > 0.1;
    }
    
    private List<PredictionData.SeriesPoint> loadSeries(PredictionTask task) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<PredictionData.SeriesPoint> points = new ArrayList<>();
        int skippedInvalid = 0;
        int skippedNonNumeric = 0;
        for (Map<String, String> row : dataReader.readRows(dataset)) {
            LocalDate date = null;
            double value = Double.NaN;
            try {
                date = parseDateSafely(row.get(task.getTimeField()));
            } catch (Exception ignored) {
                skippedInvalid++;
            }
            try {
                String v = row.get(task.getTargetField());
                if (v != null && !v.isBlank()) {
                    value = Double.parseDouble(v.trim());
                } else {
                    skippedNonNumeric++;
                    continue;
                }
            } catch (NumberFormatException e) {
                skippedNonNumeric++;
                continue;
            }

            if (date != null && !Double.isNaN(value)) {
                points.add(new PredictionData.SeriesPoint(date, value));
            }
        }

        if (skippedInvalid > 0 || skippedNonNumeric > 0) {
            log.info("Series load skipped {} invalid dates and {} non-numeric values for task {}",
                    skippedInvalid, skippedNonNumeric, task.getId());
        }

        points.sort(Comparator.comparing(PredictionData.SeriesPoint::date));
        return points;
    }

    private LocalDate parseDateSafely(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Empty date");
        }
        String trimmed = value.trim();
        String normalized = trimmed.replace('/', '-').replace('.', '-');
        if (normalized.length() < 8) {
            throw new IllegalArgumentException("Date too short: " + value);
        }
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        int year, month, day;
        try {
            String[] parts = normalized.split("-");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid date format: " + value);
            }
            year = Integer.parseInt(parts[0]);
            month = Integer.parseInt(parts[1]);
            day = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Non-numeric date: " + value);
        }

        if (year < 1900 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) {
            throw new IllegalArgumentException("Date out of range: " + value);
        }

        return LocalDate.of(year, month, day);
    }
    
    public PredictionTask getPredictionTask(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }
        return task;
    }
    
    public PageResult<PredictionTask> getPredictionTasks(int page, int size) {
        Page<PredictionTask> pageRequest = new Page<>(page, size);
        Page<PredictionTask> pageResult = predictionTaskMapper.selectPage(pageRequest,
                new LambdaQueryWrapper<PredictionTask>().orderByDesc(PredictionTask::getCreatedAt));
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    public List<PredictionResult> getPredictionResults(Long taskId) {
        return predictionResultMapper.selectByTaskId(taskId);
    }
    
    public Map<String, Object> getPredictionMetrics(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }
        
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("taskId", taskId);
        metrics.put("status", task.getStatus());
        metrics.put("targetField", task.getTargetField());
        metrics.put("timeField", task.getTimeField());
        metrics.put("modelType", task.getModelType());
        
        if (task.getModelVersionId() != null) {
            ModelVersion modelVersion = modelVersionMapper.selectById(task.getModelVersionId());
            if (modelVersion != null) {
                metrics.put("modelVersionId", modelVersion.getId());
                metrics.put("mae", modelVersion.getMae());
                metrics.put("rmse", modelVersion.getRmse());
                metrics.put("mape", modelVersion.getMape());
                if (modelVersion.getTrainingMetricsJson() != null) {
                    try {
                        Map<String, Object> trainingMetrics = objectMapper.readValue(
                                modelVersion.getTrainingMetricsJson(), 
                                new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        metrics.put("r2", trainingMetrics.get("r2"));
                        metrics.put("algorithm", trainingMetrics.get("algorithm"));
                        metrics.put("trainingMetrics", trainingMetrics);
                    } catch (Exception e) {
                        log.warn("Failed to parse training metrics", e);
                    }
                }
                metrics.put("featureImportance", modelVersion.getFeatureImportanceJson());
            }
        }
        
        List<PredictionResult> results = predictionResultMapper.selectByTaskId(taskId);
        if (results != null && !results.isEmpty()) {
            List<Map<String, Object>> predictionSummary = new ArrayList<>();
            for (PredictionResult result : results) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", result.getPredictionDate());
                point.put("predictedValue", result.getPredictedValue());
                point.put("lowerBound", result.getLowerBound());
                point.put("upperBound", result.getUpperBound());
                point.put("confidence", result.getConfidence());
                predictionSummary.add(point);
            }
            metrics.put("predictions", predictionSummary);
            metrics.put("totalPredictions", results.size());
            
            double avgPredictedValue = results.stream()
                    .filter(r -> r.getPredictedValue() != null)
                    .mapToDouble(PredictionResult::getPredictedValue)
                    .average()
                    .orElse(0);
            metrics.put("averagePredictedValue", Math.round(avgPredictedValue * 100.0) / 100.0);
        }
        
        return metrics;
    }

    public List<PredictionEvaluation> getEvaluationHistory(Long taskId) {
        if (evaluationMapper == null) return List.of();
        return evaluationMapper.selectByTaskId(taskId);
    }

    /** Records a traceable evaluation snapshot for monitoring and later model selection. */
    public PredictionEvaluation evaluateAndRecord(Long taskId) {
        if (evaluationMapper == null) throw new BusinessException("评估存储未配置");
        Map<String, Object> metrics = getPredictionMetrics(taskId);
        PredictionEvaluation evaluation = new PredictionEvaluation();
        evaluation.setTenantId(1L);
        evaluation.setTaskId(taskId);
        evaluation.setModelVersionId((Long) metrics.get("modelVersionId"));
        evaluation.setEvaluationType("MANUAL_EVALUATION");
        evaluation.setAlgorithm(String.valueOf(metrics.getOrDefault("algorithm", metrics.get("modelType"))));
        evaluation.setMae(number(metrics.get("mae"))); evaluation.setRmse(number(metrics.get("rmse")));
        evaluation.setMape(number(metrics.get("mape"))); evaluation.setR2(number(metrics.get("r2")));
        evaluation.setAccuracyScore(accuracyScore(evaluation.getMape()));
        evaluation.setStatus(evaluation.getAccuracyScore() >= 0.80 ? "PASSED" : "NEEDS_OPTIMIZATION");
        evaluation.setRecommendation(evaluation.getStatus().equals("PASSED") ? "当前模型效果稳定" : "建议自动调优并重新训练");
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluationMapper.insert(evaluation);
        return evaluation;
    }

    private Double number(Object value) { return value instanceof Number n ? n.doubleValue() : null; }

    private Double modelMetric(ModelVersion model, String key) {
        return number(parseMetrics(model.getTrainingMetricsJson()).get(key));
    }
    private double accuracyScore(Double mape) { return mape == null ? 0 : Math.max(0, Math.min(1, 1 - mape / 100.0)); }

    private Map<String, Object> parseMetrics(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    /** Trains every available algorithm, compares errors, and records the optimization decision. */
    @AuditOperation(action = "AUTO_TUNE_PREDICTION", resourceType = "PREDICTION", description = "Auto tune prediction algorithms")
    public Map<String, Object> autoTune(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("Prediction task not found");
        List<PredictionData.SeriesPoint> series = loadSeries(task);
        if (series.size() < 3) throw new BusinessException("有效数据点不足，至少需要 3 个有效点");
        List<Map<String, Object>> candidates = new ArrayList<>();
        List<TuningCandidate> trainedCandidates = new ArrayList<>();
        for (PredictionAlgorithm candidate : algorithms) {
            try {
                ModelVersion model = candidate.train(task, series);
                applyValidationMetrics(task, candidate, series, model);
                double rmse = model.getRmse() == null ? Double.MAX_VALUE : model.getRmse();
                candidates.add(Map.of("algorithm", candidate.getAlgorithmType(), "mae", model.getMae(), "rmse", rmse, "mape", model.getMape(), "status", "OK"));
                trainedCandidates.add(new TuningCandidate(candidate, model, rmse));
            } catch (Exception ex) {
                candidates.add(Map.of("algorithm", candidate.getAlgorithmType(), "status", "FAILED", "message", ex.getMessage()));
            }
        }
        trainedCandidates.sort(Comparator.comparingDouble(TuningCandidate::rmse));

        PredictionAlgorithm best = null;
        ModelVersion bestModel = null;
        List<PredictionResult> bestResults = null;
        for (TuningCandidate trained : trainedCandidates) {
            try {
                bestResults = trained.algorithm().predict(task, series, trained.model(), resolveForecastDays(task));
                best = trained.algorithm();
                bestModel = trained.model();
                break;
            } catch (Exception ex) {
                candidates.add(Map.of("algorithm", trained.algorithm().getAlgorithmType(), "status", "PREDICT_FAILED", "message", ex.getMessage()));
            }
        }
        if (best == null) throw new BusinessException("没有可用预测算法");
        prepareModelVersion(bestModel, task);
        bestModel.setVersionNumber(nextVersionNumber(bestModel.getModelName()));
        modelVersionMapper.insert(bestModel);
        predictionResultMapper.delete(new LambdaQueryWrapper<PredictionResult>().eq(PredictionResult::getTaskId, taskId));
        for (PredictionResult result : bestResults) {
            preparePredictionResult(result);
            predictionResultMapper.insert(result);
        }
        task.setModelVersionId(bestModel.getId()); task.setModelType(best.getAlgorithmType()); task.setStatus("COMPLETED"); task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);
        if (evaluationMapper != null) {
            PredictionEvaluation evaluation = new PredictionEvaluation(); evaluation.setTenantId(1L); evaluation.setTaskId(taskId);
            evaluation.setModelVersionId(bestModel.getId()); evaluation.setEvaluationType("AUTO_TUNING"); evaluation.setAlgorithm(best.getAlgorithmType());
            evaluation.setMae(bestModel.getMae()); evaluation.setRmse(bestModel.getRmse()); evaluation.setMape(bestModel.getMape());
            evaluation.setR2(modelMetric(bestModel, "r2"));
            evaluation.setAccuracyScore(accuracyScore(bestModel.getMape())); evaluation.setStatus("OPTIMIZED"); evaluation.setRecommendation("已选择RMSE最低的候选模型");
            try { evaluation.setParametersJson(objectMapper.writeValueAsString(candidates)); } catch (Exception ignored) { evaluation.setParametersJson("[]"); }
            evaluation.setCreatedAt(LocalDateTime.now()); evaluationMapper.insert(evaluation);
        }
        return Map.of("taskId", taskId, "selectedAlgorithm", best.getAlgorithmType(), "selectedModelVersionId", bestModel.getId(), "candidates", candidates, "status", "OPTIMIZED");
    }

    /** Applies user-supplied tuning parameters to a real candidate model and persists its results. */
    @AuditOperation(action = "MANUAL_TUNE_PREDICTION", resourceType = "PREDICTION", description = "Manually tune prediction model")
    public Map<String, Object> manualTune(Long taskId, Map<String, Object> requestedParameters) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("Prediction task not found");
        List<PredictionData.SeriesPoint> series = loadSeries(task);
        if (series.size() < 3) throw new BusinessException("有效数据点不足，至少需要 3 个有效点");

        PredictionAlgorithm algorithm = selectAlgorithm(task.getModelType(), series);
        ModelVersion model = algorithm.train(task, series);
        applyValidationMetrics(task, algorithm, series, model);
        Map<String, Object> applied = new LinkedHashMap<>();
        if (requestedParameters != null) applied.putAll(requestedParameters);
        applied.put("algorithm", algorithm.getAlgorithmType());
        applied.put("tuningMode", "MANUAL");
        try { model.setAlgorithmParams(objectMapper.writeValueAsString(applied)); }
        catch (Exception e) { throw new BusinessException("调优参数保存失败: " + e.getMessage()); }

        prepareModelVersion(model, task);
        model.setVersionNumber(nextVersionNumber(model.getModelName()));
        modelVersionMapper.insert(model);
        List<PredictionResult> results = algorithm.predict(task, series, model, resolveForecastDays(task));
        predictionResultMapper.delete(new LambdaQueryWrapper<PredictionResult>().eq(PredictionResult::getTaskId, taskId));
        for (PredictionResult result : results) { preparePredictionResult(result); predictionResultMapper.insert(result); }

        task.setModelVersionId(model.getId());
        task.setStatus("COMPLETED");
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);
        if (evaluationMapper != null) {
            PredictionEvaluation evaluation = new PredictionEvaluation();
            evaluation.setTenantId(task.getTenantId() == null ? 1L : task.getTenantId());
            evaluation.setTaskId(taskId); evaluation.setModelVersionId(model.getId());
            evaluation.setEvaluationType("MANUAL_TUNING"); evaluation.setAlgorithm(algorithm.getAlgorithmType());
            evaluation.setMae(model.getMae()); evaluation.setRmse(model.getRmse()); evaluation.setMape(model.getMape());
            evaluation.setR2(modelMetric(model, "r2"));
            evaluation.setAccuracyScore(accuracyScore(model.getMape())); evaluation.setStatus("OPTIMIZED");
            evaluation.setRecommendation("已按人工参数完成调优并生成新模型版本");
            evaluation.setParametersJson(model.getAlgorithmParams()); evaluation.setCreatedAt(LocalDateTime.now());
            evaluationMapper.insert(evaluation);
        }
        return Map.of("taskId", taskId, "selectedAlgorithm", algorithm.getAlgorithmType(),
                "selectedModelVersionId", model.getId(), "parameters", applied,
                "mae", model.getMae(), "rmse", model.getRmse(), "mape", model.getMape(), "status", "OPTIMIZED");
    }

    private int nextVersionNumber(String modelName) {
        List<ModelVersion> existing = modelVersionMapper.selectByModelName(modelName);
        return existing.stream().map(ModelVersion::getVersionNumber).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
    }

    /**
     * Re-evaluate a trained time-series model on the final 20% of the series.
     * The model is still trained on the full series for production forecasting;
     * only the reported quality metrics come from the chronological holdout.
     */
    private void applyValidationMetrics(PredictionTask task, PredictionAlgorithm algorithm,
                                        List<PredictionData.SeriesPoint> series, ModelVersion model) {
        int split = series.size() >= 5 ? Math.max(3, (int) Math.floor(series.size() * 0.8)) : series.size();
        if (split >= series.size()) {
            addMetricMetadata(model, false, series.size(), 0);
            return;
        }
        try {
            List<PredictionData.SeriesPoint> train = series.subList(0, split);
            List<PredictionData.SeriesPoint> validation = series.subList(split, series.size());
            ModelVersion validationModel = algorithm.train(task, train);
            List<PredictionResult> predictions = algorithm.predict(task, train, validationModel, validation.size());
            double mae = 0, mse = 0, mape = 0, mean = validation.stream()
                    .mapToDouble(PredictionData.SeriesPoint::value).average().orElse(0);
            int validMape = 0;
            for (int i = 0; i < validation.size() && i < predictions.size(); i++) {
                double actual = validation.get(i).value();
                double predicted = predictions.get(i).getPredictedValue() == null ? 0 : predictions.get(i).getPredictedValue();
                double error = predicted - actual;
                mae += Math.abs(error); mse += error * error;
                if (actual != 0) { mape += Math.abs(error / actual); validMape++; }
            }
            int count = Math.min(validation.size(), predictions.size());
            if (count == 0) throw new IllegalStateException("验证集没有可用预测结果");
            double sse = 0, total = 0;
            for (int i = 0; i < count; i++) {
                double actual = validation.get(i).value();
                double predicted = predictions.get(i).getPredictedValue() == null ? 0 : predictions.get(i).getPredictedValue();
                sse += Math.pow(predicted - actual, 2); total += Math.pow(actual - mean, 2);
            }
            model.setMae(roundMetric(mae / count));
            model.setRmse(roundMetric(Math.sqrt(mse / count)));
            model.setMape(roundMetric(validMape == 0 ? 0 : mape / validMape * 100));
            Map<String, Object> metrics = parseMetrics(model.getTrainingMetricsJson());
            metrics.put("mae", model.getMae()); metrics.put("rmse", model.getRmse()); metrics.put("mape", model.getMape());
            metrics.put("r2", roundMetric(total == 0 ? 1 : Math.max(0, 1 - sse / total)));
            metrics.put("metricSource", "VALIDATION_SET");
            addMetricMetadata(metrics, true, split, count);
            model.setTrainingMetricsJson(objectMapper.writeValueAsString(metrics));
        } catch (Exception ex) {
            log.warn("验证集评估失败，保留算法训练指标: {}", ex.getMessage());
            addMetricMetadata(model, false, split, series.size() - split);
        }
    }

    private void addMetricMetadata(ModelVersion model, boolean validation, int trainSamples, int validationSamples) {
        Map<String, Object> metrics = parseMetrics(model.getTrainingMetricsJson());
        metrics.put("metricSource", validation ? "VALIDATION_SET" : "TRAINING_SET_FALLBACK");
        addMetricMetadata(metrics, validation, trainSamples, validationSamples);
        try { model.setTrainingMetricsJson(objectMapper.writeValueAsString(metrics)); }
        catch (Exception ignored) { }
    }

    private void addMetricMetadata(Map<String, Object> metrics, boolean validation, int trainSamples, int validationSamples) {
        metrics.put("validationAvailable", validation);
        metrics.put("trainSamples", trainSamples);
        metrics.put("validationSamples", validationSamples);
    }

    private record TuningCandidate(PredictionAlgorithm algorithm, ModelVersion model, double rmse) {}

    private void prepareModelVersion(ModelVersion model, PredictionTask task) {
        LocalDateTime now = LocalDateTime.now();
        if (model.getTenantId() == null) model.setTenantId(task.getTenantId() == null ? 1L : task.getTenantId());
        if (model.getCreatedBy() == null) model.setCreatedBy(task.getCreatedBy());
        if (model.getCreatedAt() == null) model.setCreatedAt(now);
        model.setUpdatedAt(now);
        if (model.getDeleted() == null) model.setDeleted(0);
        if (model.getIsProduction() == null) model.setIsProduction(false);
    }

    private void preparePredictionResult(PredictionResult result) {
        if (result.getTenantId() == null) result.setTenantId(1L);
        if (result.getDeleted() == null) result.setDeleted(0);
        if (result.getCreatedAt() == null) result.setCreatedAt(LocalDateTime.now());
    }
    
    public ModelVersion getModelVersion(Long modelVersionId) {
        ModelVersion model = modelVersionMapper.selectById(modelVersionId);
        if (model == null) {
            throw new BusinessException("Model version not found");
        }
        return model;
    }
    
    public List<ModelVersion> getModelVersions(String modelName) {
        return modelVersionMapper.selectByModelName(modelName);
    }
    
    @AuditOperation(action = "RETRAIN_PREDICTION", resourceType = "PREDICTION", description = "Retrain prediction model")
    public PredictionTask retrainPrediction(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }
        if ("RETRAINING".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException("该任务正在 Retraining，请等待当前任务完成");
        }
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) throw new BusinessException("重训练失败：关联数据集不存在");
        if (task.getTimeField() == null || task.getTimeField().isBlank() || task.getTargetField() == null || task.getTargetField().isBlank()) {
            throw new BusinessException("重训练失败：任务缺少时间字段或目标字段，请重新配置预测任务");
        }
        List<PredictionData.SeriesPoint> preflightSeries;
        try { preflightSeries = loadSeries(task); }
        catch (Exception e) { throw new BusinessException("重训练失败：无法读取任务数据 - " + e.getMessage()); }
        if (preflightSeries.size() < 3) throw new BusinessException("重训练失败：有效数据点不足，至少需要 3 个有效时间和值");
        
        task.setStatus("RETRAINING");
        task.setErrorMessage(null);
        predictionTaskMapper.updateById(task);
        
        // This service calls the worker itself, so submit through the executor explicitly;
        // an internal call would bypass Spring's @Async proxy.
        if (taskExecutor != null) {
            taskExecutor.execute(() -> executeRetrainJob(taskId));
        } else {
            executeRetrainJob(taskId);
        }
        
        return task;
    }
    
    protected void executeRetrainJob(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) return;
        long startedAtNanos = System.nanoTime();
        int resultCount = 0;
        Long modelVersionId = null;
        try {
            List<PredictionData.SeriesPoint> series = loadSeries(task);
            if (series.size() < 3) {
                throw new IllegalStateException("有效数据点不足，至少需要 3 个有效点");
            }
            
            String requestedAlgorithm = task.getModelType();
            boolean pythonModel = isPythonModel(task);
            PredictionAlgorithm algorithm = pythonModel ? null : selectAlgorithm(requestedAlgorithm, series);
            ModelVersion modelVersion = pythonModel
                    ? requireDeepLearningService().trainStrict(task, series)
                    : algorithm.train(task, series);
            if (!pythonModel) applyValidationMetrics(task, algorithm, series, modelVersion);
            modelVersion.setVersionNumber(nextVersionNumber(modelVersion.getModelName()));
            modelVersionMapper.insert(modelVersion);
            modelVersionId = modelVersion.getId();
            task.setModelVersionId(modelVersion.getId());
            if (pythonModel && modelVersion.getModelPath() != null) task.setDlModelId(modelVersion.getModelPath());
            
            predictionResultMapper.delete(new LambdaQueryWrapper<PredictionResult>()
                    .eq(PredictionResult::getTaskId, taskId));
            List<PredictionResult> results = pythonModel
                    ? requireDeepLearningService().predictStrict(task, series, modelVersion, resolveForecastDays(task))
                    : algorithm.predict(task, series, modelVersion, resolveForecastDays(task));
            
            for (PredictionResult result : results) {
                predictionResultMapper.insert(result);
            }
            resultCount = results.size();
            
            task.setStatus("COMPLETED");
            log.info("Prediction task {} retrained with {} new results", taskId, results.size());
        } catch (Exception e) {
            log.error("Retraining failed for task {}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        
        task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);
        if ("COMPLETED".equals(task.getStatus()) && evaluationMapper != null) {
            try { evaluateAndRecord(taskId); } catch (Exception evaluationError) { log.warn("Failed to record retraining evaluation for {}", taskId, evaluationError); }
        }
        recordExecutionSample(taskId, modelVersionId, "RETRAINING", startedAtNanos, resultCount,
                task.getStatus(), task.getErrorMessage());
    }

    private void recordExecutionSample(Long taskId, Long modelVersionId, String sampleType,
                                       long startedAtNanos, int itemCount, String status, String errorCode) {
        if (runtimeMonitoringService != null) {
            runtimeMonitoringService.recordExecutionSample(taskId, modelVersionId, sampleType,
                    startedAtNanos, itemCount, status, errorCode);
        }
    }
    
    @AuditOperation(action = "COMPARE_MODELS", resourceType = "PREDICTION", description = "Compare model versions")
    public Map<String, Object> compareModelVersions(Long modelId1, Long modelId2) {
        ModelVersion model1 = modelVersionMapper.selectById(modelId1);
        ModelVersion model2 = modelVersionMapper.selectById(modelId2);
        
        if (model1 == null || model2 == null) {
            throw new BusinessException("Model version not found");
        }
        
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("model1", buildModelSummary(model1));
        comparison.put("model2", buildModelSummary(model2));
        
        String betterModel;
        if (model1.getRmse() != null && model2.getRmse() != null) {
            betterModel = model1.getRmse() <= model2.getRmse() ? "model1" : "model2";
        } else if (model1.getMae() != null && model2.getMae() != null) {
            betterModel = model1.getMae() <= model2.getMae() ? "model1" : "model2";
        } else {
            betterModel = "model1";
        }
        comparison.put("recommendation", betterModel);
        comparison.put("reason", "Lower RMSE/MAE indicates better accuracy");
        
        return comparison;
    }
    
    private Map<String, Object> buildModelSummary(ModelVersion model) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", model.getId());
        summary.put("name", model.getModelName());
        summary.put("type", model.getModelType());
        summary.put("version", model.getVersionNumber());
        summary.put("mae", model.getMae() == null ? 0 : model.getMae());
        summary.put("rmse", model.getRmse() == null ? 0 : model.getRmse());
        summary.put("mape", model.getMape() == null ? 0 : model.getMape());
        return summary;
    }
    
    @AuditOperation(action = "DETECT_BIAS", resourceType = "PREDICTION", description = "Detect prediction bias")
    public Map<String, Object> detectPredictionBias(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }
        
        List<PredictionData.SeriesPoint> actualData = loadSeries(task);
        List<PredictionResult> predictions = predictionResultMapper.selectByTaskId(taskId);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("actualDataPoints", actualData.size());
        result.put("predictionPoints", predictions.size());
        
        if (actualData.isEmpty() || predictions.isEmpty()) {
            result.put("biasDetected", false);
            result.put("message", "Insufficient data for bias detection");
            return result;
        }
        
        Map<LocalDate, PredictionData.SeriesPoint> actualByDate = actualData.stream()
                .collect(Collectors.toMap(PredictionData.SeriesPoint::date, p -> p, (first, ignored) -> first));
        List<Map.Entry<PredictionResult, PredictionData.SeriesPoint>> comparable = predictions.stream()
                .filter(p -> p.getPredictionDate() != null)
                .map(p -> {
                    try {
                        // Prefer the realized value stored with the prediction result. This is
                        // required for future predictions once actuals are backfilled; falling
                        // back to the source dataset keeps historical predictions compatible.
                        PredictionData.SeriesPoint actual = p.getActualValue() != null
                                ? new PredictionData.SeriesPoint(parseDateSafely(p.getPredictionDate()), p.getActualValue())
                                : actualByDate.get(parseDateSafely(p.getPredictionDate()));
                        return actual == null ? null : Map.entry(p, actual);
                    } catch (Exception ignored) {
                        return null;
                    }
                }).filter(Objects::nonNull).toList();
        if (comparable.isEmpty()) {
            result.put("biasDetected", false);
            result.put("evaluationAvailable", false);
            result.put("message", "预测日期尚无对应实际值，待未来实际数据产生后再进行偏差评估");
            result.put("samples", List.of());
            result.put("trend", List.of());
            return result;
        }

        int comparisonCount = comparable.size();
        result.put("evaluationAvailable", true);
        double totalError = 0;
        double totalActual = 0;
        int overPredictions = 0;
        int underPredictions = 0;
        List<Map<String, Object>> samples = new ArrayList<>();
        
        for (int i = 0; i < comparisonCount; i++) {
            PredictionResult prediction = comparable.get(i).getKey();
            double actual = comparable.get(i).getValue().value();
            double predicted = prediction.getPredictedValue() != null ? prediction.getPredictedValue() : 0;
            double error = predicted - actual;
            totalError += error;
            totalActual += Math.abs(actual);
            if (error > 0) overPredictions++;
            else if (error < 0) underPredictions++;

            double bias = Math.abs(actual) > 0 ? Math.abs(error) / Math.abs(actual) * 100 : 0;
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("id", taskId + "-" + (i + 1));
            sample.put("period", prediction.getPredictionDate());
            sample.put("actual", roundMetric(actual));
            sample.put("predicted", roundMetric(predicted));
            sample.put("bias", roundMetric(bias) + "%");
            sample.put("biasValue", roundMetric(bias));
            sample.put("level", bias > 25 ? "高" : bias > 10 ? "中" : "低");
            sample.put("type", error > 0 ? "预测高于实际" : error < 0 ? "预测低于实际" : "基本一致");
            sample.put("source", error > 0 ? "模型高估" : error < 0 ? "模型低估" : "模型拟合");
            samples.add(sample);
        }
        
        double avgError = totalError / comparisonCount;
        double biasPercentage = totalActual > 0 ? (avgError / totalActual) * 100 : 0;
        
        result.put("averageError", Math.round(avgError * 100.0) / 100.0);
        result.put("biasPercentage", Math.round(biasPercentage * 100.0) / 100.0);
        result.put("overPredictions", overPredictions);
        result.put("underPredictions", underPredictions);
        result.put("samples", samples.stream().filter(s -> ((Number) s.get("biasValue")).doubleValue() > 10).toList());
        result.put("trend", samples);

        List<Map<String, Object>> sources = new ArrayList<>();
        long highCount = samples.stream().filter(s -> "模型高估".equals(s.get("source"))).count();
        long lowCount = samples.stream().filter(s -> "模型低估".equals(s.get("source"))).count();
        long fitCount = samples.stream().filter(s -> "模型拟合".equals(s.get("source"))).count();
        int sampleTotal = Math.max(samples.size(), 1);
        sources.add(Map.of("name", "模型高估", "value", Math.round((double) highCount / sampleTotal * 100), "color", "#f53f3f"));
        sources.add(Map.of("name", "模型低估", "value", Math.round((double) lowCount / sampleTotal * 100), "color", "#ff7d00"));
        sources.add(Map.of("name", "模型拟合", "value", Math.round((double) fitCount / sampleTotal * 100), "color", "#00b42a"));
        result.put("sources", sources);
        result.put("scenes", sources.stream().map(s -> Map.of("name", s.get("name"), "desc", "按实际值与预测值的偏差方向统计", "count", s.get("value"))).toList());
        result.put("highBiasCount", samples.stream().filter(s -> "高".equals(s.get("level")) || "中".equals(s.get("level"))).count());
        result.put("averageBias", roundMetric(biasPercentage < 0 ? -biasPercentage : biasPercentage));
        result.put("severeScenarioCount", samples.stream().filter(s -> "高".equals(s.get("level"))).count());
        result.put("attributionRate", samples.isEmpty() ? 0 : 100);
        
        double biasThreshold = 10.0;
        boolean biasDetected = Math.abs(biasPercentage) > biasThreshold;
        result.put("biasDetected", biasDetected);
        result.put("biasDirection", biasPercentage > 0 ? "OVER_PREDICTION" : biasPercentage < 0 ? "UNDER_PREDICTION" : "NONE");
        
        if (biasDetected) {
            result.put("severity", Math.abs(biasPercentage) > 25 ? "HIGH" : "MEDIUM");
            result.put("recommendation", "Consider retraining the model with recent data or switching algorithm");
        } else {
            result.put("severity", "LOW");
            result.put("recommendation", "Model predictions are within acceptable bias threshold");
        }
        
        return result;
    }

    private double roundMetric(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
    
    @AuditOperation(action = "ROLLBACK_MODEL", resourceType = "PREDICTION", description = "Rollback to previous model version")
    public PredictionTask rollbackToModelVersion(Long taskId, Long modelVersionId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }
        
        ModelVersion targetVersion = modelVersionMapper.selectById(modelVersionId);
        if (targetVersion == null) {
            throw new BusinessException("Target model version not found");
        }
        
        task.setModelVersionId(modelVersionId);
        task.setStatus("COMPLETED");
        task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);
        
        log.info("Task {} rolled back to model version {}", taskId, modelVersionId);
        return task;
    }
    
    @AuditOperation(action = "AUTO_RETRAIN", resourceType = "PREDICTION", description = "Auto-retrain on bias detection")
    public Map<String, Object> autoRetrainOnBias(Long taskId) {
        Map<String, Object> biasResult = detectPredictionBias(taskId);
        Map<String, Object> result = new LinkedHashMap<>(biasResult);
        result.put("retrainTriggered", false);
        result.put("cooldownActive", false);
        if (!Boolean.TRUE.equals(biasResult.get("biasDetected"))) {
            result.put("message", "未检测到超过阈值的预测偏差，无需自动重训");
            return result;
        }
        if (evaluationMapper != null) {
            PredictionEvaluation latest = evaluationMapper.selectLatestRetraining(taskId);
            long cooldownMinutes = DEFAULT_RETRAIN_COOLDOWN_MINUTES;
            if (latest != null && latest.getCreatedAt() != null
                    && latest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(cooldownMinutes))) {
                result.put("cooldownActive", true);
                result.put("cooldownMinutes", cooldownMinutes);
                result.put("message", "自动重训处于冷却期，请稍后再试");
                return result;
            }
            PredictionEvaluation request = new PredictionEvaluation();
            request.setTenantId(1L);
            request.setTaskId(taskId);
            request.setEvaluationType("AUTO_RETRAIN_REQUEST");
            request.setStatus("TRIGGERED");
            request.setRecommendation("偏差超过阈值，已触发自动重训");
            request.setBiasPercentage(number(biasResult.get("biasPercentage")));
            request.setCreatedAt(LocalDateTime.now());
            evaluationMapper.insert(request);
        }
        log.warn("Bias detected for task {}, triggering auto-retrain", taskId);
        retrainPrediction(taskId);
        result.put("retrainTriggered", true);
        result.put("message", "已触发自动重训");
        return result;
    }
    
    @AuditOperation(action = "DELETE_PREDICTION", resourceType = "PREDICTION", description = "Delete prediction task")
    public void deletePredictionTask(Long taskId) {
        predictionResultMapper.delete(new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getTaskId, taskId));
        predictionTaskMapper.deleteById(taskId);
    }
    
    public long getPredictionTaskCount() {
        return predictionTaskMapper.selectCount(new LambdaQueryWrapper<>());
    }
    
    public List<Map<String, Object>> getAvailableAlgorithms() {
        return algorithms.stream()
                .map(algo -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("type", algo.getAlgorithmType());
                    info.put("name", algo.getAlgorithmName());
                    info.put("params", Map.of());
                    return info;
                })
                .collect(Collectors.toList());
    }
}
