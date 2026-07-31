package com.river.agi.common.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.analysis.service.AnalysisService;
import com.river.agi.annotation.service.AnnotationService;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.service.AsyncTaskService;
import com.river.agi.dataset.service.DatasetParserService;
import com.river.agi.prediction.service.PredictionService;
import com.river.agi.security.service.SecurityService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskHandlers {
    
    private final AsyncTaskService asyncTaskService;
    private final DatasetParserService datasetParserService;
    private final AnalysisService analysisService;
    private final SecurityService securityService;
    private final PredictionService predictionService;
    private final AnnotationService annotationService;
    private final ObjectMapper objectMapper;
    
    @PostConstruct
    public void registerHandlers() {
        registerDatasetParseHandler();
        registerQualityAnalysisHandler();
        registerSecurityScanHandler();
        registerPredictionTrainHandler();
        registerPreAnnotationHandler();
        
        log.info("All async task handlers registered successfully");
    }
    
    private void registerDatasetParseHandler() {
        asyncTaskService.registerHandler(AsyncTask.TaskType.DATASET_PARSE.name(), task -> {
            log.info("Executing dataset parse task: {}", task.getTaskName());
            try {
                Map<String, Object> params = parseParams(task.getParamsJson());
                Long datasetId = task.getResourceId();
                
                asyncTaskService.updateTaskProgress(task.getId(), 10, Map.of("status", "Starting file parsing"));
                
                datasetParserService.parseDataset(datasetId);
                
                asyncTaskService.updateTaskProgress(task.getId(), 100, Map.of(
                    "status", "Completed",
                    "datasetId", datasetId
                ));
                
                log.info("Dataset parse task completed: {}", task.getTaskName());
            } catch (Exception e) {
                log.error("Dataset parse task failed: {}", task.getTaskName(), e);
                throw new RuntimeException("Dataset parse failed: " + e.getMessage(), e);
            }
        });
    }
    
    private void registerQualityAnalysisHandler() {
        asyncTaskService.registerHandler(AsyncTask.TaskType.QUALITY_ANALYSIS.name(), task -> {
            log.info("Executing quality analysis task: {}", task.getTaskName());
            try {
                Long datasetId = task.getResourceId();
                
                asyncTaskService.updateTaskProgress(task.getId(), 20, Map.of("status", "Loading dataset"));
                
                var analysisTask = analysisService.runAnalysis(datasetId, "QUALITY");
                
                asyncTaskService.updateTaskProgress(task.getId(), 100, Map.of(
                    "status", "Completed",
                    "analysisTaskId", analysisTask.getId()
                ));
                
                log.info("Quality analysis task completed: {}", task.getTaskName());
            } catch (Exception e) {
                log.error("Quality analysis task failed: {}", task.getTaskName(), e);
                throw new RuntimeException("Quality analysis failed: " + e.getMessage(), e);
            }
        });
    }
    
    private void registerSecurityScanHandler() {
        asyncTaskService.registerHandler(AsyncTask.TaskType.SECURITY_SCAN.name(), task -> {
            log.info("Executing security scan task: {}", task.getTaskName());
            try {
                Long datasetId = task.getResourceId();
                
                asyncTaskService.updateTaskProgress(task.getId(), 20, Map.of("status", "Loading dataset"));
                
                var result = securityService.scanSensitiveData(datasetId, null);
                
                asyncTaskService.updateTaskProgress(task.getId(), 100, Map.of(
                    "status", "Completed",
                    "scanSummary", result
                ));
                
                log.info("Security scan task completed: {}", task.getTaskName());
            } catch (Exception e) {
                log.error("Security scan task failed: {}", task.getTaskName(), e);
                throw new RuntimeException("Security scan failed: " + e.getMessage(), e);
            }
        });
    }
    
    private void registerPredictionTrainHandler() {
        asyncTaskService.registerHandler(AsyncTask.TaskType.PREDICTION.name(), task -> {
            log.info("Executing prediction train task: {}", task.getTaskName());
            try {
                Long taskId = task.getResourceId();
                
                asyncTaskService.updateTaskProgress(task.getId(), 20, Map.of("status", "Loading data"));
                
                var predictionTask = predictionService.runPrediction(taskId);
                
                asyncTaskService.updateTaskProgress(task.getId(), 100, Map.of(
                    "status", "Completed",
                    "predictionTaskId", predictionTask.getId(),
                    "modelVersionId", predictionTask.getModelVersionId()
                ));
                
                log.info("Prediction train task completed: {}", task.getTaskName());
            } catch (Exception e) {
                log.error("Prediction train task failed: {}", task.getTaskName(), e);
                throw new RuntimeException("Prediction training failed: " + e.getMessage(), e);
            }
        });
    }
    
    private void registerPreAnnotationHandler() {
        asyncTaskService.registerHandler(AsyncTask.TaskType.PRE_ANNOTATE.name(), task -> {
            log.info("Executing pre-annotation task: {}", task.getTaskName());
            try {
                Long taskId = task.getResourceId();
                
                asyncTaskService.updateTaskProgress(task.getId(), 30, Map.of("status", "Starting pre-annotation"));
                
                annotationService.preAnnotate(taskId);
                
                asyncTaskService.updateTaskProgress(task.getId(), 100, Map.of(
                    "status", "Completed",
                    "annotationTaskId", taskId
                ));
                
                log.info("Pre-annotation task completed: {}", task.getTaskName());
            } catch (Exception e) {
                log.error("Pre-annotation task failed: {}", task.getTaskName(), e);
                throw new RuntimeException("Pre-annotation failed: " + e.getMessage(), e);
            }
        });
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(paramsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse task params", e);
            return Map.of();
        }
    }
}
