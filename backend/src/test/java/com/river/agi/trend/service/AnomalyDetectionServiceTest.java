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
import com.river.agi.trend.entity.AnomalyAlert;
import com.river.agi.trend.mapper.AnomalyAlertMapper;
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
@DisplayName("异常检测服务测试")
class AnomalyDetectionServiceTest {

    @Mock
    private AnomalyAlertMapper anomalyAlertMapper;
    @Mock
    private PredictionTaskMapper predictionTaskMapper;
    @Mock
    private PredictionResultMapper predictionResultMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;

    private AnomalyDetectionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AnomalyDetectionService(
                anomalyAlertMapper, predictionTaskMapper, predictionResultMapper,
                datasetMapper, dataReader, objectMapper);
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
    @DisplayName("detectPredictionDeviations - 任务不存在抛异常")
    void detectPredictionDeviations_taskNotFound() {
        when(predictionTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.detectPredictionDeviations(1L));
    }

    @Test
    @DisplayName("detectPredictionDeviations - 实际值未填充跳过")
    void detectPredictionDeviations_noActual() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2025-06-01");
        pr.setPredictedValue(100.0);
        pr.setLowerBound(90.0);
        pr.setUpperBound(110.0);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(new ArrayList<>());
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(pr));

        List<AnomalyAlert> alerts = service.detectPredictionDeviations(1L);
        assertTrue(alerts.isEmpty());
        verify(anomalyAlertMapper, never()).insert(any());
    }

    @Test
    @DisplayName("detectPredictionDeviations - 偏差越界生成告警")
    void detectPredictionDeviations_deviationGeneratesAlert() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        // actual = 130, predicted = 100, deviation = 30% -> ORANGE/RED severity
        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2025-06-01");
        pr.setPredictedValue(100.0);
        pr.setActualValue(130.0);
        pr.setLowerBound(90.0);
        pr.setUpperBound(110.0);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(new ArrayList<>());
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(pr));
        when(anomalyAlertMapper.insert(any())).thenAnswer(inv -> {
            AnomalyAlert a = inv.getArgument(0);
            a.setId(1L);
            return 1;
        });

        List<AnomalyAlert> alerts = service.detectPredictionDeviations(1L);
        assertEquals(1, alerts.size());
        AnomalyAlert alert = alerts.get(0);
        assertEquals("OUT_OF_BOUNDS", alert.getAnomalyType());
        assertEquals(1L, alert.getTenantId());
        assertNotNull(alert.getDescription());
    }

    @Test
    @DisplayName("detectPredictionDeviations - 边界内但偏差大于阈值告警")
    void detectPredictionDeviations_withinBoundsButHighDeviation() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        // actual = 115, predicted = 100, bounds [80,120] -> within bounds, deviation 15% > 10% threshold
        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2025-06-01");
        pr.setPredictedValue(100.0);
        pr.setActualValue(115.0);
        pr.setLowerBound(80.0);
        pr.setUpperBound(120.0);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(new ArrayList<>());
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(pr));
        when(anomalyAlertMapper.insert(any())).thenAnswer(inv -> {
            AnomalyAlert a = inv.getArgument(0);
            a.setId(2L);
            return 1;
        });

        List<AnomalyAlert> alerts = service.detectPredictionDeviations(1L);
        assertEquals(1, alerts.size());
        assertEquals("PREDICTION_DEVIATION", alerts.get(0).getAnomalyType());
    }

    @Test
    @DisplayName("detectPredictionDeviations - 从历史数据中获取实际值")
    void detectPredictionDeviations_actualFromHistory() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        Map<String, String> row = new HashMap<>();
        row.put("date", "2025-06-01");
        row.put("sales", "150.0");

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2025-06-01");
        pr.setPredictedValue(100.0);
        pr.setLowerBound(90.0);
        pr.setUpperBound(110.0);
        pr.setActualValue(null);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(List.of(row));
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(pr));
        when(anomalyAlertMapper.insert(any())).thenAnswer(inv -> {
            AnomalyAlert a = inv.getArgument(0);
            a.setId(3L);
            return 1;
        });

        List<AnomalyAlert> alerts = service.detectPredictionDeviations(1L);
        assertEquals(1, alerts.size());
    }

    @Test
    @DisplayName("detectPredictionDeviations - 非法日期跳过")
    void detectPredictionDeviations_invalidDate() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("not-a-date");
        pr.setPredictedValue(100.0);
        pr.setActualValue(130.0);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(new ArrayList<>());
        when(predictionResultMapper.selectList(any())).thenReturn(List.of(pr));

        List<AnomalyAlert> alerts = service.detectPredictionDeviations(1L);
        assertTrue(alerts.isEmpty());
    }

    @Test
    @DisplayName("detectChangePoints - 数据集不存在抛异常")
    void detectChangePoints_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.detectChangePoints(1L, "date", "sales"));
    }

    @Test
    @DisplayName("detectChangePoints - 数据点不足14返回空")
    void detectChangePoints_insufficientData() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(10, 100, 1));
        List<Map<String, Object>> cps = service.detectChangePoints(1L, "date", "sales");
        assertNotNull(cps);
        assertTrue(cps.isEmpty());
    }

    @Test
    @DisplayName("detectChangePoints - 突变数据触发变化点")
    void detectChangePoints_detectJump() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        // 构造 30 个稳定点 + 5 个跃升点，使变化幅度 > 2 个标准差
        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 30; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("sales", String.valueOf(100.0));
            rows.add(row);
        }
        for (int i = 30; i < 35; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("sales", String.valueOf(1000.0));
            rows.add(row);
        }

        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(rows);
        when(anomalyAlertMapper.insert(any())).thenAnswer(inv -> {
            AnomalyAlert a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });

        List<Map<String, Object>> cps = service.detectChangePoints(1L, "date", "sales");
        assertNotNull(cps);
        // 应当检测到跃升点
        assertFalse(cps.isEmpty());
        Map<String, Object> cp = cps.get(0);
        assertEquals("JUMP_UP", cp.get("direction"));
    }

    @Test
    @DisplayName("detectVolatilityAnomalies - 数据集不存在抛异常")
    void detectVolatilityAnomalies_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.detectVolatilityAnomalies(1L, "date", "sales"));
    }

    @Test
    @DisplayName("detectVolatilityAnomalies - 数据点不足7返回空")
    void detectVolatilityAnomalies_insufficient() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(5, 100, 1));
        List<Map<String, Object>> anomalies = service.detectVolatilityAnomalies(1L, "date", "sales");
        assertNotNull(anomalies);
        assertTrue(anomalies.isEmpty());
    }

    @Test
    @DisplayName("detectVolatilityAnomalies - 检测到尖峰")
    void detectVolatilityAnomalies_spikeDetected() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        // 构造带有少量波动的基线数据，确保滚动标准差 > 0
        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        double[] baseValues = {100, 102, 98, 101, 99, 103, 97, 100, 102, 98, 101, 99, 103, 97, 100, 102, 98, 101, 99, 103};
        for (int i = 0; i < 20; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("sales", String.valueOf(baseValues[i]));
            rows.add(row);
        }
        // 在第 15 天插入一个明显尖峰
        rows.get(15).put("sales", "500.0");

        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(rows);

        List<Map<String, Object>> anomalies = service.detectVolatilityAnomalies(1L, "date", "sales");
        assertNotNull(anomalies);
        assertFalse(anomalies.isEmpty());
        Map<String, Object> anomaly = anomalies.get(0);
        assertEquals("SPIKE", anomaly.get("type"));
    }

    @Test
    @DisplayName("getAlerts - 各种过滤条件")
    void getAlerts_filters() {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId(1L);
        alert.setDatasetId(10L);
        when(anomalyAlertMapper.selectList(any())).thenReturn(List.of(alert));

        List<AnomalyAlert> result = service.getAlerts(10L, 5L, "RED", "OPEN");
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getAlerts - 全部为 null 不过滤")
    void getAlerts_noFilters() {
        when(anomalyAlertMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<AnomalyAlert> result = service.getAlerts(null, null, null, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("resolveAlert - 告警不存在抛异常")
    void resolveAlert_notFound() {
        when(anomalyAlertMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resolveAlert(1L, "test"));
    }

    @Test
    @DisplayName("resolveAlert - 成功解决告警")
    void resolveAlert_success() {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId(1L);
        alert.setStatus("OPEN");
        when(anomalyAlertMapper.selectById(1L)).thenReturn(alert);
        when(anomalyAlertMapper.updateById(any())).thenReturn(1);

        AnomalyAlert resolved = service.resolveAlert(1L, "handled");
        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals("handled", resolved.getRootCauseHint());
        assertNotNull(resolved.getResolvedAt());
        verify(anomalyAlertMapper).updateById(alert);
    }

    @Test
    @DisplayName("getAlertSummary - 各种状态分组")
    void getAlertSummary_variousStatus() {
        AnomalyAlert a1 = new AnomalyAlert();
        a1.setStatus("OPEN");
        a1.setSeverity("RED");
        a1.setAnomalyType("OUT_OF_BOUNDS");

        AnomalyAlert a2 = new AnomalyAlert();
        a2.setStatus("RESOLVED");
        a2.setSeverity("YELLOW");
        a2.setAnomalyType("PREDICTION_DEVIATION");

        when(anomalyAlertMapper.selectList(any())).thenReturn(List.of(a1, a2));
        Map<String, Object> summary = service.getAlertSummary(10L);
        assertNotNull(summary);
        assertEquals(2, summary.get("totalAlerts"));
        assertEquals(1L, summary.get("openAlerts"));
        assertEquals(1L, summary.get("resolvedAlerts"));
        assertNotNull(summary.get("bySeverity"));
        assertNotNull(summary.get("byType"));
    }

    @Test
    @DisplayName("getAlertSummary - 空告警列表")
    void getAlertSummary_empty() {
        when(anomalyAlertMapper.selectList(any())).thenReturn(new ArrayList<>());
        Map<String, Object> summary = service.getAlertSummary(10L);
        assertEquals(0, summary.get("totalAlerts"));
    }
}
