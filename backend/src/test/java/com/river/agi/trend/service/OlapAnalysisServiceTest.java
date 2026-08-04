package com.river.agi.trend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
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
@DisplayName("OLAP 分析服务测试")
class OlapAnalysisServiceTest {

    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;

    private OlapAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new OlapAnalysisService(datasetMapper, dataReader, new ObjectMapper());
    }

    private List<Map<String, String>> buildPivotRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] regions = {"north", "south"};
        String[] products = {"A", "B"};
        for (String region : regions) {
            for (String product : products) {
                Map<String, String> row = new HashMap<>();
                row.put("region", region);
                row.put("product", product);
                row.put("sales", "100.0");
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> buildTimeSeriesRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 30; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("category", i % 2 == 0 ? "X" : "Y");
            row.put("sales", String.valueOf(100.0 + i));
            rows.add(row);
        }
        return rows;
    }

    @Test
    @DisplayName("pivotTable - 数据集不存在抛异常")
    void pivotTable_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.pivotTable(1L, "region", "product", "sales", "SUM"));
    }

    @Test
    @DisplayName("pivotTable - 成功生成数据透视表")
    void pivotTable_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildPivotRows());

        Map<String, Object> result = service.pivotTable(1L, "region", "product", "sales", "SUM");
        assertNotNull(result);
        assertNotNull(result.get("headers"));
        assertNotNull(result.get("data"));
        assertEquals("SUM", result.get("aggFunc"));
    }

    @Test
    @DisplayName("pivotTable - AVG 聚合")
    void pivotTable_avgAgg() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildPivotRows());

        Map<String, Object> result = service.pivotTable(1L, "region", "product", "sales", "AVG");
        assertNotNull(result);
    }

    @Test
    @DisplayName("pivotTable - COUNT 聚合")
    void pivotTable_countAgg() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildPivotRows());

        Map<String, Object> result = service.pivotTable(1L, "region", "product", "sales", "COUNT");
        assertNotNull(result);
    }

    @Test
    @DisplayName("pivotTable - MAX 聚合")
    void pivotTable_maxAgg() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildPivotRows());

        Map<String, Object> result = service.pivotTable(1L, "region", "product", "sales", "MAX");
        assertNotNull(result);
    }

    @Test
    @DisplayName("pivotTable - MIN 聚合")
    void pivotTable_minAgg() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildPivotRows());

        Map<String, Object> result = service.pivotTable(1L, "region", "product", "sales", "MIN");
        assertNotNull(result);
    }

    @Test
    @DisplayName("sliceAndDice - 数据集不存在抛异常")
    void sliceAndDice_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.sliceAndDice(1L, "category", "sales", null, "date"));
    }

    @Test
    @DisplayName("sliceAndDice - 成功切片分析")
    void sliceAndDice_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildTimeSeriesRows());

        Map<String, Object> result = service.sliceAndDice(
                1L, "category", "sales", null, "date");
        assertNotNull(result);
        assertNotNull(result.get("totalRecords"));
        assertNotNull(result.get("category_sales"));
        assertNotNull(result.get("sales_total"));
        assertNotNull(result.get("sales_avg"));
    }

    @Test
    @DisplayName("sliceAndDice - 带过滤器")
    void sliceAndDice_withFilters() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildTimeSeriesRows());

        Map<String, Object> result = service.sliceAndDice(
                1L, "category,date", "sales", "category:X", "date");
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPeriodComparison - 数据集不存在抛异常")
    void getPeriodComparison_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.getPeriodComparison(1L, "date", "sales", "2025-01-01", "2025-01-10", "MOM"));
    }

    @Test
    @DisplayName("getPeriodComparison - 环比对比")
    void getPeriodComparison_mom() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildTimeSeriesRows());

        Map<String, Object> result = service.getPeriodComparison(
                1L, "date", "sales", "2025-01-10", "2025-01-15", "MOM");
        assertNotNull(result);
        assertEquals("MOM", result.get("comparisonType"));
        assertEquals("环比", result.get("label"));
    }

    @Test
    @DisplayName("getPeriodComparison - 同比对比")
    void getPeriodComparison_yoy() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildTimeSeriesRows());

        Map<String, Object> result = service.getPeriodComparison(
                1L, "date", "sales", "2025-01-10", "2025-01-15", "YOY");
        assertNotNull(result);
        assertEquals("YOY", result.get("comparisonType"));
        assertEquals("同比", result.get("label"));
    }

    @Test
    @DisplayName("getKpiDashboard - 数据集不存在抛异常")
    void getKpiDashboard_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getKpiDashboard(1L, "sales", "date"));
    }

    @Test
    @DisplayName("getKpiDashboard - 成功生成 KPI")
    void getKpiDashboard_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildTimeSeriesRows());

        Map<String, Object> result = service.getKpiDashboard(1L, "sales", "date");
        assertNotNull(result);
        assertNotNull(result.get("total"));
        assertNotNull(result.get("average"));
        assertNotNull(result.get("max"));
        assertNotNull(result.get("min"));
        assertNotNull(result.get("median"));
        assertNotNull(result.get("stdDev"));
        assertNotNull(result.get("q1"));
        assertNotNull(result.get("q3"));
        assertNotNull(result.get("iqr"));
    }

    @Test
    @DisplayName("getKpiDashboard - 空数据返回基本结构")
    void getKpiDashboard_empty() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(new ArrayList<>());

        Map<String, Object> result = service.getKpiDashboard(1L, "sales", "date");
        assertNotNull(result);
        assertEquals(0, result.get("recordCount"));
    }
}
