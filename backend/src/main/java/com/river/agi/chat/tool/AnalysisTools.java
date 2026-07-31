package com.river.agi.chat.tool;

import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.analysis.service.AnalysisService;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisTools {
    
    private final AnalysisService analysisService;
    private final DatasetMapper datasetMapper;
    private final ResourceAccessValidator accessValidator;
    private final SecurityUtils securityUtils;
    
    @Tool(description = "Inspect a dataset to get basic information like name, row count, column count, file type and status. Use this to understand what data is available before analysis.")
    public Map<String, Object> inspectDataset(Long datasetId) {
        log.info("Tool called: inspectDataset, datasetId: {}", datasetId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
            
            Dataset dataset = datasetMapper.selectById(datasetId);
            if (dataset == null) {
                return Map.of("error", "Dataset not found");
            }
            return Map.of(
                    "id", dataset.getId(),
                    "name", dataset.getName(),
                    "rowCount", dataset.getRowCount() != null ? dataset.getRowCount() : 0,
                    "columnCount", dataset.getColumnCount() != null ? dataset.getColumnCount() : 0,
                    "status", dataset.getStatus() != null ? dataset.getStatus() : "UNKNOWN",
                    "fileType", dataset.getFileType() != null ? dataset.getFileType() : "UNKNOWN",
                    "description", dataset.getDescription() != null ? dataset.getDescription() : ""
            );
        } catch (Exception e) {
            log.error("Failed to inspect dataset {}", datasetId, e);
            return Map.of("error", e.getMessage());
        }
    }
    
    @Tool(description = "Generate a data profile for a dataset including column statistics, data types, null rates, value distributions and quality summary. Use this when user asks for data profile or data overview.")
    public String profileDataset(Long datasetId) {
        log.info("Tool called: profileDataset, datasetId: {}", datasetId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
            
            var task = analysisService.runAnalysis(datasetId, "PROFILE");
            if (task != null && task.getResultJson() != null) {
                return task.getResultJson();
            }
            return "{\"status\":\"completed\",\"message\":\"Profile generation completed\"}";
        } catch (Exception e) {
            log.error("Failed to profile dataset {}", datasetId, e);
            return "{\"error\": \"Failed to profile dataset: " + e.getMessage() + "\"}";
        }
    }
    
    @Tool(description = "Detect outliers and anomalies in a dataset. Returns outlier detection results including Z-scores, anomaly levels and affected rows. Use this when user asks about anomalies or outliers.")
    public String detectOutliers(Long datasetId, String field) {
        log.info("Tool called: detectOutliers, datasetId: {}, field: {}", datasetId, field);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
            
            var task = analysisService.runAnalysis(datasetId, "OUTLIERS");
            if (task != null && task.getResultJson() != null) {
                return task.getResultJson();
            }
            return "{\"status\":\"completed\",\"message\":\"Outlier detection completed\"}";
        } catch (Exception e) {
            log.error("Failed to detect outliers for dataset {}", datasetId, e);
            return "{\"error\": \"Failed to detect outliers: " + e.getMessage() + "\"}";
        }
    }
    
    @Tool(description = "Analyze data quality of a dataset including completeness, uniqueness, accuracy, consistency and validity scores. Returns quality metrics, issues and overall grade. Use this when user asks about data quality.")
    public String analyzeQuality(Long datasetId) {
        log.info("Tool called: analyzeQuality, datasetId: {}", datasetId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
            
            var task = analysisService.runAnalysis(datasetId, "QUALITY");
            if (task != null && task.getResultJson() != null) {
                return task.getResultJson();
            }
            return "{\"status\":\"completed\",\"message\":\"Quality analysis completed\"}";
        } catch (Exception e) {
            log.error("Failed to analyze quality for dataset {}", datasetId, e);
            return "{\"error\": \"Failed to analyze quality: " + e.getMessage() + "\"}";
        }
    }
}
