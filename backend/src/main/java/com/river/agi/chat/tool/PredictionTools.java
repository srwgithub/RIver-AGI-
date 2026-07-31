package com.river.agi.chat.tool;

import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.service.PredictionService;
import com.river.agi.prediction.service.EnhancedPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PredictionTools {

    private final PredictionService predictionService;
    private final ResourceAccessValidator accessValidator;
    private final SecurityUtils securityUtils;

    @Autowired(required = false)
    private EnhancedPredictionService enhancedPredictionService;

    @Tool(description = "Create and run a demand prediction task on a dataset. Specify dataset ID, target field (what to predict, e.g. sales), time field (date column). Optional: forecastDays (1-365), algorithm (AUTO/LINEAR_REGRESSION/EXPONENTIAL_SMOOTHING/HOLT_WINTERS/MOVING_AVERAGE), confidenceLevel (0.80/0.90/0.95/0.99). Returns task ID and status.")
    public String createPredictionTask(Long datasetId, String targetField, String timeField,
                                       Integer forecastDays, String algorithm, String confidenceLevel) {
        log.info("Tool called: createPredictionTask, datasetId: {}, targetField: {}, timeField: {}, forecastDays: {}, algorithm: {}",
                datasetId, targetField, timeField, forecastDays, algorithm);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validateDatasetAccess(datasetId, userId);

            PredictionTask task = new PredictionTask();
            task.setDatasetId(datasetId);
            task.setTargetField(targetField);
            task.setTimeField(timeField);
            task.setModelType(algorithm);
            task.setForecastDays(forecastDays);
            task.setConfidenceLevel(confidenceLevel);

            task = predictionService.createPredictionTask(task, auth);
            task = predictionService.runPrediction(task.getId());

            return "{\"taskId\": " + task.getId() + ", \"status\": \"" + task.getStatus() +
                   "\", \"modelVersionId\": " + task.getModelVersionId() + "}";
        } catch (Exception e) {
            log.error("Failed to create prediction task for dataset {}", datasetId, e);
            return "{\"error\": \"Failed to create prediction task: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Get prediction results for a prediction task. Returns predicted values, confidence intervals, model metrics (MAE, RMSE, MAPE, R2) and model version info. Use this when user asks about prediction results or forecast accuracy.")
    public String getPredictionResults(Long predictionTaskId) {
        log.info("Tool called: getPredictionResults, predictionTaskId: {}", predictionTaskId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validatePredictionAccess(predictionTaskId, userId);

            var task = predictionService.getPredictionTask(predictionTaskId);

            StringBuilder result = new StringBuilder("{");
            result.append("\"taskId\": ").append(task.getId()).append(",");
            result.append("\"status\": \"").append(task.getStatus()).append("\",");

            if (task.getModelVersionId() != null) {
                var modelVersion = predictionService.getModelVersion(task.getModelVersionId());
                result.append("\"modelMetrics\": {");
                result.append("\"mae\": ").append(modelVersion.getMae() != null ? modelVersion.getMae() : "null").append(",");
                result.append("\"rmse\": ").append(modelVersion.getRmse() != null ? modelVersion.getRmse() : "null").append(",");
                result.append("\"mape\": ").append(modelVersion.getMape() != null ? modelVersion.getMape() : "null").append(",");
                if (modelVersion.getTrainingMetricsJson() != null) {
                    result.append("\"r2\": ").append(modelVersion.getTrainingMetricsJson()).append(",");
                }
                result.append("},");
            }

            var results = predictionService.getPredictionResults(predictionTaskId);
            result.append("\"predictionCount\": ").append(results.size()).append(",");
            if (!results.isEmpty()) {
                result.append("\"latestPrediction\": {");
                var latest = results.get(results.size() - 1);
                result.append("\"date\": \"").append(latest.getPredictionDate()).append("\",");
                result.append("\"value\": ").append(latest.getPredictedValue()).append(",");
                result.append("\"lowerBound\": ").append(latest.getLowerBound()).append(",");
                result.append("\"upperBound\": ").append(latest.getUpperBound()).append(",");
                result.append("\"confidence\": ").append(latest.getConfidence());
                result.append("}");
            }
            result.append("}");

            return result.toString();
        } catch (Exception e) {
            log.error("Failed to get prediction results for task {}", predictionTaskId, e);
            return "{\"error\": \"Failed to get prediction results: " + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Retrain a prediction task with updated data. Creates a new model version with improved training. Use this when user asks to retrain or improve prediction accuracy.")
    public String retrainPrediction(Long predictionTaskId) {
        log.info("Tool called: retrainPrediction, predictionTaskId: {}", predictionTaskId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validatePredictionAccess(predictionTaskId, userId);

            var task = predictionService.retrainPrediction(predictionTaskId);
            return "{\"taskId\": " + task.getId() + ", \"status\": \"" + task.getStatus() + "\"}";
        } catch (Exception e) {
            log.error("Failed to retrain prediction task {}", predictionTaskId, e);
            return "{\"error\": \"Failed to retrain prediction: " + e.getMessage() + "\"}";
        }
    }
}
