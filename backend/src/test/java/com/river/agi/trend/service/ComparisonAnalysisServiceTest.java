package com.river.agi.trend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("对比分析服务测试")
class ComparisonAnalysisServiceTest {

    @Mock
    private PredictionTaskMapper predictionTaskMapper;
    @Mock
    private PredictionResultMapper predictionResultMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;

    private ComparisonAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ComparisonAnalysisService(
                predictionTaskMapper, predictionResultMapper, null,
                datasetMapper, dataReader, new ObjectMapper());
    }

    private List<Map<String, String>> buildSeriesRows(int count, double base, double step) {
        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < count; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("sales", String.valueOf(base + step * i));
            rows.add(row);
        }
        return rows;
    }

    @Test
    @DisplayName("getActualVsPredicted - 任务不存在抛异常")
    void getActualVsPredicted_taskNotFound() {
        when(predictionTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getActualVsPredicted(1L));
    }

    @Test
    @DisplayName("getActualVsPredicted - 成功返回结果")
    void getActualVsPredicted_success() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2025-06-01");
        pr.setPredictedValue(120.0);
        pr.setActualValue(100.0);
        pr.setLowerBound(110.0);
        pr.setUpperBound(130.0);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(5, 100, 1));
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(pr));

        Map<String, Object> result = service.getActualVsPredicted(1L);
        assertNotNull(result);
        assertNotNull(result.get("xAxis"));
        assertNotNull(result.get("actual"));
        assertNotNull(result.get("predicted"));
        assertNotNull(result.get("deviations"));
        assertNotNull(result.get("summary"));
    }

    @Test
    @DisplayName("getYearOverYear - 数据集不存在抛异常")
    void getYearOverYear_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.getYearOverYear(1L, "date", "sales", "2025-01"));
    }

    @Test
    @DisplayName("getYearOverYear - 成功返回同比数据")
    void getYearOverYear_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        // 跨两年的数据
        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 60; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i * 30).toString());
            row.put("sales", String.valueOf(100.0 + i));
            rows.add(row);
        }

        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(rows);

        Map<String, Object> result = service.getYearOverYear(1L, "date", "sales", "2025-01");
        assertNotNull(result);
        assertEquals("YoY", result.get("type"));
        assertNotNull(result.get("yoyData"));
    }

    @Test
    @DisplayName("getMonthOverMonth - 数据集不存在抛异常")
    void getMonthOverMonth_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.getMonthOverMonth(1L, "date", "sales"));
    }

    @Test
    @DisplayName("getMonthOverMonth - 成功返回环比数据")
    void getMonthOverMonth_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(20, 100, 5));

        Map<String, Object> result = service.getMonthOverMonth(1L, "date", "sales");
        assertNotNull(result);
        assertEquals("MoM", result.get("type"));
        assertNotNull(result.get("momData"));
    }

    @Test
    @DisplayName("getMultiAlgorithmComparison - 数据集不存在抛异常")
    void getMultiAlgorithmComparison_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.getMultiAlgorithmComparison(1L, "date", "sales", 5));
    }

    @Test
    @DisplayName("getMultiAlgorithmComparison - 数据点不足抛异常")
    void getMultiAlgorithmComparison_insufficient() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(5, 100, 1));
        assertThrows(BusinessException.class, () ->
                service.getMultiAlgorithmComparison(1L, "date", "sales", 5));
    }

    @Test
    @DisplayName("getMultiAlgorithmComparison - 成功返回多算法对比")
    void getMultiAlgorithmComparison_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(30, 100, 2));

        Map<String, Object> result = service.getMultiAlgorithmComparison(1L, "date", "sales", 5);
        assertNotNull(result);
        assertNotNull(result.get("xAxis"));
        assertNotNull(result.get("actualValues"));
        assertNotNull(result.get("algorithmPredictions"));
        assertNotNull(result.get("algorithmMetrics"));
        assertNotNull(result.get("bestAlgorithm"));
    }
}
