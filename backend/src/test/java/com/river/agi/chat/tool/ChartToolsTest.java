package com.river.agi.chat.tool;

import com.river.agi.chart.service.ChartService;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("图表工具测试")
class ChartToolsTest {

    @Mock private ChartService chartService;
    @Mock private ResourceAccessValidator accessValidator;
    @Mock private SecurityUtils securityUtils;
    @Mock private Authentication authentication;

    private ChartTools tools;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        tools = new ChartTools(chartService, objectMapper, accessValidator, securityUtils);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        doNothing().when(accessValidator).validateDatasetAccess(anyLong(), anyLong());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("推荐图表 - 成功返回 JSON")
    void recommendCharts_success() {
        List<Map<String, Object>> recommendations = new java.util.ArrayList<>();
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", "bar");
        rec.put("rationale", "适合分类对比");
        recommendations.add(rec);
        when(chartService.recommendCharts(1L)).thenReturn(recommendations);

        String result = tools.recommendCharts(1L);

        assertTrue(result.contains("bar"));
        assertTrue(result.contains("rationale"));
        verify(accessValidator).validateDatasetAccess(1L, 1L);
        verify(chartService).recommendCharts(1L);
    }

    @Test
    @DisplayName("推荐图表 - 返回多个推荐")
    void recommendCharts_multipleRecommendations() {
        List<Map<String, Object>> recommendations = new java.util.ArrayList<>();
        recommendations.add(Map.of("type", "bar"));
        recommendations.add(Map.of("type", "line"));
        recommendations.add(Map.of("type", "pie"));
        when(chartService.recommendCharts(2L)).thenReturn(recommendations);

        String result = tools.recommendCharts(2L);

        assertTrue(result.contains("bar"));
        assertTrue(result.contains("line"));
        assertTrue(result.contains("pie"));
    }

    @Test
    @DisplayName("推荐图表 - 异常返回错误 JSON")
    void recommendCharts_exception() {
        when(chartService.recommendCharts(1L)).thenThrow(new RuntimeException("dataset not found"));

        String result = tools.recommendCharts(1L);

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("dataset not found"));
    }

    @Test
    @DisplayName("推荐图表 - 访问校验失败返回错误")
    void recommendCharts_accessDenied() {
        doThrow(new RuntimeException("access denied")).when(accessValidator).validateDatasetAccess(1L, 1L);

        String result = tools.recommendCharts(1L);

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("access denied"));
    }

    @Test
    @DisplayName("生成图表 - 成功返回 JSON 数据")
    void generateChart_success() {
        Map<String, Object> chartData = new LinkedHashMap<>();
        chartData.put("type", "bar");
        chartData.put("labels", List.of("A", "B", "C"));
        chartData.put("values", List.of(10, 20, 30));
        when(chartService.generateChart(1L, "bar", "category", "amount")).thenReturn(chartData);

        String result = tools.generateChart(1L, "bar", "category", "amount");

        assertTrue(result.contains("bar"));
        assertTrue(result.contains("labels"));
        assertTrue(result.contains("values"));
        verify(accessValidator).validateDatasetAccess(1L, 1L);
        verify(chartService).generateChart(1L, "bar", "category", "amount");
    }

    @Test
    @DisplayName("生成图表 - 折线图类型")
    void generateChart_lineType() {
        Map<String, Object> chartData = Map.of("type", "line", "data", List.of());
        when(chartService.generateChart(1L, "line", "date", "sales")).thenReturn(chartData);

        String result = tools.generateChart(1L, "line", "date", "sales");

        assertTrue(result.contains("line"));
    }

    @Test
    @DisplayName("生成图表 - 散点图类型")
    void generateChart_scatterType() {
        Map<String, Object> chartData = Map.of("type", "scatter");
        when(chartService.generateChart(1L, "scatter", "x", "y")).thenReturn(chartData);

        String result = tools.generateChart(1L, "scatter", "x", "y");

        assertTrue(result.contains("scatter"));
    }

    @Test
    @DisplayName("生成图表 - 饼图类型")
    void generateChart_pieType() {
        Map<String, Object> chartData = Map.of("type", "pie");
        when(chartService.generateChart(1L, "pie", "category", "count")).thenReturn(chartData);

        String result = tools.generateChart(1L, "pie", "category", "count");

        assertTrue(result.contains("pie"));
    }

    @Test
    @DisplayName("生成图表 - 面积图类型")
    void generateChart_areaType() {
        Map<String, Object> chartData = Map.of("type", "area");
        when(chartService.generateChart(1L, "area", "date", "value")).thenReturn(chartData);

        String result = tools.generateChart(1L, "area", "date", "value");

        assertTrue(result.contains("area"));
    }

    @Test
    @DisplayName("生成图表 - 异常返回错误 JSON")
    void generateChart_exception() {
        when(chartService.generateChart(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("invalid fields"));

        String result = tools.generateChart(1L, "bar", "x", "y");

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("invalid fields"));
    }

    @Test
    @DisplayName("生成图表 - 访问校验失败返回错误")
    void generateChart_accessDenied() {
        doThrow(new RuntimeException("forbidden")).when(accessValidator).validateDatasetAccess(1L, 1L);

        String result = tools.generateChart(1L, "bar", "x", "y");

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("forbidden"));
    }
}
