package com.river.agi.trend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OlapAnalysisService {

    private final DatasetMapper datasetMapper;
    private final DatasetDataReaderService dataReader;
    private final ObjectMapper objectMapper;

    public Map<String, Object> pivotTable(Long datasetId, String rowDim, String colDim,
                                           String valueField, String aggFunc) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> rows = dataReader.readRows(dataset);
        Map<String, Map<String, List<Double>>> pivot = new LinkedHashMap<>();
        Set<String> colValues = new TreeSet<>();

        for (Map<String, String> row : rows) {
            String rowKey = row.getOrDefault(rowDim, "(空)");
            String colKey = row.getOrDefault(colDim, "(空)");
            double val = parseDouble(row.get(valueField));
            if (Double.isNaN(val)) continue;
            colValues.add(colKey);
            pivot.computeIfAbsent(rowKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(colKey, k -> new ArrayList<>()).add(val);
        }

        List<String> headers = new ArrayList<>();
        headers.add(rowDim);
        headers.addAll(colValues);
        headers.add("合计");

        List<Map<String, Object>> data = new ArrayList<>();
        double grandTotal = 0;
        for (Map.Entry<String, Map<String, List<Double>>> entry : pivot.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(rowDim, entry.getKey());
            double rowTotal = 0;
            for (String col : colValues) {
                List<Double> values = entry.getValue().get(col);
                double agg = aggregate(values, aggFunc);
                row.put(col, Math.round(agg * 100.0) / 100.0);
                rowTotal += agg;
            }
            row.put("合计", Math.round(rowTotal * 100.0) / 100.0);
            grandTotal += rowTotal;
            data.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("data", data);
        result.put("grandTotal", Math.round(grandTotal * 100.0) / 100.0);
        result.put("aggFunc", aggFunc);
        return result;
    }

    public Map<String, Object> sliceAndDice(Long datasetId, String dimensions, String measures,
                                             String filters, String timeField) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> allRows = dataReader.readRows(dataset);
        List<String> dims = Arrays.asList(dimensions.split(","));
        List<String> meas = Arrays.asList(measures.split(","));
        Map<String, String> filterMap = parseFilters(filters);

        List<Map<String, String>> filtered = allRows.stream()
                .filter(r -> applyFilters(r, filterMap))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRecords", filtered.size());

        for (String dim : dims) {
            for (String measure : meas) {
                Map<String, Double> aggregated = filtered.stream()
                        .filter(r -> r.get(dim) != null)
                        .collect(Collectors.groupingBy(
                                r -> r.get(dim),
                                Collectors.summingDouble(r -> parseDouble(r.get(measure)))
                        ));

                List<Map<String, Object>> items = aggregated.entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .map(e -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("name", e.getKey());
                            item.put("value", Math.round(((Number) e.getValue()).doubleValue() * 100.0) / 100.0);
                            return item;
                        })
                        .collect(Collectors.toList());

                result.put(dim + "_" + measure, items);
            }
        }

        if (timeField != null && dims.contains(timeField) && !meas.isEmpty()) {
            String firstMeasure = meas.get(0);
            Map<String, Double> timeSeries = filtered.stream()
                    .filter(r -> r.get(timeField) != null)
                    .collect(Collectors.groupingBy(
                            r -> normalizeDate(r.get(timeField)),
                            Collectors.summingDouble(r -> parseDouble(r.get(firstMeasure)))
                    ));
            List<Map<String, Object>> tsList = timeSeries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("date", e.getKey());
                        item.put("value", Math.round(((Number) e.getValue()).doubleValue() * 100.0) / 100.0);
                        return item;
                    })
                    .collect(Collectors.toList());
            result.put("timeSeries_" + firstMeasure, tsList);
        }

        for (String measure : meas) {
            double total = filtered.stream()
                    .mapToDouble(r -> parseDouble(r.get(measure)))
                    .filter(v -> !Double.isNaN(v))
                    .sum();
            double avg = filtered.stream()
                    .mapToDouble(r -> parseDouble(r.get(measure)))
                    .filter(v -> !Double.isNaN(v))
                    .average().orElse(0);
            result.put(measure + "_total", Math.round(total * 100.0) / 100.0);
            result.put(measure + "_avg", Math.round(avg * 100.0) / 100.0);
        }
        return result;
    }

    public Map<String, Object> getPeriodComparison(Long datasetId, String timeField, String measure,
                                                    String currentStart, String currentEnd,
                                                    String compareType) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> rows = dataReader.readRows(dataset);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate currStart = LocalDate.parse(currentStart, fmt);
        LocalDate currEnd = LocalDate.parse(currentEnd, fmt);

        LocalDate prevStart, prevEnd;
        String label;
        long days = java.time.temporal.ChronoUnit.DAYS.between(currStart, currEnd) + 1;
        if ("YOY".equals(compareType)) {
            prevStart = currStart.minusYears(1);
            prevEnd = currEnd.minusYears(1);
            label = "同比";
        } else {
            prevStart = currStart.minusDays(days);
            prevEnd = currStart.minusDays(1);
            label = "环比";
        }

        double currentTotal = filterAndSum(rows, timeField, measure, currStart, currEnd);
        double prevTotal = filterAndSum(rows, timeField, measure, prevStart, prevEnd);
        double changePct = prevTotal != 0 ? Math.round((currentTotal - prevTotal) / prevTotal * 10000.0) / 100.0 : 0;

        List<Map<String, Object>> currentSeries = aggregateByPeriod(rows, timeField, measure, currStart, currEnd);
        List<Map<String, Object>> prevSeries = aggregateByPeriod(rows, timeField, measure, prevStart, prevEnd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("comparisonType", compareType);
        result.put("label", label);

        Map<String, Object> currentPeriodMap = new LinkedHashMap<>();
        currentPeriodMap.put("start", currentStart);
        currentPeriodMap.put("end", currentEnd);
        currentPeriodMap.put("total", Math.round(currentTotal * 100.0) / 100.0);
        result.put("currentPeriod", currentPeriodMap);

        Map<String, Object> previousPeriodMap = new LinkedHashMap<>();
        previousPeriodMap.put("start", prevStart.toString());
        previousPeriodMap.put("end", prevEnd.toString());
        previousPeriodMap.put("total", Math.round(prevTotal * 100.0) / 100.0);
        result.put("previousPeriod", previousPeriodMap);

        result.put("changePercent", changePct);
        result.put("changeDirection", currentTotal > prevTotal ? "UP" : currentTotal < prevTotal ? "DOWN" : "FLAT");
        result.put("currentSeries", currentSeries);
        result.put("previousSeries", prevSeries);
        return result;
    }

    public Map<String, Object> getKpiDashboard(Long datasetId, String measure, String timeField) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<Map<String, String>> rows = dataReader.readRows(dataset);
        List<Double> values = rows.stream()
                .map(r -> parseDouble(r.get(measure)))
                .filter(v -> !Double.isNaN(v))
                .sorted()
                .collect(Collectors.toList());

        double total = values.stream().mapToDouble(Double::doubleValue).sum();
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double median = values.isEmpty() ? 0 : values.get(values.size() / 2);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", Math.round(total * 100.0) / 100.0);
        result.put("average", Math.round(avg * 100.0) / 100.0);
        result.put("max", Math.round(max * 100.0) / 100.0);
        result.put("min", Math.round(min * 100.0) / 100.0);
        result.put("median", Math.round(median * 100.0) / 100.0);
        result.put("recordCount", values.size());

        if (!values.isEmpty()) {
            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            double meanVal = sum / values.size();
            double variance = values.stream().mapToDouble(v -> (v - meanVal) * (v - meanVal)).sum() / values.size();
            double stdDev = Math.sqrt(variance);
            result.put("stdDev", Math.round(stdDev * 100.0) / 100.0);
            result.put("cv", meanVal != 0 ? Math.round(stdDev / meanVal * 10000.0) / 100.0 : 0);

            double q1 = values.get((int)(values.size() * 0.25));
            double q3 = values.get((int)(values.size() * 0.75));
            result.put("q1", Math.round(q1 * 100.0) / 100.0);
            result.put("q3", Math.round(q3 * 100.0) / 100.0);
            result.put("iqr", Math.round((q3 - q1) * 100.0) / 100.0);
        }
        return result;
    }

    private double filterAndSum(List<Map<String, String>> rows, String timeField, String measure,
                                LocalDate start, LocalDate end) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return rows.stream()
                .filter(r -> {
                    LocalDate date = parseLocalDate(r.get(timeField));
                    return date != null && !date.isBefore(start) && !date.isAfter(end);
                })
                .mapToDouble(r -> parseDouble(r.get(measure)))
                .filter(v -> !Double.isNaN(v))
                .sum();
    }

    private List<Map<String, Object>> aggregateByPeriod(List<Map<String, String>> rows, String timeField,
                                                         String measure, LocalDate start, LocalDate end) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Double> byDate = new TreeMap<>();
        for (Map<String, String> row : rows) {
            LocalDate date = parseLocalDate(row.get(timeField));
            if (date == null || date.isBefore(start) || date.isAfter(end)) continue;
            double val = parseDouble(row.get(measure));
            if (Double.isNaN(val)) continue;
            byDate.merge(date.toString(), val, Double::sum);
        }
        return byDate.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", e.getKey());
                    item.put("value", Math.round(((Number) e.getValue()).doubleValue() * 100.0) / 100.0);
                    return item;
                })
                .collect(Collectors.toList());
    }

    private Map<String, String> parseFilters(String filters) {
        Map<String, String> map = new HashMap<>();
        if (filters == null || filters.isEmpty()) return map;
        for (String part : filters.split(";")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    private boolean applyFilters(Map<String, String> row, Map<String, String> filters) {
        for (Map.Entry<String, String> f : filters.entrySet()) {
            String val = row.get(f.getKey());
            if (val == null || !val.contains(f.getValue())) return false;
        }
        return true;
    }

    private double aggregate(List<Double> values, String func) {
        if (values == null || values.isEmpty()) return 0;
        return switch (func == null ? "SUM" : func.toUpperCase()) {
            case "AVG" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "COUNT" -> values.size();
            case "MAX" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case "MIN" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            default -> values.stream().mapToDouble(Double::doubleValue).sum();
        };
    }

    private double parseDouble(String val) {
        if (val == null || val.trim().isEmpty()) return Double.NaN;
        try { return Double.parseDouble(val.trim().replace(",", "")); }
        catch (Exception e) { return Double.NaN; }
    }

    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            String normalized = dateStr.trim().replace('/', '-').replace('.', '-');
            if (normalized.length() > 10) normalized = normalized.substring(0, 10);
            return LocalDate.parse(normalized);
        } catch (Exception e) { return null; }
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null) return null;
        String normalized = dateStr.trim().replace('/', '-').replace('.', '-');
        if (normalized.length() > 10) normalized = normalized.substring(0, 10);
        return normalized;
    }
}
