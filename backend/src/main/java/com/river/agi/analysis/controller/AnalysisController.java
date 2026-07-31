package com.river.agi.analysis.controller;

import com.river.agi.analysis.entity.AnalysisTask;
import com.river.agi.analysis.entity.FieldStatistics;
import com.river.agi.analysis.entity.OutlierDetection;
import com.river.agi.analysis.service.AnalysisService;
import com.river.agi.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "Data analysis APIs")
public class AnalysisController {
    
    private final AnalysisService analysisService;

    @GetMapping("/tasks")
    @Operation(summary = "List analysis tasks", description = "List analysis tasks with optional dataset filter and pagination")
    public ApiResponse<Page<AnalysisTask>> listAnalysisTasks(
            @Parameter(description = "Dataset ID") @RequestParam(required = false) Long datasetId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(analysisService.listAnalysisTasks(datasetId, page, size));
    }
    
    @PostMapping("/tasks")
    @Operation(summary = "Create analysis task", description = "Create a new analysis task")
    public ApiResponse<AnalysisTask> createAnalysisTask(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId,
            @Parameter(description = "Task type") @RequestParam String taskType,
            @RequestBody(required = false) Map<String, Object> parameters) {
        return ApiResponse.ok(analysisService.createAnalysisTask(datasetId, taskType, parameters));
    }
    
    @GetMapping("/tasks/{id}")
    @Operation(summary = "Get analysis task", description = "Get analysis task by ID")
    public ApiResponse<AnalysisTask> getAnalysisTask(@Parameter(description = "Task ID") @PathVariable Long id) {
        return ApiResponse.ok(analysisService.getAnalysisTask(id));
    }
    
    @GetMapping("/tasks/{id}/results")
    @Operation(summary = "Get analysis results", description = "Get results of an analysis task")
    public ApiResponse<String> getAnalysisResults(@Parameter(description = "Task ID") @PathVariable Long id) {
        AnalysisTask task = analysisService.getAnalysisTask(id);
        return ApiResponse.ok(task.getResultJson());
    }
    
    @PostMapping("/profile")
    @Operation(summary = "Generate data profile", description = "Generate comprehensive data profile")
    public ApiResponse<AnalysisTask> generateProfile(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId) {
        return ApiResponse.ok(analysisService.runAnalysis(datasetId, "PROFILE"));
    }
    
    @PostMapping("/outliers")
    @Operation(summary = "Detect outliers", description = "Detect outliers in numeric fields")
    public ApiResponse<AnalysisTask> detectOutliers(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId) {
        return ApiResponse.ok(analysisService.runAnalysis(datasetId, "OUTLIERS"));
    }
    
    @PostMapping("/quality")
    @Operation(summary = "Analyze data quality", description = "Analyze overall data quality")
    public ApiResponse<AnalysisTask> analyzeQuality(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId) {
        return ApiResponse.ok(analysisService.runAnalysis(datasetId, "QUALITY"));
    }
    
    @GetMapping("/statistics/{datasetId}")
    @Operation(summary = "Get field statistics", description = "Get statistics for all fields")
    public ApiResponse<List<FieldStatistics>> getFieldStatistics(
            @Parameter(description = "Dataset ID") @PathVariable Long datasetId) {
        return ApiResponse.ok(analysisService.getFieldStatistics(datasetId));
    }
    
    @GetMapping("/outliers/{datasetId}")
    @Operation(summary = "Get outliers", description = "Get detected outliers")
    public ApiResponse<List<OutlierDetection>> getOutliers(
            @Parameter(description = "Dataset ID") @PathVariable Long datasetId) {
        return ApiResponse.ok(analysisService.getOutliers(datasetId));
    }
    
    @GetMapping("/tasks/count")
    @Operation(summary = "Count analysis tasks", description = "Get total number of analysis tasks")
    public ApiResponse<Long> countAnalysisTasks() {
        return ApiResponse.ok(analysisService.getAnalysisTaskCount());
    }
}
