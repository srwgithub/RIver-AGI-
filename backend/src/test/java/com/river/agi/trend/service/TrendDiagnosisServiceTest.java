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
import com.river.agi.trend.entity.TrendDiagnosis;
import com.river.agi.trend.mapper.TrendDiagnosisMapper;
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
@DisplayName("趋势诊断服务测试")
class TrendDiagnosisServiceTest {

    @Mock
    private TrendDiagnosisMapper trendDiagnosisMapper;
    @Mock
    private PredictionTaskMapper predictionTaskMapper;
    @Mock
    private PredictionResultMapper predictionResultMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;

    private TrendDiagnosisService service;

    @BeforeEach
    void setUp() {
        service = new TrendDiagnosisService(
                trendDiagnosisMapper, predictionTaskMapper, predictionResultMapper,
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
    @DisplayName("diagnose - 任务不存在抛异常")
    void diagnose_taskNotFound() {
        when(predictionTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.diagnose(1L));
    }

    @Test
    @DisplayName("diagnose - 数据集不存在抛异常")
    void diagnose_datasetNotFound() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.diagnose(1L));
    }

    @Test
    @DisplayName("diagnose - 上升趋势")
    void diagnose_upTrend() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2025-03-01");
        pr.setPredictedValue(120.0);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(30, 100, 2));
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr));
        when(trendDiagnosisMapper.insert(any())).thenAnswer(inv -> {
            TrendDiagnosis d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        TrendDiagnosis result = service.diagnose(1L);
        assertNotNull(result);
        assertEquals("UP", result.getTrendDirection());
        assertNotNull(result.getTrendSlope());
        assertNotNull(result.getTrendStrength());
        assertNotNull(result.getRSquared());
        assertNotNull(result.getVolatilityLevel());
        assertNotNull(result.getTrendSummary());
        assertEquals(1L, result.getTenantId());
    }

    @Test
    @DisplayName("diagnose - 下降趋势")
    void diagnose_downTrend() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 30; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("sales", String.valueOf(200.0 - i * 3));
            rows.add(row);
        }

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(rows);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());
        when(trendDiagnosisMapper.insert(any())).thenAnswer(inv -> {
            TrendDiagnosis d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        TrendDiagnosis result = service.diagnose(1L);
        assertNotNull(result);
        assertEquals("DOWN", result.getTrendDirection());
    }

    @Test
    @DisplayName("diagnose - 平稳趋势")
    void diagnose_flatTrend() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(30, 100, 0));
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());
        when(trendDiagnosisMapper.insert(any())).thenAnswer(inv -> {
            TrendDiagnosis d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        TrendDiagnosis result = service.diagnose(1L);
        assertNotNull(result);
        // 完全平坦时 slope 为 0
        assertEquals("FLAT", result.getTrendDirection());
    }

    @Test
    @DisplayName("diagnose - 数据点不足14，季节性 INSUFFICIENT_DATA")
    void diagnose_insufficientForSeasonality() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(10, 100, 1));
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());
        when(trendDiagnosisMapper.insert(any())).thenAnswer(inv -> {
            TrendDiagnosis d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        TrendDiagnosis result = service.diagnose(1L);
        assertNotNull(result);
        assertEquals("INSUFFICIENT_DATA", result.getSeasonalityStatus());
    }

    @Test
    @DisplayName("getLatestDiagnosis - 找到结果")
    void getLatestDiagnosis_found() {
        TrendDiagnosis diagnosis = new TrendDiagnosis();
        diagnosis.setId(1L);
        diagnosis.setPredictionTaskId(5L);
        when(trendDiagnosisMapper.selectList(any())).thenReturn(List.of(diagnosis));

        TrendDiagnosis result = service.getLatestDiagnosis(5L);
        assertNotNull(result);
        assertEquals(5L, result.getPredictionTaskId());
    }

    @Test
    @DisplayName("getLatestDiagnosis - 没有结果返回 null")
    void getLatestDiagnosis_empty() {
        when(trendDiagnosisMapper.selectList(any())).thenReturn(new ArrayList<>());
        TrendDiagnosis result = service.getLatestDiagnosis(999L);
        assertNull(result);
    }
}
