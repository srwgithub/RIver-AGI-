package com.river.agi.trend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.prediction.service.PredictionData;
import com.river.agi.trend.entity.DecisionScenario;
import com.river.agi.trend.mapper.DecisionScenarioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionSupportService {

    private final DecisionScenarioMapper scenarioMapper;
    private final PredictionTaskMapper taskMapper;
    private final PredictionResultMapper resultMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetDataReaderService dataReader;
    private final ObjectMapper objectMapper;

    public DecisionScenario createWhatIfScenario(Long predictionTaskId, String scenarioName,
                                                   Map<String, Double> adjustedFactors,
                                                   String assumptions) {
        PredictionTask task = taskMapper.selectById(predictionTaskId);
        if (task == null) throw new BusinessException("预测任务不存在");

        List<PredictionData.SeriesPoint> history = loadHistorySeries(task);
        double[] historyValues = history.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        double baseMean = Arrays.stream(historyValues).average().orElse(0);
        double baseGrowth = calculateRecentGrowth(historyValues);

        List<PredictionResult> basePredictions = resultMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PredictionResult>()
                        .eq(PredictionResult::getTaskId, predictionTaskId)
                        .orderByAsc(PredictionResult::getPredictionDate)
        );

        double adjustmentMultiplier = 1.0;
        for (Map.Entry<String, Double> factor : adjustedFactors.entrySet()) {
            adjustmentMultiplier *= (1 + factor.getValue());
        }

        List<Map<String, Object>> forecastResults = new ArrayList<>();
        double cumulativeGrowth = 0;
        for (int i = 0; i < basePredictions.size(); i++) {
            PredictionResult pr = basePredictions.get(i);
            double adjustedValue = pr.getPredictedValue() * adjustmentMultiplier;
            double timeFactor = 1 + baseGrowth * (i + 1) * (adjustmentMultiplier - 1);
            adjustedValue = adjustedValue * Math.max(0.5, Math.min(1.5, timeFactor));

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", pr.getPredictionDate());
            point.put("baseValue", Math.round(pr.getPredictedValue() * 100.0) / 100.0);
            point.put("adjustedValue", Math.round(adjustedValue * 100.0) / 100.0);
            point.put("delta", Math.round((adjustedValue - pr.getPredictedValue()) * 100.0) / 100.0);
            point.put("deltaPercent", pr.getPredictedValue() != 0 ?
                    Math.round((adjustedValue - pr.getPredictedValue()) / pr.getPredictedValue() * 10000.0) / 100.0 : 0);
            forecastResults.add(point);
            cumulativeGrowth += (adjustedValue - pr.getPredictedValue());
        }

        double expectedGrowth = basePredictions.isEmpty() ? 0 :
                Math.round(cumulativeGrowth / (basePredictions.get(0).getPredictedValue() * basePredictions.size()) * 10000.0) / 100.0;

        String riskLevel;
        double volatility = calculateVolatility(historyValues);
        if (volatility > 0.3 || Math.abs(adjustmentMultiplier - 1) > 0.3) riskLevel = "HIGH";
        else if (volatility > 0.15 || Math.abs(adjustmentMultiplier - 1) > 0.15) riskLevel = "MEDIUM";
        else riskLevel = "LOW";

        List<String> recommendations = generateActionRecommendations(adjustedFactors, expectedGrowth, riskLevel);

        DecisionScenario scenario = new DecisionScenario();
        scenario.setPredictionTaskId(predictionTaskId);
        scenario.setDatasetId(task.getDatasetId());
        scenario.setScenarioName(scenarioName);
        scenario.setScenarioType("WHAT_IF");
        // assumptions_json is a MySQL JSON column.  The API accepts a human-readable
        // string, so serialize it as a valid JSON string instead of inserting raw text.
        scenario.setAssumptionsJson(safeJson(assumptions == null ? "" : assumptions));
        scenario.setAdjustedFactorsJson(safeJson(adjustedFactors));
        scenario.setForecastResultsJson(safeJson(forecastResults));
        scenario.setExpectedGrowth(expectedGrowth);
        scenario.setRiskLevel(riskLevel);
        scenario.setActionRecommendationsJson(safeJson(recommendations));
        scenario.setTenantId(1L);
        scenario.setCreatedAt(LocalDateTime.now());
        scenarioMapper.insert(scenario);
        return scenario;
    }

    public Map<String, Object> generateThreeScenarios(Long predictionTaskId) {
        PredictionTask task = taskMapper.selectById(predictionTaskId);
        if (task == null) throw new BusinessException("预测任务不存在");

        List<PredictionResult> basePredictions = resultMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PredictionResult>()
                        .eq(PredictionResult::getTaskId, predictionTaskId)
                        .orderByAsc(PredictionResult::getPredictionDate)
        );

        if (basePredictions.isEmpty()) {
            throw new BusinessException("请先运行预测任务");
        }

        List<PredictionData.SeriesPoint> history = loadHistorySeries(task);
        double[] values = history.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        double volatility = calculateVolatility(values);
        double uncertaintyFactor = Math.min(0.3, volatility * 1.5);

        Map<String, Object> scenarios = new LinkedHashMap<>();
        scenarios.put("optimistic", buildScenario(basePredictions, 1 + uncertaintyFactor, "乐观情景",
                "市场环境向好，需求增长超预期，外部因素有利", "LOW"));
        scenarios.put("neutral", buildScenario(basePredictions, 1.0, "基准情景",
                "市场环境保持稳定，按当前趋势发展", "MEDIUM"));
        scenarios.put("pessimistic", buildScenario(basePredictions, 1 - uncertaintyFactor, "悲观情景",
                "市场环境恶化，需求下降，外部风险增加", "HIGH"));

        List<String> xAxis = basePredictions.stream().map(PredictionResult::getPredictionDate).collect(Collectors.toList());
        scenarios.put("xAxis", xAxis);
        return scenarios;
    }

    public Map<String, Object> getDecisionRecommendations(Long datasetId, String targetField) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> rows = dataReader.readRows(dataset);
        double[] values = rows.stream()
                .mapToDouble(r -> parseDouble(r.get(targetField)))
                .filter(v -> !Double.isNaN(v))
                .toArray();

        if (values.length < 10) {
            return Map.of("status", "INSUFFICIENT_DATA", "message", "数据量不足，无法生成决策建议");
        }

        double mean = Arrays.stream(values).average().orElse(0);
        double recentGrowth = calculateRecentGrowth(values);
        double volatility = calculateVolatility(values);

        List<Map<String, Object>> recommendations = new ArrayList<>();
        String overallTrend = recentGrowth > 0.02 ? "上升" : recentGrowth < -0.02 ? "下降" : "平稳";

        if ("上升".equals(overallTrend)) {
            recommendations.add(Map.of(
                    "category", "增长策略",
                    "priority", "HIGH",
                    "title", "抓住增长机遇",
                    "description", String.format("当前指标呈%s趋势（最近增长率%.1f%%），建议加大资源投入扩大市场份额。",
                            overallTrend, recentGrowth * 100),
                    "actions", Arrays.asList("增加产能/库存以应对需求增长", "加大营销投入强化增长势头", "拓展新客户群体")
            ));
        } else if ("下降".equals(overallTrend)) {
            recommendations.add(Map.of(
                    "category", "风险预警",
                    "priority", "HIGH",
                    "title", "应对下滑风险",
                    "description", String.format("当前指标呈%s趋势（最近变化率%.1f%%），建议采取措施扭转趋势。",
                            overallTrend, recentGrowth * 100),
                    "actions", Arrays.asList("分析下滑根因（使用根因分析功能）", "调整定价/促销策略", "优化产品/服务组合", "控制成本支出")
            ));
        } else {
            recommendations.add(Map.of(
                    "category", "维稳策略",
                    "priority", "MEDIUM",
                    "title", "维持稳定运营",
                    "description", String.format("当前指标相对平稳（波动率%.1f%%），建议优化运营效率。", volatility * 100),
                    "actions", Arrays.asList("关注效率提升和成本优化", "维持现有客户满意度", "探索新增长点")
            ));
        }

        if (volatility > 0.25) {
            recommendations.add(Map.of(
                    "category", "风险管理",
                    "priority", "HIGH",
                    "title", "降低波动风险",
                    "description", String.format("数据波动率较高（CV=%.1f%%），建议建立风险对冲机制。", volatility * 100),
                    "actions", Arrays.asList("建立安全库存/缓冲机制", "多元化收入来源", "建立预警阈值和应急预案")
            ));
        }

        recommendations.add(Map.of(
                "category", "预测优化",
                "priority", "MEDIUM",
                "title", "持续优化预测模型",
                "description", "建议定期更新模型以保持预测准确性。",
                "actions", Arrays.asList("每周/月重新训练预测模型", "监控预测偏差并及时调整", "引入更多外部特征提升准确度")
        ));

        Map<String, Object> goalAssessment = assessGoalProbability(values, recentGrowth, volatility);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallTrend", overallTrend);
        result.put("recentGrowthRate", Math.round(recentGrowth * 10000.0) / 100.0);
        result.put("volatilityLevel", volatility < 0.1 ? "LOW" : volatility < 0.25 ? "MEDIUM" : "HIGH");
        result.put("volatilityCoefficient", Math.round(volatility * 10000.0) / 100.0);
        result.put("goalAssessment", goalAssessment);
        result.put("recommendations", recommendations);
        return result;
    }

    public List<DecisionScenario> getScenarios(Long predictionTaskId) {
        return scenarioMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DecisionScenario>()
                        .eq(predictionTaskId != null, DecisionScenario::getPredictionTaskId, predictionTaskId)
                        .eq(DecisionScenario::getTenantId, 1L)
                        .orderByDesc(DecisionScenario::getCreatedAt)
        );
    }

    private Map<String, Object> buildScenario(List<PredictionResult> basePredictions, double factor,
                                                String name, String desc, String riskLevel) {
        List<Map<String, Object>> points = new ArrayList<>();
        double totalDelta = 0;
        for (PredictionResult pr : basePredictions) {
            double adjusted = pr.getPredictedValue() * factor;
            // Map.of rejects null values. Prediction intervals are optional for
            // some model types, so build this response with a null-tolerant map.
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", pr.getPredictionDate());
            point.put("value", Math.round(adjusted * 100.0) / 100.0);
            point.put("lowerBound", pr.getLowerBound() != null
                    ? Math.round(pr.getLowerBound() * factor * 100.0) / 100.0 : null);
            point.put("upperBound", pr.getUpperBound() != null
                    ? Math.round(pr.getUpperBound() * factor * 100.0) / 100.0 : null);
            points.add(point);
            totalDelta += (adjusted - pr.getPredictedValue());
        }
        double totalBase = basePredictions.stream().mapToDouble(PredictionResult::getPredictedValue).sum();
        double growth = totalBase != 0 ? Math.round(totalDelta / totalBase * 10000.0) / 100.0 : 0;

        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("name", name);
        scenario.put("description", desc);
        scenario.put("riskLevel", riskLevel);
        scenario.put("growthPercent", growth);
        scenario.put("data", points);
        return scenario;
    }

    private Map<String, Object> assessGoalProbability(double[] values, double growth, double volatility) {
        double recentAvg = 0;
        int window = Math.min(7, values.length);
        for (int i = values.length - window; i < values.length; i++) recentAvg += values[i];
        recentAvg /= window;

        double targetHigh = recentAvg * 1.2;
        double targetMid = recentAvg * 1.1;
        double targetLow = recentAvg;

        double probExceed = Math.max(0, Math.min(1, 0.5 + growth * 5 - volatility));
        double probMeet = Math.max(0, Math.min(1, 0.5 + growth * 10 - volatility * 0.5));

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("currentLevel", Math.round(recentAvg * 100.0) / 100.0);
        assessment.put("probabilityExceedTarget", Math.round(probExceed * 10000.0) / 100.0);
        assessment.put("probabilityMeetTarget", Math.round(probMeet * 10000.0) / 100.0);
        assessment.put("suggestedTarget", "建议设定目标为基准值的" + (growth > 0 ? "105%-115%" : "95%-105%"));
        return assessment;
    }

    private List<String> generateActionRecommendations(Map<String, Double> factors, double growth, String risk) {
        List<String> recs = new ArrayList<>();
        for (Map.Entry<String, Double> f : factors.entrySet()) {
            if (f.getValue() > 0) {
                recs.add(String.format("「%s」提升%.0f%%预计可带来%.1f%%的整体增长，建议重点推进",
                        f.getKey(), f.getValue() * 100, growth * 100));
            } else {
                recs.add(String.format("「%s」下降%.0f%%预计导致%.1f%%的整体下滑，需提前制定应对方案",
                        f.getKey(), Math.abs(f.getValue()) * 100, Math.abs(growth) * 100));
            }
        }
        if ("HIGH".equals(risk)) {
            recs.add("风险等级较高，建议分阶段试点验证后再全面推广");
            recs.add("建立监控机制，设置预警阈值");
        }
        recs.add("建议进行A/B测试验证假设");
        return recs;
    }

    private List<PredictionData.SeriesPoint> loadHistorySeries(PredictionTask task) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        List<PredictionData.SeriesPoint> points = new ArrayList<>();
        for (Map<String, String> row : dataReader.readRows(dataset)) {
            try {
                String dateStr = row.get(task.getTimeField());
                String valStr = row.get(task.getTargetField());
                if (dateStr == null || valStr == null) continue;
                LocalDate date = parseDate(dateStr);
                double value = Double.parseDouble(valStr.trim());
                if (!Double.isNaN(value) && date != null) {
                    points.add(new PredictionData.SeriesPoint(date, value));
                }
            } catch (Exception ignored) {}
        }
        points.sort(Comparator.comparing(PredictionData.SeriesPoint::date));
        return points;
    }

    private double calculateRecentGrowth(double[] values) {
        if (values.length < 7) return 0;
        int half = values.length / 2;
        double recent = 0, earlier = 0;
        for (int i = 0; i < half; i++) earlier += values[i];
        for (int i = values.length - half; i < values.length; i++) recent += values[i];
        earlier /= half;
        recent /= half;
        return earlier != 0 ? (recent - earlier) / earlier : 0;
    }

    private double calculateVolatility(double[] values) {
        if (values.length < 2) return 0;
        double m = Arrays.stream(values).average().orElse(0);
        if (m == 0) return 0;
        double sumSq = 0;
        for (double v : values) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / (values.length - 1)) / Math.abs(m);
    }

    private double parseDouble(String val) {
        if (val == null || val.trim().isEmpty()) return Double.NaN;
        try { return Double.parseDouble(val.trim().replace(",", "")); }
        catch (Exception e) { return Double.NaN; }
    }

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        try {
            String normalized = value.trim().replace('/', '-').replace('.', '-');
            if (normalized.length() > 10) normalized = normalized.substring(0, 10);
            String[] parts = normalized.split("-");
            if (parts.length != 3) return null;
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception e) { return null; }
    }

    private String safeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }
}
