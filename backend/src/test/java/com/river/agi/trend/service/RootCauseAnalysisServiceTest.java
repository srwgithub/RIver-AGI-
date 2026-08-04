package com.river.agi.trend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.trend.entity.AnomalyAlert;
import com.river.agi.trend.entity.RootCauseAnalysis;
import com.river.agi.trend.mapper.AnomalyAlertMapper;
import com.river.agi.trend.mapper.RootCauseAnalysisMapper;
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
@DisplayName("根因分析服务测试")
class RootCauseAnalysisServiceTest {

    @Mock
    private RootCauseAnalysisMapper rcaMapper;
    @Mock
    private AnomalyAlertMapper alertMapper;
    @Mock
    private PredictionTaskMapper taskMapper;
    @Mock
    private PredictionResultMapper resultMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;

    private RootCauseAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new RootCauseAnalysisService(
                rcaMapper, alertMapper, taskMapper, resultMapper,
                datasetMapper, dataReader, new ObjectMapper());
    }

    private List<Map<String, String>> buildRichRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        String[] regions = {"north", "south"};
        for (int i = 0; i < 20; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("region", regions[i % 2]);
            row.put("sales", String.valueOf(100.0 + i * 2));
            row.put("marketing", String.valueOf(50.0 + i));
            rows.add(row);
        }
        return rows;
    }

    @Test
    @DisplayName("analyze - 告警不存在抛异常")
    void analyze_alertNotFound() {
        when(alertMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.analyze(1L));
    }

    @Test
    @DisplayName("analyze - 数据集不存在抛异常")
    void analyze_datasetNotFound() {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId(1L);
        alert.setDatasetId(10L);
        alert.setActualValue(120.0);
        alert.setPredictedValue(100.0);
        alert.setDeviationPercent(20.0);

        when(alertMapper.selectById(1L)).thenReturn(alert);
        when(datasetMapper.selectById(10L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.analyze(1L));
    }

    @Test
    @DisplayName("analyze - 成功分析 PREDICTION_DEVIATION 告警")
    void analyze_predictionDeviationSuccess() {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId(1L);
        alert.setDatasetId(10L);
        alert.setPredictionTaskId(5L);
        alert.setAnomalyType("PREDICTION_DEVIATION");
        alert.setSeverity("RED");
        alert.setDimension("sales");
        alert.setAnomalyDate("2025-01-10");
        alert.setActualValue(120.0);
        alert.setPredictedValue(100.0);
        alert.setDeviationPercent(20.0);

        Dataset dataset = new Dataset();
        dataset.setId(10L);
        dataset.setSchemaJson("{\"date\":\"DATE\",\"sales\":\"NUMERIC\",\"region\":\"STRING\",\"marketing\":\"NUMERIC\"}");

        PredictionTask task = new PredictionTask();
        task.setId(5L);
        task.setTargetField("sales");
        task.setTimeField("date");

        when(alertMapper.selectById(1L)).thenReturn(alert);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildRichRows());
        when(taskMapper.selectById(5L)).thenReturn(task);
        when(rcaMapper.insert(any())).thenAnswer(inv -> {
            RootCauseAnalysis r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });

        RootCauseAnalysis rca = service.analyze(1L);
        assertNotNull(rca);
        assertEquals(1L, rca.getAnomalyAlertId());
        assertEquals(5L, rca.getPredictionTaskId());
        assertEquals("PREDICTION_DEVIATION", rca.getAnalysisType());
        assertNotNull(rca.getAnalysisSummary());
        assertNotNull(rca.getTopContributorsJson());
        assertNotNull(rca.getFactorsJson());
        assertNotNull(rca.getRecommendationsJson());
    }

    @Test
    @DisplayName("analyze - 任务为 null 时使用 dimension 作为 target")
    void analyze_nullTask() {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId(1L);
        alert.setDatasetId(10L);
        alert.setPredictionTaskId(null);
        alert.setAnomalyType("CHANGE_POINT");
        alert.setSeverity("YELLOW");
        alert.setDimension("sales");
        alert.setAnomalyDate("2025-01-10");
        alert.setActualValue(120.0);
        alert.setPredictedValue(100.0);
        alert.setDeviationPercent(20.0);

        Dataset dataset = new Dataset();
        dataset.setId(10L);
        dataset.setSchemaJson("{\"date\":\"DATE\",\"sales\":\"NUMERIC\",\"region\":\"STRING\"}");

        when(alertMapper.selectById(1L)).thenReturn(alert);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildRichRows());
        when(rcaMapper.insert(any())).thenAnswer(inv -> {
            RootCauseAnalysis r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });

        RootCauseAnalysis rca = service.analyze(1L);
        assertNotNull(rca);
    }

    @Test
    @DisplayName("analyze - 使用 fields 格式 schemaJson")
    void analyze_fieldsFormatSchema() {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId(1L);
        alert.setDatasetId(10L);
        alert.setAnomalyType("OUT_OF_BOUNDS");
        alert.setSeverity("ORANGE");
        alert.setDimension("sales");
        alert.setAnomalyDate("2025-01-10");
        alert.setActualValue(150.0);
        alert.setPredictedValue(100.0);
        alert.setDeviationPercent(50.0);

        Dataset dataset = new Dataset();
        dataset.setId(10L);
        dataset.setSchemaJson("{\"fields\":[{\"name\":\"date\",\"type\":\"date\"},{\"name\":\"sales\",\"type\":\"number\"},{\"name\":\"region\",\"type\":\"string\"}]}");

        when(alertMapper.selectById(1L)).thenReturn(alert);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildRichRows());
        when(rcaMapper.insert(any())).thenAnswer(inv -> {
            RootCauseAnalysis r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });

        RootCauseAnalysis rca = service.analyze(1L);
        assertNotNull(rca);
    }

    @Test
    @DisplayName("getById - 查询根因分析")
    void getById_found() {
        RootCauseAnalysis rca = new RootCauseAnalysis();
        rca.setId(1L);
        when(rcaMapper.selectById(1L)).thenReturn(rca);

        RootCauseAnalysis result = service.getById(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getById - 返回 null")
    void getById_notFound() {
        when(rcaMapper.selectById(anyLong())).thenReturn(null);
        RootCauseAnalysis result = service.getById(999L);
        assertNull(result);
    }

    @Test
    @DisplayName("drillDown - 数据集不存在抛异常")
    void drillDown_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.drillDown(1L, "region", "north", "sales", "date", null));
    }

    @Test
    @DisplayName("drillDown - 成功下钻")
    void drillDown_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setSchemaJson("{\"date\":\"DATE\",\"sales\":\"NUMERIC\",\"region\":\"STRING\"}");

        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildRichRows());

        Map<String, Object> result = service.drillDown(
                1L, "region", "north", "sales", "date", null);
        assertNotNull(result);
        assertEquals("region", result.get("dimension"));
        assertEquals("north", result.get("dimensionValue"));
        assertNotNull(result.get("totalRecords"));
        assertNotNull(result.get("totalValue"));
        assertNotNull(result.get("timeSeries"));
    }

    @Test
    @DisplayName("getContributionBreakdown - 数据集不存在抛异常")
    void getContributionBreakdown_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.getContributionBreakdown(1L, "sales", "date", "5"));
    }

    @Test
    @DisplayName("getContributionBreakdown - 成功获取贡献度")
    void getContributionBreakdown_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setSchemaJson("{\"date\":\"DATE\",\"sales\":\"NUMERIC\",\"region\":\"STRING\"}");

        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildRichRows());

        List<Map<String, Object>> result = service.getContributionBreakdown(1L, "sales", "date", "5");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
