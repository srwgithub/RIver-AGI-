package com.river.agi.dashboard.controller;

import com.river.agi.common.ApiResponse;
import com.river.agi.dashboard.entity.Dashboard;
import com.river.agi.dashboard.entity.DashboardWidget;
import com.river.agi.dashboard.entity.ReportInstance;
import com.river.agi.dashboard.entity.ReportTemplate;
import com.river.agi.dashboard.service.DashboardService;
import com.river.agi.dashboard.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final ReportService reportService;

    @GetMapping
    public ApiResponse<List<Dashboard>> listDashboards(
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(dashboardService.listDashboards(datasetId, category));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getDashboard(@PathVariable Long id) {
        return ApiResponse.ok(dashboardService.getDashboardWithWidgets(id));
    }

    @GetMapping("/default")
    public ApiResponse<Dashboard> getDefaultDashboard() {
        return ApiResponse.ok(dashboardService.getDefaultDashboard());
    }

    @PostMapping
    public ApiResponse<Dashboard> createDashboard(@RequestBody Dashboard dashboard, Authentication auth) {
        return ApiResponse.ok(dashboardService.createDashboard(dashboard, auth));
    }

    @PutMapping("/{id}")
    public ApiResponse<Dashboard> updateDashboard(@PathVariable Long id, @RequestBody Dashboard updates, Authentication auth) {
        return ApiResponse.ok(dashboardService.updateDashboard(id, updates, auth));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDashboard(@PathVariable Long id, Authentication auth) {
        dashboardService.deleteDashboard(id, auth);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/widgets")
    public ApiResponse<List<DashboardWidget>> listWidgets(@PathVariable Long id) {
        return ApiResponse.ok(dashboardService.listWidgets(id));
    }

    @PostMapping("/{id}/widgets")
    public ApiResponse<DashboardWidget> addWidget(@PathVariable Long id, @RequestBody DashboardWidget widget, Authentication auth) {
        return ApiResponse.ok(dashboardService.addWidget(id, widget, auth));
    }

    @PutMapping("/widgets/{widgetId}")
    public ApiResponse<DashboardWidget> updateWidget(@PathVariable Long widgetId, @RequestBody DashboardWidget updates, Authentication auth) {
        return ApiResponse.ok(dashboardService.updateWidget(widgetId, updates, auth));
    }

    @DeleteMapping("/widgets/{widgetId}")
    public ApiResponse<Void> deleteWidget(@PathVariable Long widgetId, Authentication auth) {
        dashboardService.deleteWidget(widgetId, auth);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/widgets/bulk")
    public ApiResponse<List<DashboardWidget>> bulkUpdateWidgets(
            @PathVariable Long id,
            @RequestBody List<DashboardWidget> widgets,
            Authentication auth) {
        return ApiResponse.ok(dashboardService.bulkUpdateWidgets(id, widgets, auth));
    }

    @GetMapping("/widget-types")
    public ApiResponse<Map<String, Object>> getWidgetTypes() {
        return ApiResponse.ok(dashboardService.getAvailableWidgetTypes());
    }

    @GetMapping("/trend-data")
    public ApiResponse<Map<String, Object>> getTrendDashboardData(
            @RequestParam Long datasetId,
            @RequestParam(required = false) Long predictionTaskId,
            @RequestParam(required = false) String measure,
            @RequestParam(required = false) String timeField) {
        return ApiResponse.ok(reportService.getDefaultTrendDashboardData(datasetId, predictionTaskId, measure, timeField));
    }

    @GetMapping("/reports/templates")
    public ApiResponse<List<ReportTemplate>> listTemplates(
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) String reportType) {
        return ApiResponse.ok(reportService.listTemplates(datasetId, reportType));
    }

    @PostMapping("/reports/templates")
    public ApiResponse<ReportTemplate> createTemplate(@RequestBody ReportTemplate template, Authentication auth) {
        return ApiResponse.ok(reportService.createTemplate(template, auth));
    }

    @PutMapping("/reports/templates/{id}")
    public ApiResponse<ReportTemplate> updateTemplate(@PathVariable Long id, @RequestBody ReportTemplate updates, Authentication auth) {
        return ApiResponse.ok(reportService.updateTemplate(id, updates, auth));
    }

    @DeleteMapping("/reports/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        reportService.deleteTemplate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/reports/generate")
    public ApiResponse<ReportInstance> generateReport(@RequestBody Map<String, Object> params, Authentication auth) {
        Long templateId = params.containsKey("templateId") ? Long.valueOf(params.get("templateId").toString()) : null;
        Long datasetId = Long.valueOf(params.get("datasetId").toString());
        Long predictionTaskId = params.containsKey("predictionTaskId") ? Long.valueOf(params.get("predictionTaskId").toString()) : null;
        String format = params.getOrDefault("exportFormat", "JSON").toString();
        return ApiResponse.ok(reportService.generateReport(templateId, datasetId, predictionTaskId, format, auth));
    }

    @GetMapping("/reports/instances")
    public ApiResponse<List<ReportInstance>> listInstances(@RequestParam(required = false) Long datasetId) {
        return ApiResponse.ok(reportService.listReportInstances(datasetId));
    }

    @GetMapping("/reports/instances/{id}")
    public ApiResponse<ReportInstance> getInstance(@PathVariable Long id) {
        return ApiResponse.ok(reportService.getReportInstance(id));
    }
}
