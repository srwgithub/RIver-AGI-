package com.river.agi.chat.tool;

import com.river.agi.chart.service.ChartService;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChartTools {
    
    private final ChartService chartService;
    private final ObjectMapper objectMapper;
    private final ResourceAccessValidator accessValidator;
    private final SecurityUtils securityUtils;
    
    @Tool(description = "Recommend suitable chart types for a dataset based on its column types and data characteristics. Returns chart type recommendations (bar, line, pie, scatter, area) with rationale. Use this when user asks what charts to create.")
    public String recommendCharts(Long datasetId) {
        log.info("Tool called: recommendCharts, datasetId: {}", datasetId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
            
            var recommendations = chartService.recommendCharts(datasetId);
            return objectMapper.writeValueAsString(recommendations);
        } catch (Exception e) {
            log.error("Failed to recommend charts for dataset {}", datasetId, e);
            return "{\"error\": \"Failed to recommend charts: " + e.getMessage() + "\"}";
        }
    }
    
    @Tool(description = "Generate chart data for visualization. Specify chart type (bar, line, pie, scatter, area), x-axis field and y-axis field. Returns structured chart data with labels, values and configuration. Use this when user wants to create a chart or visualize data.")
    public String generateChart(Long datasetId, String chartType, String xAxisField, String yAxisField) {
        log.info("Tool called: generateChart, datasetId: {}, chartType: {}, xAxisField: {}, yAxisField: {}", 
                datasetId, chartType, xAxisField, yAxisField);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
            
            var chartData = chartService.generateChart(datasetId, chartType, xAxisField, yAxisField);
            return objectMapper.writeValueAsString(chartData);
        } catch (Exception e) {
            log.error("Failed to generate chart for dataset {}", datasetId, e);
            return "{\"error\": \"Failed to generate chart: " + e.getMessage() + "\"}";
        }
    }
}
