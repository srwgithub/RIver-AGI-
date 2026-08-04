package com.river.agi.prediction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.common.annotation.AuditOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionTaskMapper predictionTaskMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final DatasetMapper datasetMapper;
    private final ObjectMapper objectMapper;
    private final SecurityUtils securityUtils;
    private final ResourceAccessValidator accessValidator;
    private final DatasetDataReaderService dataReader;
    private final RoleMapper roleMapper;
    private Executor taskExecutor;
    private RuntimeMonitoringService runtimeMonitoringService;

    private static final int DEFAULT_FORECAST_DAYS = 30;
    private static final int MAX_FORECAST_DAYS = 365;

    @Autowired(required = false)
    public void setTaskExecutor(@Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Autowired(required = false)
    public void setRuntimeMonitoringService(RuntimeMonitoringService runtimeMonitoringService) {
        this.runtimeMonitoringService = runtimeMonitoringService;
    }

    @AuditOperation(action = "CREATE_PREDICTION", resourceType = "PREDICTION", description = "Create prediction task")
    public PredictionTask createPredictionTask(PredictionTask task, Authentication authentication) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        validateDatasetParsed(dataset);

        Long userId = securityUtils.getCurrentUserId(authentication);
        accessValidator.validateDatasetOwnership(task.getDatasetId(), userId);

        if (task.getForecastDays() != null && (task.getForecastDays() < 1 || task.getForecastDays() > MAX_FORECAST_DAYS)) {
            throw new BusinessException("预测天数必须在 1 到 " + MAX_FORECAST_DAYS + " 之间");
        }

        task.setStatus("PENDING");
        task.setCreatedBy(userId);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        predictionTaskMapper.insert(task);
        return task;
    }

    @AuditOperation(action = "RUN_PREDICTION", resourceType = "PREDICTION", description = "Run prediction task")
    public PredictionTask runPrediction(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validatePredictionAccess(taskId, userId);
        }

        task.setStatus("RUNNING");
        predictionTaskMapper.updateById(task);

        // Execute asynchronously to avoid blocking the API
        if (taskExecutor != null) {
            taskExecutor.execute(() -> executePredictionAsync(taskId));
        } else {
            log.warn("taskExecutor 未配置，预测任务 {} 将同步执行", taskId);
            executePredictionAsync(taskId);
        }

        return task;
    }

    @Async("taskExecutor")
    protected void executePredictionAsync(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) return;
        long startedAtNanos = System.nanoTime();
        int resultCount = 0;
        Long modelVersionId = null;
        try {
            ModelVersion modelVersion = trainModel(task);
            modelVersionId = modelVersion.getId();
            task.setModelVersionId(modelVersion.getId());

            List<PredictionResult> results = generatePredictions(task, modelVersion);
            resultCount = results.size();

            task.setStatus("COMPLETED");
            log.info("Prediction task {} completed with {} results", taskId, results.size());
        } catch (Exception e) {
            log.error("Prediction failed for task {}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);
        recordExecutionSample(taskId, modelVersionId, "PREDICTION", startedAtNanos, resultCount,
                task.getStatus(), task.getErrorMessage());
    }

    private ModelVersion trainModel(PredictionTask task) throws Exception {
        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("demand_prediction_" + task.getDatasetId());
        String modelType = task.getModelType() != null ? task.getModelType() : "LINEAR_REGRESSION";
        modelVersion.setModelType(modelType);
        modelVersion.setVersionNumber(nextVersionNumber(modelVersion.getModelName()));
        modelVersion.setAlgorithmType(modelType);
        modelVersion.setTaskType("TIME_SERIES");
        modelVersion.setStatus("ACTIVE");

        List<SeriesPoint> series = loadSeries(task);
        if (series.size() < 3) throw new BusinessException("At least 3 valid time-series rows are required");
        modelVersion.setTrainingSamples((long) series.size());
        modelVersion.setPredictionTaskId(task.getId());

        double[] regression = regression(series);
        double[] expSmoothing = doubleExponentialSmoothing(series);
        double[] movingAvg = movingAverage(series);
        double[] holtWinters = holtWintersForecast(series);

        String actualModelUsed = modelType;
        if ("AUTO".equalsIgnoreCase(modelType)) {
            // Compare all 4 algorithms and pick the one with lowest MAPE
            double linError = validationMAPE(series, "LINEAR_REGRESSION");
            double expError = validationMAPE(series, "EXPONENTIAL_SMOOTHING");
            double maError = validationMAPE(series, "MOVING_AVERAGE");
            double hwError = validationMAPE(series, "HOLT_WINTERS");

            double minError = Math.min(Math.min(linError, expError), Math.min(maError, hwError));
            if (minError == linError) actualModelUsed = "LINEAR_REGRESSION";
            else if (minError == expError) actualModelUsed = "EXPONENTIAL_SMOOTHING";
            else if (minError == maError) actualModelUsed = "MOVING_AVERAGE";
            else actualModelUsed = "HOLT_WINTERS";
        }

        double[] finalModel = switch (actualModelUsed) {
            case "EXPONENTIAL_SMOOTHING" -> expSmoothing;
            case "MOVING_AVERAGE" -> movingAvg;
            case "HOLT_WINTERS" -> holtWinters;
            default -> regression;
        };

        HoldoutMetrics holdout = evaluateHoldout(series, actualModelUsed, finalModel);
        modelVersion.setMae(round(holdout.mae));
        modelVersion.setRmse(round(holdout.rmse));
        modelVersion.setMape(round(holdout.mape));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("mae", modelVersion.getMae());
        metrics.put("rmse", modelVersion.getRmse());
        metrics.put("mape", modelVersion.getMape());
        metrics.put("r2", round(holdout.r2));
        metrics.put("modelType", actualModelUsed);
        metrics.put("seriesLength", series.size());
        metrics.put("metricSource", holdout.validationAvailable ? "VALIDATION_SET" : "TRAINING_SET_FALLBACK");
        metrics.put("trainSamples", holdout.trainSamples);
        metrics.put("validationSamples", holdout.validationSamples);
        metrics.put("alpha", "EXPONENTIAL_SMOOTHING".equals(actualModelUsed) ? 0.3 : null);
        metrics.put("beta", "EXPONENTIAL_SMOOTHING".equals(actualModelUsed) ? 0.1 : null);
        modelVersion.setTrainingMetricsJson(objectMapper.writeValueAsString(metrics));

        Map<String, Double> featureImportance = new LinkedHashMap<>();
        featureImportance.put(task.getTimeField(), 1.0);
        modelVersion.setFeatureImportanceJson(objectMapper.writeValueAsString(featureImportance));

        modelVersion.setCreatedAt(LocalDateTime.now());

        modelVersionMapper.insert(modelVersion);
        return modelVersion;
    }

    private double computeMAPE(List<SeriesPoint> series, double[] model) {
        double mape = 0;
        for (int i = 0; i < series.size(); i++) {
            double actual = series.get(i).value;
            double predicted = model[0] + model[1] * i;
            if (actual != 0) mape += Math.abs((predicted - actual) / actual);
        }
        return (mape / series.size()) * 100;
    }

    private double validationMAPE(List<SeriesPoint> series, String modelType) {
        return evaluateHoldout(series, modelType, null).mape;
    }

    private HoldoutMetrics evaluateHoldout(List<SeriesPoint> series, String modelType, double[] fullModel) {
        int split = series.size() >= 5 ? Math.max(3, (int) Math.floor(series.size() * 0.8)) : series.size();
        if (split >= series.size()) {
            double[] fallbackModel = modelType == null ? fullModel : modelForType(series, modelType);
            return trainingMetrics(series, fallbackModel);
        }
        List<SeriesPoint> train = series.subList(0, split);
        double[] model = modelType == null ? fullModel : modelForType(train, modelType);
        return score(series, model, split, series.size() - split, true);
    }

    private HoldoutMetrics trainingMetrics(List<SeriesPoint> series, double[] model) {
        return score(series, model, 0, series.size(), false);
    }

    private HoldoutMetrics score(List<SeriesPoint> series, double[] model, int start, int count, boolean validation) {
        double mae = 0, mse = 0, mape = 0, actualSum = 0, actualSquared = 0;
        int valid = 0;
        double mean = 0;
        for (int i = start; i < start + count; i++) mean += series.get(i).value;
        mean /= Math.max(1, count);
        for (int i = start; i < start + count; i++) {
            double actual = series.get(i).value;
            double predicted = model[0] + model[1] * i;
            double error = predicted - actual;
            mae += Math.abs(error); mse += error * error;
            actualSum += Math.abs(actual); actualSquared += Math.pow(actual - mean, 2);
            if (actual != 0) { mape += Math.abs(error / actual); valid++; }
        }
        int denominator = Math.max(1, count);
        double residual = mse / denominator;
        double r2 = actualSquared == 0 ? 1.0 : Math.max(0, 1 - residual * denominator / actualSquared);
        return new HoldoutMetrics(mae / denominator, Math.sqrt(residual), valid == 0 ? 0 : mape / valid * 100,
                r2, validation, start, count);
    }

    private double[] modelForType(List<SeriesPoint> points, String modelType) {
        return switch (modelType) {
            case "EXPONENTIAL_SMOOTHING" -> doubleExponentialSmoothing(points);
            case "MOVING_AVERAGE" -> movingAverage(points);
            case "HOLT_WINTERS" -> holtWintersForecast(points);
            default -> regression(points);
        };
    }

    private int nextVersionNumber(String modelName) {
        return modelVersionMapper.selectByModelName(modelName).stream()
                .map(ModelVersion::getVersionNumber).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private record HoldoutMetrics(double mae, double rmse, double mape, double r2,
                                  boolean validationAvailable, int trainSamples, int validationSamples) {}

    private double[] doubleExponentialSmoothing(List<SeriesPoint> points) {
        double alpha = 0.3;
        double beta = 0.1;
        int n = points.size();

        double level = points.get(0).value;
        double trend = points.size() > 1 ? points.get(1).value - points.get(0).value : 0;

        for (int i = 1; i < n; i++) {
            double newLevel = alpha * points.get(i).value + (1 - alpha) * (level + trend);
            double newTrend = beta * (newLevel - level) + (1 - beta) * trend;
            level = newLevel;
            trend = newTrend;
        }

        double intercept = level - trend * (n - 1);
        return new double[]{intercept, trend};
    }

    private double[] movingAverage(List<SeriesPoint> points) {
        int window = Math.min(7, Math.max(2, points.size() / 3));
        int n = points.size();
        double sum = 0;
        int count = 0;
        for (int i = Math.max(0, n - window); i < n; i++) {
            sum += points.get(i).value;
            count++;
        }
        double avg = count > 0 ? sum / count : 0;
        double trend = 0;
        if (n >= window * 2) {
            double prevSum = 0;
            for (int i = n - 2 * window; i < n - window; i++) prevSum += points.get(i).value;
            double prevAvg = prevSum / window;
            trend = avg - prevAvg;
        }
        double intercept = avg - trend * (n - 1);
        return new double[]{intercept, trend};
    }

    private double[] holtWintersForecast(List<SeriesPoint> points) {
        int n = points.size();
        int seasonLength = Math.min(7, Math.max(2, n / 4));
        if (n < seasonLength * 2) {
            return doubleExponentialSmoothing(points);
        }

        double alpha = 0.3, beta = 0.1, gamma = 0.3;
        double[] seasonals = new double[seasonLength];
        double seasonAvg = 0;
        for (int i = 0; i < seasonLength && i < n; i++) {
            seasonAvg += points.get(i).value;
        }
        seasonAvg /= Math.min(seasonLength, n);
        for (int i = 0; i < seasonLength; i++) {
            seasonals[i] = (i < n ? points.get(i).value : seasonAvg) - seasonAvg;
        }

        double level = points.get(0).value;
        double trend = n > 1 ? points.get(1).value - points.get(0).value : 0;

        for (int i = 1; i < n; i++) {
            int seasonIdx = i % seasonLength;
            double newLevel = alpha * (points.get(i).value - seasonals[seasonIdx]) + (1 - alpha) * (level + trend);
            double newTrend = beta * (newLevel - level) + (1 - beta) * trend;
            seasonals[seasonIdx] = gamma * (points.get(i).value - newLevel) + (1 - gamma) * seasonals[seasonIdx];
            level = newLevel;
            trend = newTrend;
        }

        double intercept = level - trend * (n - 1);
        return new double[]{intercept, trend};
    }

    private List<PredictionResult> generatePredictions(PredictionTask task, ModelVersion modelVersion) {
        List<SeriesPoint> series = loadSeries(task);
        String modelType = modelVersion.getModelType() != null ? modelVersion.getModelType() : "LINEAR_REGRESSION";
        double[] model;

        if ("EXPONENTIAL_SMOOTHING".equals(modelType)) {
            model = doubleExponentialSmoothing(series);
        } else if ("MOVING_AVERAGE".equals(modelType)) {
            model = movingAverage(series);
        } else if ("HOLT_WINTERS".equals(modelType)) {
            model = holtWintersForecast(series);
        } else {
            model = regression(series);
        }

        LocalDate lastDate = series.get(series.size() - 1).date;
        double residual = residualStdDev(series, model);
        int forecastDays = task.getForecastDays() == null ? DEFAULT_FORECAST_DAYS : Math.min(task.getForecastDays(), MAX_FORECAST_DAYS);
        double zScore = "0.99".equals(task.getConfidenceLevel()) ? 2.576
                : "0.90".equals(task.getConfidenceLevel()) ? 1.645
                : "0.80".equals(task.getConfidenceLevel()) ? 1.282
                : 1.96;
        List<PredictionResult> results = new ArrayList<>();
        for (int i = 1; i <= forecastDays; i++) {
            PredictionResult result = new PredictionResult();
            result.setTaskId(task.getId());
            result.setPredictionDate(lastDate.plusDays(i).toString());
            double predictedValue = Math.max(0, model[0] + model[1] * (series.size() - 1 + i));
            result.setPredictedValue(round(predictedValue));
            result.setLowerBound(round(Math.max(0, predictedValue - zScore * residual)));
            result.setUpperBound(round(predictedValue + zScore * residual));
            result.setConfidence(residual == 0 ? 1.0 : Double.parseDouble(task.getConfidenceLevel() == null ? "0.95" : task.getConfidenceLevel()));

            result.setCreatedAt(LocalDateTime.now());
            results.add(result);

            predictionResultMapper.insert(result);
        }

        return results;
    }

    private List<SeriesPoint> loadSeries(PredictionTask task) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) throw new BusinessException("Dataset not found");
        List<SeriesPoint> points = new ArrayList<>();
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
                points.add(new SeriesPoint(date, value));
            }
        }
        if (skippedInvalid > 0 || skippedNonNumeric > 0) {
            log.info("Series load skipped {} invalid dates and {} non-numeric values for task {}",
                    skippedInvalid, skippedNonNumeric, task.getId());
        }
        points.sort(Comparator.comparing(p -> p.date));
        return points;
    }

    private LocalDate parseDateSafely(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Invalid date");
        String trimmed = value.trim();
        String normalized = trimmed.replace('/', '-').replace('.', '-');
        if (normalized.length() < 8) throw new IllegalArgumentException("Date too short: " + value);
        if (normalized.length() > 10) normalized = normalized.substring(0, 10);
        String[] parts = normalized.split("-");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid date format: " + value);
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        if (year < 1900 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) {
            throw new IllegalArgumentException("Date out of range: " + value);
        }
        return LocalDate.of(year, month, day);
    }

    private double[] regression(List<SeriesPoint> points) {
        double meanX = (points.size() - 1) / 2.0;
        double meanY = points.stream().mapToDouble(p -> p.value).average().orElse(0);
        double numerator = 0, denominator = 0;
        for (int i = 0; i < points.size(); i++) {
            numerator += (i - meanX) * (points.get(i).value - meanY);
            denominator += (i - meanX) * (i - meanX);
        }
        double slope = denominator == 0 ? 0 : numerator / denominator;
        return new double[]{meanY - slope * meanX, slope};
    }

    private double rSquared(List<SeriesPoint> points, double[] regression) {
        double mean = points.stream().mapToDouble(p -> p.value).average().orElse(0);
        double total = 0, residual = 0;
        for (int i = 0; i < points.size(); i++) {
            total += Math.pow(points.get(i).value - mean, 2);
            residual += Math.pow(points.get(i).value - (regression[0] + regression[1] * i), 2);
        }
        return total == 0 ? 1.0 : Math.max(0, 1 - residual / total);
    }

    private double residualStdDev(List<SeriesPoint> points, double[] regression) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) sum += Math.pow(points.get(i).value - (regression[0] + regression[1] * i), 2);
        return Math.sqrt(sum / Math.max(1, points.size() - 2));
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private record SeriesPoint(LocalDate date, double value) {}

    public PredictionTask getPredictionTask(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validatePredictionAccess(taskId, userId);
        }
        return task;
    }

    public PageResult<PredictionTask> getPredictionTasks(int page, int size) {
        Page<PredictionTask> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<PredictionTask> wrapper = new LambdaQueryWrapper<PredictionTask>()
                .orderByDesc(PredictionTask::getCreatedAt);
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Long userId = securityUtils.getCurrentUserId(auth);
            if (!isAdminUser(userId)) {
                List<Long> datasetIds = datasetMapper.selectList(
                        new LambdaQueryWrapper<Dataset>().eq(Dataset::getCreatedBy, userId))
                        .stream().map(Dataset::getId).toList();
                if (datasetIds.isEmpty()) {
                    return PageResult.of(List.of(), 0L, page, size);
                }
                wrapper.in(PredictionTask::getDatasetId, datasetIds);
            }
        }
        
        Page<PredictionTask> pageResult = predictionTaskMapper.selectPage(pageRequest, wrapper);
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    private boolean isAdminUser(Long userId) {
        try {
            return roleMapper.selectCodesByUserId(userId).contains("ADMIN");
        } catch (Exception e) {
            return false;
        }
    }

    public List<PredictionResult> getPredictionResults(Long taskId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validatePredictionAccess(taskId, userId);
        }
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
                                modelVersion.getTrainingMetricsJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        metrics.put("r2", trainingMetrics.get("r2"));
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
            metrics.put("averagePredictedValue", round(avgPredictedValue));
        }

        return metrics;
    }

    public Map<String, Object> getPredictionComparison(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Prediction task not found");
        }

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("taskId", taskId);

        List<SeriesPoint> historicalData = loadSeries(task);
        List<PredictionResult> predictions = predictionResultMapper.selectByTaskId(taskId);

        if (historicalData.isEmpty() || predictions.isEmpty()) {
            comparison.put("status", "INSUFFICIENT_DATA");
            comparison.put("message", "Not enough data for comparison");
            return comparison;
        }

        double[] regression = regression(historicalData);
        List<Map<String, Object>> comparisonPoints = new ArrayList<>();

        double totalError = 0;
        double totalAbsError = 0;
        int comparisonCount = 0;

        for (PredictionResult prediction : predictions) {
            try {
                LocalDate predDate = LocalDate.parse(prediction.getPredictionDate());
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", prediction.getPredictionDate());
                point.put("predictedValue", prediction.getPredictedValue());
                point.put("lowerBound", prediction.getLowerBound());
                point.put("upperBound", prediction.getUpperBound());

                Optional<SeriesPoint> actualPoint = historicalData.stream()
                        .filter(sp -> sp.date.equals(predDate))
                        .findFirst();

                if (actualPoint.isPresent()) {
                    double actual = actualPoint.get().value;
                    double predicted = prediction.getPredictedValue();
                    double error = predicted - actual;
                    double absError = Math.abs(error);
                    double percentageError = actual != 0 ? Math.abs(error / actual) * 100 : 0;

                    point.put("actualValue", round(actual));
                    point.put("error", round(error));
                    point.put("absError", round(absError));
                    point.put("percentageError", round(percentageError));
                    point.put("withinConfidenceInterval",
                            actual >= prediction.getLowerBound() && actual <= prediction.getUpperBound());

                    totalError += error;
                    totalAbsError += absError;
                    comparisonCount++;
                } else {
                    point.put("actualValue", null);
                    point.put("status", "PENDING_ACTUAL");
                }

                comparisonPoints.add(point);
            } catch (Exception e) {
                log.warn("Failed to compare prediction for date: {}", prediction.getPredictionDate(), e);
            }
        }

        comparison.put("comparisonPoints", comparisonPoints);
        comparison.put("totalComparisons", comparisonCount);

        if (comparisonCount > 0) {
            comparison.put("meanError", round(totalError / comparisonCount));
            comparison.put("meanAbsoluteError", round(totalAbsError / comparisonCount));

            long withinIntervalCount = comparisonPoints.stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("withinConfidenceInterval")))
                    .count();
            comparison.put("withinConfidenceRate", round((double) withinIntervalCount / comparisonCount * 100));
        }

        comparison.put("deviationAnalysis", analyzeDeviation(comparisonPoints));

        return comparison;
    }

    private Map<String, Object> analyzeDeviation(List<Map<String, Object>> comparisonPoints) {
        Map<String, Object> analysis = new LinkedHashMap<>();

        List<Map<String, Object>> pointsWithError = comparisonPoints.stream()
                .filter(p -> p.get("error") != null)
                .toList();

        if (pointsWithError.isEmpty()) {
            analysis.put("status", "NO_ERROR_DATA");
            return analysis;
        }

        double maxError = pointsWithError.stream()
                .mapToDouble(p -> ((Number) p.get("absError")).doubleValue())
                .max()
                .orElse(0);

        double maxPercentageError = pointsWithError.stream()
                .mapToDouble(p -> ((Number) p.get("percentageError")).doubleValue())
                .max()
                .orElse(0);

        Optional<Map<String, Object>> worstPoint = pointsWithError.stream()
                .max(Comparator.comparingDouble(p -> ((Number) p.get("absError")).doubleValue()));

        Optional<Map<String, Object>> bestPoint = pointsWithError.stream()
                .min(Comparator.comparingDouble(p -> ((Number) p.get("absError")).doubleValue()));

        long overestimateCount = pointsWithError.stream()
                .filter(p -> ((Number) p.get("error")).doubleValue() > 0)
                .count();

        long underestimateCount = pointsWithError.stream()
                .filter(p -> ((Number) p.get("error")).doubleValue() < 0)
                .count();

        analysis.put("maxError", round(maxError));
        analysis.put("maxPercentageError", round(maxPercentageError));
        analysis.put("worstPerformingPoint", worstPoint.map(p -> p.get("date")).orElse(null));
        analysis.put("bestPerformingPoint", bestPoint.map(p -> p.get("date")).orElse(null));
        analysis.put("overestimateCount", overestimateCount);
        analysis.put("underestimateCount", underestimateCount);
        analysis.put("overestimateRate", round((double) overestimateCount / pointsWithError.size() * 100));
        analysis.put("underestimateRate", round((double) underestimateCount / pointsWithError.size() * 100));

        String accuracyLevel;
        double meanPercentageError = pointsWithError.stream()
                .mapToDouble(p -> ((Number) p.get("percentageError")).doubleValue())
                .average()
                .orElse(0);

        if (meanPercentageError < 5) {
            accuracyLevel = "HIGH";
        } else if (meanPercentageError < 10) {
            accuracyLevel = "MEDIUM";
        } else if (meanPercentageError < 20) {
            accuracyLevel = "LOW";
        } else {
            accuracyLevel = "VERY_LOW";
        }
        analysis.put("accuracyLevel", accuracyLevel);
        analysis.put("meanPercentageError", round(meanPercentageError));

        return analysis;
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validatePredictionAccess(taskId, userId);
        }

        task.setStatus("RETRAINING");
        predictionTaskMapper.updateById(task);

        // Execute retraining asynchronously
        executeRetrainAsync(taskId);

        return task;
    }

    @Async("taskExecutor")
    protected void executeRetrainAsync(Long taskId) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task == null) return;
        long startedAtNanos = System.nanoTime();
        int resultCount = 0;
        Long modelVersionId = null;
        try {
            ModelVersion modelVersion = trainModel(task);
            modelVersionId = modelVersion.getId();
            task.setModelVersionId(modelVersion.getId());

            predictionResultMapper.delete(new LambdaQueryWrapper<PredictionResult>().eq(PredictionResult::getTaskId, taskId));
            List<PredictionResult> results = generatePredictions(task, modelVersion);
            resultCount = results.size();

            task.setStatus("COMPLETED");
            log.info("Prediction task {} retrained with {} new results", taskId, results.size());
        } catch (Exception e) {
            log.error("Retraining failed for task {}", taskId, e);
            task.setStatus("FAILED");
        }

        task.setUpdatedAt(LocalDateTime.now());
        predictionTaskMapper.updateById(task);
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

    @AuditOperation(action = "DELETE_PREDICTION", resourceType = "PREDICTION", description = "Delete prediction task")
    public void deletePredictionTask(Long taskId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validatePredictionAccess(taskId, userId);
        }
        predictionResultMapper.delete(new LambdaQueryWrapper<PredictionResult>().eq(PredictionResult::getTaskId, taskId));
        predictionTaskMapper.deleteById(taskId);
    }

    public void updatePredictionType(Long taskId, String modelType) {
        PredictionTask task = predictionTaskMapper.selectById(taskId);
        if (task != null) {
            task.setModelType(modelType);
            task.setUpdatedAt(LocalDateTime.now());
            predictionTaskMapper.updateById(task);
        }
    }

    public long getPredictionTaskCount() {
        return predictionTaskMapper.selectCount(new LambdaQueryWrapper<>());
    }

    private void validateDatasetParsed(Dataset dataset) {
        String status = dataset.getStatus();
        if (!"PARSED".equals(status)) {
            throw new BusinessException("数据集尚未解析完成 (当前状态: " + status + ")，请先执行解析后再进行预测分析");
        }
        if (dataset.getSchemaJson() == null || dataset.getSchemaJson().isBlank()) {
            throw new BusinessException("数据集 Schema 为空，无法执行预测");
        }
        if (dataset.getRowCount() == null || dataset.getRowCount() <= 0) {
            throw new BusinessException("数据集行数为 0，无法执行预测");
        }
    }
}
