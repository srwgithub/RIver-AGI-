package com.river.agi.chart.controller;

import com.river.agi.chart.entity.ChartConfig;
import com.river.agi.chart.entity.Report;
import com.river.agi.chart.service.ChartService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/charts")
@RequiredArgsConstructor
@Tag(name = "Charts & Reports", description = "Chart and report generation APIs")
public class ChartController {
    
    private final ChartService chartService;
    private final SecurityUtils securityUtils;
    private final ResourceAccessValidator accessValidator;
    
    @PostMapping("/recommend")
    @Operation(summary = "Recommend charts", description = "Get recommended chart types based on dataset structure")
    public ApiResponse<List<Map<String, Object>>> recommendCharts(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId) {
        return ApiResponse.ok(chartService.recommendCharts(datasetId));
    }
    
    @PostMapping("/generate")
    @Operation(summary = "Generate chart", description = "Generate chart data for visualization")
    public ApiResponse<Map<String, Object>> generateChart(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId,
            @Parameter(description = "Chart type") @RequestParam String chartType,
            @Parameter(description = "X-axis field") @RequestParam(required = false) String xAxisField,
            @Parameter(description = "Y-axis field") @RequestParam(required = false) String yAxisField) {
        return ApiResponse.ok(chartService.generateChart(datasetId, chartType, xAxisField, yAxisField));
    }
    
    @PostMapping("/save")
    @Operation(summary = "Save chart config", description = "Save a chart configuration")
    public ApiResponse<ChartConfig> saveChartConfig(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId,
            @Parameter(description = "Chart type") @RequestParam String chartType,
            @Parameter(description = "Chart title") @RequestParam String title,
            @Parameter(description = "X-axis field") @RequestParam String xAxisField,
            @Parameter(description = "Y-axis field") @RequestParam String yAxisField,
            @RequestBody Map<String, Object> data,
            Authentication authentication) {
        return ApiResponse.ok(chartService.saveChartConfig(datasetId, chartType, title, 
                xAxisField, yAxisField, data, authentication));
    }
    
    @GetMapping("/configs/{datasetId}")
    @Operation(summary = "Get chart configs", description = "Get saved chart configurations for a dataset")
    public ApiResponse<List<ChartConfig>> getChartConfigs(@Parameter(description = "Dataset ID") @PathVariable Long datasetId) {
        return ApiResponse.ok(chartService.getChartConfigs(datasetId));
    }
    
    // Report endpoints
    
    @PostMapping("/reports")
    @Operation(summary = "Generate report", description = "Generate a data analysis report")
    public ApiResponse<Report> generateReport(
            @Parameter(description = "Dataset ID") @RequestParam Long datasetId,
            @Parameter(description = "Report type") @RequestParam(defaultValue = "SUMMARY") String reportType,
            Authentication authentication) {
        return ApiResponse.ok(chartService.generateReport(datasetId, reportType, authentication));
    }
    
    @GetMapping("/reports")
    @Operation(summary = "List reports", description = "Get paginated list of reports")
    public ApiResponse<PageResult<Report>> getReports(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Dataset ID filter") @RequestParam(required = false) Long datasetId) {
        return ApiResponse.ok(chartService.getReports(page, size, datasetId));
    }
    
    @GetMapping("/reports/{id}")
    @Operation(summary = "Get report", description = "Get report by ID")
    public ApiResponse<Report> getReport(@Parameter(description = "Report ID") @PathVariable Long id) {
        return ApiResponse.ok(chartService.getReport(id));
    }
    
    @DeleteMapping("/reports/{id}")
    @Operation(summary = "Delete report", description = "Delete report by ID")
    public ApiResponse<Void> deleteReport(@Parameter(description = "Report ID") @PathVariable Long id,
                                          Authentication authentication) {
        Report report = chartService.getReport(id);
        if (report.getDatasetId() != null && authentication != null) {
            accessValidator.validateDatasetOwnership(report.getDatasetId(), securityUtils.getCurrentUserId(authentication));
        }
        chartService.deleteReport(id);
        return ApiResponse.ok(null);
    }
}
