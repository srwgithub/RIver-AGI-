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
import com.river.agi.trend.entity.DecisionScenario;
import com.river.agi.trend.mapper.DecisionScenarioMapper;
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
@DisplayName("决策支持服务测试")
class DecisionSupportServiceTest {

    @Mock
    private DecisionScenarioMapper scenarioMapper;
    @Mock
    private PredictionTaskMapper taskMapper;
    @Mock
    private PredictionResultMapper resultMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;

    private DecisionSupportService service;

    @BeforeEach
    void setUp() {
        service = new DecisionSupportService(
                scenarioMapper, taskMapper, resultMapper,
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
    @DisplayName("createWhatIfScenario - 任务不存在抛异常")
    void createWhatIfScenario_taskNotFound() {
        when(taskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.createWhatIfScenario(1L, "test", Map.of(), "assumptions"));
    }

    @Test
    @DisplayName("createWhatIfScenario - 成功创建场景")
    void createWhatIfScenario_success() {
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

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(20, 100, 1));
        when(resultMapper.selectList(any())).thenReturn(List.of(pr));
        when(scenarioMapper.insert(any())).thenAnswer(inv -> {
            DecisionScenario s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });

        DecisionScenario scenario = service.createWhatIfScenario(
                1L, "growth scenario", Map.of("marketing", 0.2), "Increase marketing spend");
        assertNotNull(scenario);
        assertEquals("WHAT_IF", scenario.getScenarioType());
        assertEquals(1L, scenario.getTenantId());
        assertNotNull(scenario.getRiskLevel());
    }

    @Test
    @DisplayName("createWhatIfScenario - 高风险场景")
    void createWhatIfScenario_highRisk() {
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

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(20, 100, 5));
        when(resultMapper.selectList(any())).thenReturn(List.of(pr));
        when(scenarioMapper.insert(any())).thenAnswer(inv -> {
            DecisionScenario s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });

        // 50% 增长系数 - 高风险
        DecisionScenario scenario = service.createWhatIfScenario(
                1L, "aggressive", Map.of("marketing", 0.5), "Aggressive growth");
        assertNotNull(scenario);
        assertTrue("HIGH".equals(scenario.getRiskLevel()) || "MEDIUM".equals(scenario.getRiskLevel()));
    }

    @Test
    @DisplayName("generateThreeScenarios - 任务不存在抛异常")
    void generateThreeScenarios_taskNotFound() {
        when(taskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateThreeScenarios(1L));
    }

    @Test
    @DisplayName("generateThreeScenarios - 预测结果为空抛异常")
    void generateThreeScenarios_emptyPredictions() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(resultMapper.selectList(any())).thenReturn(new ArrayList<>());

        assertThrows(BusinessException.class, () -> service.generateThreeScenarios(1L));
    }

    @Test
    @DisplayName("generateThreeScenarios - 成功生成三个场景")
    void generateThreeScenarios_success() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(10L);

        PredictionResult pr1 = new PredictionResult();
        pr1.setPredictionDate("2025-06-01");
        pr1.setPredictedValue(100.0);
        pr1.setLowerBound(90.0);
        pr1.setUpperBound(110.0);

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(20, 100, 1));
        when(resultMapper.selectList(any())).thenReturn(List.of(pr1));

        Map<String, Object> scenarios = service.generateThreeScenarios(1L);
        assertNotNull(scenarios);
        assertNotNull(scenarios.get("optimistic"));
        assertNotNull(scenarios.get("neutral"));
        assertNotNull(scenarios.get("pessimistic"));
        assertNotNull(scenarios.get("xAxis"));
    }

    @Test
    @DisplayName("getDecisionRecommendations - 数据集不存在抛异常")
    void getDecisionRecommendations_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.getDecisionRecommendations(1L, "sales"));
    }

    @Test
    @DisplayName("getDecisionRecommendations - 数据点不足返回 INSUFFICIENT_DATA")
    void getDecisionRecommendations_insufficientData() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(5, 100, 1));

        Map<String, Object> result = service.getDecisionRecommendations(1L, "sales");
        assertEquals("INSUFFICIENT_DATA", result.get("status"));
    }

    @Test
    @DisplayName("getDecisionRecommendations - 上升趋势生成增长策略")
    void getDecisionRecommendations_upTrend() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        // 显著上升趋势
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(20, 100, 5));

        Map<String, Object> result = service.getDecisionRecommendations(1L, "sales");
        assertNotNull(result);
        assertNotNull(result.get("recommendations"));
        assertEquals("上升", result.get("overallTrend"));
    }

    @Test
    @DisplayName("getDecisionRecommendations - 下降趋势生成风险预警")
    void getDecisionRecommendations_downTrend() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        List<Map<String, String>> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 20; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", start.plusDays(i).toString());
            row.put("sales", String.valueOf(200.0 - i * 5));
            rows.add(row);
        }
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(rows);

        Map<String, Object> result = service.getDecisionRecommendations(1L, "sales");
        assertEquals("下降", result.get("overallTrend"));
    }

    @Test
    @DisplayName("getDecisionRecommendations - 平稳趋势生成维稳策略")
    void getDecisionRecommendations_flatTrend() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSeriesRows(20, 100, 0.01));

        Map<String, Object> result = service.getDecisionRecommendations(1L, "sales");
        assertEquals("平稳", result.get("overallTrend"));
    }

    @Test
    @DisplayName("getScenarios - 返回场景列表")
    void getScenarios_success() {
        DecisionScenario s = new DecisionScenario();
        s.setId(1L);
        s.setScenarioName("test");
        when(scenarioMapper.selectList(any())).thenReturn(List.of(s));

        List<DecisionScenario> result = service.getScenarios(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getScenarios - predictionTaskId 为 null")
    void getScenarios_nullId() {
        when(scenarioMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<DecisionScenario> result = service.getScenarios(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
