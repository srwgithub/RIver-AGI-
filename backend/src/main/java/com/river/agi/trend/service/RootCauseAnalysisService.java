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
import com.river.agi.trend.entity.RootCauseAnalysis;
import com.river.agi.trend.mapper.AnomalyAlertMapper;
import com.river.agi.trend.mapper.RootCauseAnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RootCauseAnalysisService {

    private final RootCauseAnalysisMapper rcaMapper;
    private final AnomalyAlertMapper alertMapper;
    private final PredictionTaskMapper taskMapper;
    private final PredictionResultMapper resultMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetDataReaderService dataReader;
    private final ObjectMapper objectMapper;

    public RootCauseAnalysis analyze(Long alertId) {
        AnomalyAlert alert = alertMapper.selectById(alertId);
        if (alert == null) throw new BusinessException("告警不存在");

        Dataset dataset = datasetMapper.selectById(alert.getDatasetId());
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> rows = dataReader.readRows(dataset);
        PredictionTask task = alert.getPredictionTaskId() != null ? taskMapper.selectById(alert.getPredictionTaskId()) : null;

        RootCauseAnalysis rca = new RootCauseAnalysis();
        rca.setAnomalyAlertId(alertId);
        rca.setPredictionTaskId(alert.getPredictionTaskId());
        rca.setDatasetId(alert.getDatasetId());
        rca.setAnalysisType(alert.getAnomalyType());
        rca.setTargetMetric(alert.getDimension());
        rca.setImpactValue(alert.getActualValue() - alert.getPredictedValue());
        rca.setImpactPercent(alert.getDeviationPercent());
        rca.setTenantId(1L);
        rca.setCreatedAt(java.time.LocalDateTime.now());

        Map<String, List<String>> schema = parseSchema(dataset);
        List<String> numericFields = schema.getOrDefault("numeric", new ArrayList<>());
        List<String> categoricalFields = schema.getOrDefault("categorical", new ArrayList<>());

        List<Map<String, Object>> topContributors = new ArrayList<>();
        double totalValue = calculateTotalValue(rows, alert.getDimension());
        double targetTotal = totalValue;

        String targetField = task != null ? task.getTargetField() : alert.getDimension();
        String timeField = task != null ? task.getTimeField() : null;
        String anomalyDate = alert.getAnomalyDate();

        for (String dim : categoricalFields) {
            if (targetField != null && dim.equals(targetField)) continue;
            if (timeField != null && dim.equals(timeField)) continue;

            Map<String, Double> dimensionContribution = calculateDimensionContribution(rows, dim, targetField, anomalyDate, timeField);
            if (dimensionContribution.isEmpty()) continue;

            List<Map.Entry<String, Double>> sorted = dimensionContribution.entrySet().stream()
                    .sorted((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())))
                    .limit(3)
                    .toList();

            for (Map.Entry<String, Double> entry : sorted) {
                double pct = totalValue != 0 ? Math.round(entry.getValue() / totalValue * 10000.0) / 100.0 : 0;
                Map<String, Object> contributor = new LinkedHashMap<>();
                contributor.put("dimension", dim);
                contributor.put("value", entry.getKey());
                contributor.put("contributionValue", Math.round(entry.getValue() * 100.0) / 100.0);
                contributor.put("contributionPercent", pct);
                topContributors.add(contributor);
            }
        }

        topContributors.sort((a, b) -> Double.compare(
                Math.abs((Double) b.get("contributionPercent")),
                Math.abs((Double) a.get("contributionPercent"))
        ));
        topContributors = topContributors.stream().limit(5).collect(Collectors.toList());

        List<Map<String, Object>> factorCorrelations = new ArrayList<>();
        if (targetField != null) {
            for (String numField : numericFields) {
                if (numField.equals(targetField)) continue;
                double corr = calculateCorrelation(rows, numField, targetField);
                if (!Double.isNaN(corr) && Math.abs(corr) > 0.3) {
                    Map<String, Object> factor = new LinkedHashMap<>();
                    factor.put("field", numField);
                    factor.put("correlation", Math.round(corr * 1000.0) / 1000.0);
                    factor.put("strength", Math.abs(corr) > 0.7 ? "STRONG" : Math.abs(corr) > 0.5 ? "MODERATE" : "WEAK");
                    factor.put("direction", corr > 0 ? "POSITIVE" : "NEGATIVE");
                    factorCorrelations.add(factor);
                }
            }
            factorCorrelations.sort((a, b) -> Double.compare(
                    Math.abs((Double) b.get("correlation")),
                    Math.abs((Double) a.get("correlation"))
            ));
        }

        List<String> recommendations = generateRecommendations(alert, topContributors, factorCorrelations);

        rca.setTopContributorsJson(safeJson(topContributors));
        rca.setFactorsJson(safeJson(factorCorrelations));
        rca.setRecommendationsJson(safeJson(recommendations));
        rca.setAnalysisSummary(generateSummary(alert, topContributors, factorCorrelations));

        rcaMapper.insert(rca);
        return rca;
    }

    public RootCauseAnalysis getById(Long id) {
        return rcaMapper.selectById(id);
    }

    public Map<String, Object> drillDown(Long datasetId, String dimension, String dimensionValue,
                                          String targetField, String timeField, String dateRange) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> rows = dataReader.readRows(dataset);
        List<Map<String, String>> filtered = rows.stream()
                .filter(r -> dimensionValue == null || dimensionValue.equals(r.get(dimension)))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", dimension);
        result.put("dimensionValue", dimensionValue);
        result.put("totalRecords", filtered.size());

        double sum = filtered.stream()
                .mapToDouble(r -> parseDouble(r.get(targetField)))
                .filter(v -> !Double.isNaN(v))
                .sum();
        result.put("totalValue", Math.round(sum * 100.0) / 100.0);
        result.put("averageValue", filtered.isEmpty() ? 0 :
                Math.round(sum / filtered.size() * 100.0) / 100.0);

        Map<String, Double> byTime = new LinkedHashMap<>();
        if (timeField != null) {
            byTime = filtered.stream()
                    .filter(r -> r.get(timeField) != null)
                    .collect(Collectors.groupingBy(
                            r -> normalizeDate(r.get(timeField)),
                            Collectors.summingDouble(r -> parseDouble(r.get(targetField)))
                    ));
        }
        result.put("timeSeries", byTime.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", e.getKey());
                    item.put("value", Math.round(((Number) e.getValue()).doubleValue() * 100.0) / 100.0);
                    return item;
                })
                .collect(Collectors.toList()));

        Map<String, List<String>> schema = parseSchema(dataset);
        List<String> categoricalFields = schema.getOrDefault("categorical", new ArrayList<>());
        for (String otherDim : categoricalFields) {
            if (otherDim.equals(dimension) || otherDim.equals(timeField)) continue;
            Map<String, Double> byOtherDim = filtered.stream()
                    .filter(r -> r.get(otherDim) != null)
                    .collect(Collectors.groupingBy(
                            r -> r.get(otherDim),
                            Collectors.summingDouble(r -> parseDouble(r.get(targetField)))
                    ));
            List<Map<String, Object>> topValues = byOtherDim.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", e.getKey());
                        item.put("value", Math.round(((Number) e.getValue()).doubleValue() * 100.0) / 100.0);
                        return item;
                    })
                    .collect(Collectors.toList());
            result.put("breakdownBy_" + otherDim, topValues);
        }
        return result;
    }

    public List<Map<String, Object>> getContributionBreakdown(Long datasetId, String targetField,
                                                               String timeField, String topN) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> rows = dataReader.readRows(dataset);
        Map<String, List<String>> schema = parseSchema(dataset);
        int limit = topN != null ? Integer.parseInt(topN) : 10;

        List<Map<String, Object>> breakdowns = new ArrayList<>();
        for (String dim : schema.getOrDefault("categorical", new ArrayList<>())) {
            if (dim.equals(timeField) || dim.equals(targetField)) continue;
            Map<String, Double> byDim = rows.stream()
                    .filter(r -> r.get(dim) != null)
                    .collect(Collectors.groupingBy(r -> r.get(dim),
                            Collectors.summingDouble(r -> parseDouble(r.get(targetField)))));

            double total = byDim.values().stream().mapToDouble(Double::doubleValue).sum();
            List<Map<String, Object>> topItems = byDim.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(limit)
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", e.getKey());
                        item.put("value", Math.round(((Number) e.getValue()).doubleValue() * 100.0) / 100.0);
                        item.put("percent", total != 0 ? Math.round(e.getValue() / total * 10000.0) / 100.0 : 0);
                        return item;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> dimBreakdown = new LinkedHashMap<>();
            dimBreakdown.put("dimension", dim);
            dimBreakdown.put("total", Math.round(total * 100.0) / 100.0);
            dimBreakdown.put("items", topItems);
            breakdowns.add(dimBreakdown);
        }
        return breakdowns;
    }

    private Map<String, List<String>> parseSchema(Dataset dataset) {
        Map<String, List<String>> result = new HashMap<>();
        List<String> numeric = new ArrayList<>();
        List<String> categorical = new ArrayList<>();
        try {
            Map<String, Object> schema = objectMapper.readValue(dataset.getSchemaJson(), Map.class);
            if (schema.containsKey("fields")) {
                List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
                for (Map<String, Object> f : fields) {
                    String name = (String) f.get("name");
                    String type = (String) f.getOrDefault("type", "string");
                    if ("number".equals(type) || "integer".equals(type) || "float".equals(type) || "double".equals(type)) {
                        numeric.add(name);
                    } else if (!"date".equals(type) && !"datetime".equals(type) && !"timestamp".equals(type)) {
                        categorical.add(name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse schema for dataset {}", dataset.getId(), e);
        }
        result.put("numeric", numeric);
        result.put("categorical", categorical);
        return result;
    }

    private Map<String, Double> calculateDimensionContribution(List<Map<String, String>> rows,
                                                                String dimension, String targetField,
                                                                String anomalyDate, String timeField) {
        Map<String, Double> contribution = new HashMap<>();
        List<Map<String, String>> anomalyRows = rows;
        if (anomalyDate != null && timeField != null) {
            anomalyRows = rows.stream()
                    .filter(r -> anomalyDate.equals(normalizeDate(r.get(timeField))))
                    .collect(Collectors.toList());
        }
        for (Map<String, String> row : anomalyRows) {
            String dimVal = row.get(dimension);
            if (dimVal == null) continue;
            double val = parseDouble(row.get(targetField));
            if (!Double.isNaN(val)) {
                contribution.merge(dimVal, val, Double::sum);
            }
        }
        double avgValue = anomalyRows.stream()
                .mapToDouble(r -> parseDouble(r.get(targetField)))
                .filter(v -> !Double.isNaN(v))
                .average().orElse(0);
        Map<String, Double> diffs = new HashMap<>();
        int expectedCountPerDim = Math.max(1, anomalyRows.size() / Math.max(contribution.size(), 1));
        for (Map.Entry<String, Double> entry : contribution.entrySet()) {
            double expected = avgValue * expectedCountPerDim;
            diffs.put(entry.getKey(), entry.getValue() - expected);
        }
        return diffs;
    }

    private double calculateCorrelation(List<Map<String, String>> rows, String fieldX, String fieldY) {
        List<double[]> pairs = new ArrayList<>();
        for (Map<String, String> row : rows) {
            double x = parseDouble(row.get(fieldX));
            double y = parseDouble(row.get(fieldY));
            if (!Double.isNaN(x) && !Double.isNaN(y)) {
                pairs.add(new double[]{x, y});
            }
        }
        if (pairs.size() < 5) return Double.NaN;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        int n = pairs.size();
        for (double[] p : pairs) {
            sumX += p[0]; sumY += p[1];
            sumXY += p[0] * p[1];
            sumX2 += p[0] * p[0];
            sumY2 += p[1] * p[1];
        }
        double denom = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return denom == 0 ? Double.NaN : (n * sumXY - sumX * sumY) / denom;
    }

    private double calculateTotalValue(List<Map<String, String>> rows, String field) {
        return rows.stream()
                .mapToDouble(r -> parseDouble(r.get(field)))
                .filter(v -> !Double.isNaN(v))
                .sum();
    }

    private double parseDouble(String val) {
        if (val == null || val.trim().isEmpty()) return Double.NaN;
        try { return Double.parseDouble(val.trim().replace(",", "")); }
        catch (Exception e) { return Double.NaN; }
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null) return null;
        String normalized = dateStr.trim().replace('/', '-').replace('.', '-');
        if (normalized.length() > 10) normalized = normalized.substring(0, 10);
        return normalized;
    }

    private List<String> generateRecommendations(AnomalyAlert alert,
                                                   List<Map<String, Object>> topContributors,
                                                   List<Map<String, Object>> factors) {
        List<String> recs = new ArrayList<>();
        if ("PREDICTION_DEVIATION".equals(alert.getAnomalyType()) || "OUT_OF_BOUNDS".equals(alert.getAnomalyType())) {
            boolean positive = alert.getActualValue() > alert.getPredictedValue();
            recs.add(positive ? "实际值超预期，建议分析增长驱动因素并评估是否持续" :
                    "实际值低于预期，建议及时排查市场因素和执行偏差");
            recs.add("建议重新训练模型以纳入最新数据，提升后续预测准确度");
        }
        if ("CHANGE_POINT".equals(alert.getAnomalyType())) {
            recs.add("检测到趋势结构性变化，建议评估是否存在外部事件影响（政策/竞品/季节性等）");
            recs.add("考虑启用事件干预模型，将突变点作为特殊因子纳入预测");
        }
        if (!topContributors.isEmpty()) {
            Map<String, Object> top = topContributors.get(0);
            recs.add(String.format("重点关注维度「%s」的「%s」，其贡献度为%.1f%%",
                    top.get("dimension"), top.get("value"), top.get("contributionPercent")));
        }
        if (!factors.isEmpty()) {
            Map<String, Object> topFactor = factors.get(0);
            recs.add(String.format("强关联因素「%s」（相关系数%.2f，%s相关），建议联动监控",
                    topFactor.get("field"), topFactor.get("correlation"),
                    "POSITIVE".equals(topFactor.get("direction")) ? "正" : "负"));
        }
        recs.add("建议建立预警机制，当偏差超过20%时自动通知相关负责人");
        return recs;
    }

    private String generateSummary(AnomalyAlert alert,
                                    List<Map<String, Object>> topContributors,
                                    List<Map<String, Object>> factors) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s在%s发生%s类型异常（%s级），",
                alert.getDimension(), alert.getAnomalyDate(), alert.getAnomalyType(), alert.getSeverity()));
        sb.append(String.format("实际值%.2f，预测值%.2f，偏差%.2f%%。",
                alert.getActualValue(), alert.getPredictedValue(), alert.getDeviationPercent()));
        if (!topContributors.isEmpty()) {
            sb.append("主要贡献维度：");
            for (int i = 0; i < Math.min(3, topContributors.size()); i++) {
                Map<String, Object> c = topContributors.get(i);
                sb.append(String.format("%s=%s(%.1f%%)", c.get("dimension"), c.get("value"), c.get("contributionPercent")));
                if (i < Math.min(2, topContributors.size() - 1)) sb.append("、");
            }
            sb.append("。");
        }
        if (!factors.isEmpty()) {
            sb.append(String.format("发现%d个强关联因素，最强关联为%s（r=%.2f）。",
                    factors.size(), factors.get(0).get("field"), factors.get(0).get("correlation")));
        }
        return sb.toString();
    }

    private String safeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }
}
