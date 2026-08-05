package com.river.agi.trend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.river.agi.trend.entity.AnomalyAlert;
import com.river.agi.trend.mapper.AnomalyAlertMapper;
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
public class AnomalyDetectionService {

    private final AnomalyAlertMapper anomalyAlertMapper;
    private final PredictionTaskMapper predictionTaskMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetDataReaderService dataReader;
    private final ObjectMapper objectMapper;

    private static final double YELLOW_THRESHOLD = 0.10;
    private static final double ORANGE_THRESHOLD = 0.20;
    private static final double RED_THRESHOLD = 0.35;
    private static final double CHANGE_POINT_THRESHOLD = 2.0;

    public List<AnomalyAlert> detectPredictionDeviations(Long predictionTaskId) {
        PredictionTask task = predictionTaskMapper.selectById(predictionTaskId);
        if (task == null) throw new BusinessException("预测任务不存在");

        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        List<PredictionData.SeriesPoint> history = loadSeries(dataset, task.getTimeField(), task.getTargetField());
        Map<LocalDate, Double> actualMap = history.stream()
                .collect(Collectors.toMap(PredictionData.SeriesPoint::date, PredictionData.SeriesPoint::value, (a, b) -> b));

        List<PredictionResult> predictions = predictionResultMapper.selectList(
                new LambdaQueryWrapper<PredictionResult>()
                        .eq(PredictionResult::getTaskId, predictionTaskId)
                        .orderByAsc(PredictionResult::getPredictionDate)
        );

        // A forecast normally starts after the last historical date. In that
        // state there is no realized value to compare, so an empty result is a
        // valid business outcome rather than an error or a fabricated alert.
        boolean hasComparableValue = predictions.stream().anyMatch(pr -> {
            LocalDate date = parseDate(pr.getPredictionDate());
            return date != null && (pr.getActualValue() != null || actualMap.containsKey(date));
        });
        if (!hasComparableValue) return Collections.emptyList();

        List<AnomalyAlert> alerts = new ArrayList<>();
        for (PredictionResult pr : predictions) {
            LocalDate predDate = parseDate(pr.getPredictionDate());
            if (predDate == null || pr.getPredictedValue() == null) continue;
            // Prediction results may receive the realized value after the forecast
            // was created. Prefer that persisted value, then fall back to the
            // historical dataset for backfilled historical predictions.
            Double actual = pr.getActualValue() != null ? pr.getActualValue() : actualMap.get(predDate);
            if (actual == null) continue;

            double deviation = actual != 0 ? (actual - pr.getPredictedValue()) / actual : 0;
            double absDeviation = Math.abs(deviation);

            boolean withinBounds = pr.getLowerBound() == null ||
                    (actual >= pr.getLowerBound() && actual <= pr.getUpperBound());

            if (absDeviation > YELLOW_THRESHOLD || !withinBounds) {
                AnomalyAlert alert = new AnomalyAlert();
                alert.setPredictionTaskId(predictionTaskId);
                alert.setDatasetId(task.getDatasetId());
                alert.setAnomalyType(withinBounds ? "PREDICTION_DEVIATION" : "OUT_OF_BOUNDS");
                alert.setDimension(task.getTargetField());
                alert.setAnomalyDate(pr.getPredictionDate());
                alert.setActualValue(Math.round(actual * 100.0) / 100.0);
                alert.setPredictedValue(Math.round(pr.getPredictedValue() * 100.0) / 100.0);
                alert.setDeviationPercent(Math.round(deviation * 10000.0) / 100.0);
                alert.setExpectedLowerBound(pr.getLowerBound() != null ? Math.round(pr.getLowerBound() * 100.0) / 100.0 : null);
                alert.setExpectedUpperBound(pr.getUpperBound() != null ? Math.round(pr.getUpperBound() * 100.0) / 100.0 : null);
                alert.setSeverity(classifySeverity(absDeviation, withinBounds));
                alert.setDescription(generateDescription(alert, pr.getPredictedValue(), actual));
                alert.setStatus("OPEN");
                alert.setTenantId(1L);
                alert.setDetectedAt(LocalDateTime.now());
                anomalyAlertMapper.insert(alert);
                alerts.add(alert);
            }
        }
        return alerts;
    }

    public List<Map<String, Object>> detectChangePoints(Long datasetId, String timeField, String targetField) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<PredictionData.SeriesPoint> series = loadSeries(dataset, timeField, targetField);
        if (series.size() < 14) return Collections.emptyList();

        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        double[] changes = new double[values.length];
        List<Map<String, Object>> changePoints = new ArrayList<>();

        int window = Math.max(3, values.length / 20);
        double globalMean = Arrays.stream(values).average().orElse(0);
        double globalStd = stdDev(values, globalMean);

        for (int i = window; i < values.length - window; i++) {
            double beforeMean = 0, afterMean = 0;
            for (int j = 1; j <= window; j++) {
                beforeMean += values[i - j];
                afterMean += values[i + j];
            }
            beforeMean /= window;
            afterMean /= window;
            double change = globalStd > 0 ? (afterMean - beforeMean) / globalStd : 0;
            changes[i] = change;

            if (Math.abs(change) > CHANGE_POINT_THRESHOLD) {
                Map<String, Object> cp = new LinkedHashMap<>();
                cp.put("index", i);
                cp.put("date", series.get(i).date().toString());
                cp.put("value", Math.round(values[i] * 100.0) / 100.0);
                cp.put("beforeMean", Math.round(beforeMean * 100.0) / 100.0);
                cp.put("afterMean", Math.round(afterMean * 100.0) / 100.0);
                cp.put("changeMagnitude", Math.round(change * 100.0) / 100.0);
                cp.put("direction", change > 0 ? "JUMP_UP" : "JUMP_DOWN");
                cp.put("severity", Math.abs(change) > 3 ? "RED" : Math.abs(change) > 2.5 ? "ORANGE" : "YELLOW");

                AnomalyAlert alert = new AnomalyAlert();
                alert.setDatasetId(datasetId);
                alert.setAnomalyType("CHANGE_POINT");
                alert.setSeverity((String) cp.get("severity"));
                alert.setDimension(targetField);
                alert.setAnomalyDate(series.get(i).date().toString());
                alert.setActualValue((Double) cp.get("value"));
                alert.setPredictedValue(Math.round(beforeMean * 100.0) / 100.0);
                alert.setDeviationPercent(Math.round(change * 100.0) / 100.0);
                alert.setDescription(String.format("趋势突变点：%s出现%s，变化幅度%.2f个标准差",
                        series.get(i).date(), change > 0 ? "跃升" : "骤降", Math.abs(change)));
                alert.setStatus("OPEN");
                alert.setTenantId(1L);
                alert.setDetectedAt(LocalDateTime.now());
                anomalyAlertMapper.insert(alert);

                cp.put("alertId", alert.getId());
                changePoints.add(cp);
            }
        }
        return changePoints;
    }

    public List<Map<String, Object>> detectVolatilityAnomalies(Long datasetId, String timeField, String targetField) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<PredictionData.SeriesPoint> series = loadSeries(dataset, timeField, targetField);
        if (series.size() < 7) return Collections.emptyList();

        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        List<Map<String, Object>> anomalies = new ArrayList<>();
        int window = 7;

        double[] rollingMean = new double[values.length];
        double[] rollingStd = new double[values.length];

        for (int i = window; i < values.length; i++) {
            double[] slice = Arrays.copyOfRange(values, i - window, i);
            double mean = Arrays.stream(slice).average().orElse(0);
            double std = stdDev(slice, mean);
            rollingMean[i] = mean;
            rollingStd[i] = std;

            if (std > 0 && Math.abs(values[i] - mean) / std > 2.5) {
                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("date", series.get(i).date().toString());
                anomaly.put("value", Math.round(values[i] * 100.0) / 100.0);
                anomaly.put("rollingMean", Math.round(mean * 100.0) / 100.0);
                anomaly.put("zScore", Math.round(Math.abs(values[i] - mean) / std * 100.0) / 100.0);
                anomaly.put("type", values[i] > mean ? "SPIKE" : "DIP");
                anomaly.put("severity", Math.abs(values[i] - mean) / std > 3.5 ? "RED" : "ORANGE");
                anomalies.add(anomaly);
            }
        }
        return anomalies;
    }

    public List<AnomalyAlert> getAlerts(Long datasetId, Long predictionTaskId, String severity, String status) {
        LambdaQueryWrapper<AnomalyAlert> wrapper = new LambdaQueryWrapper<AnomalyAlert>()
                .orderByDesc(AnomalyAlert::getDetectedAt);
        if (datasetId != null) wrapper.eq(AnomalyAlert::getDatasetId, datasetId);
        if (predictionTaskId != null) wrapper.eq(AnomalyAlert::getPredictionTaskId, predictionTaskId);
        if (severity != null) wrapper.eq(AnomalyAlert::getSeverity, severity);
        if (status != null) wrapper.eq(AnomalyAlert::getStatus, status);
        wrapper.eq(AnomalyAlert::getTenantId, 1L);
        return anomalyAlertMapper.selectList(wrapper);
    }

    public AnomalyAlert resolveAlert(Long alertId, String resolution) {
        AnomalyAlert alert = anomalyAlertMapper.selectById(alertId);
        if (alert == null) throw new BusinessException("告警不存在");
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setRootCauseHint(resolution);
        anomalyAlertMapper.updateById(alert);
        return alert;
    }

    public Map<String, Object> getAlertSummary(Long datasetId) {
        List<AnomalyAlert> all = getAlerts(datasetId, null, null, null);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAlerts", all.size());
        summary.put("openAlerts", all.stream().filter(a -> "OPEN".equals(a.getStatus())).count());
        summary.put("resolvedAlerts", all.stream().filter(a -> "RESOLVED".equals(a.getStatus())).count());
        Map<String, Long> bySeverity = all.stream()
                .collect(Collectors.groupingBy(AnomalyAlert::getSeverity, Collectors.counting()));
        summary.put("bySeverity", bySeverity);
        Map<String, Long> byType = all.stream()
                .collect(Collectors.groupingBy(AnomalyAlert::getAnomalyType, Collectors.counting()));
        summary.put("byType", byType);
        return summary;
    }

    private String classifySeverity(double absDeviation, boolean withinBounds) {
        if (!withinBounds) {
            if (absDeviation > RED_THRESHOLD) return "RED";
            if (absDeviation > ORANGE_THRESHOLD) return "ORANGE";
        }
        if (absDeviation > RED_THRESHOLD) return "RED";
        if (absDeviation > ORANGE_THRESHOLD) return "ORANGE";
        return "YELLOW";
    }

    private String generateDescription(AnomalyAlert alert, double predicted, double actual) {
        String direction = actual > predicted ? "高于" : "低于";
        String severityText = switch (alert.getSeverity()) {
            case "RED" -> "严重";
            case "ORANGE" -> "较大";
            default -> "轻微";
        };
        return String.format("%s日实际值%s预测值%.2f%%，偏差%s。实际值=%.2f，预测值=%.2f，置信区间=[%s, %s]",
                alert.getAnomalyDate(), direction, Math.abs(alert.getDeviationPercent()), severityText,
                actual, predicted, formatBound(alert.getExpectedLowerBound()), formatBound(alert.getExpectedUpperBound()));
    }

    private String formatBound(Double value) {
        return value == null ? "未提供" : String.format("%.2f", value);
    }

    private double stdDev(double[] arr, double mean) {
        double sumSq = 0;
        for (double v : arr) sumSq += (v - mean) * (v - mean);
        return Math.sqrt(sumSq / Math.max(arr.length, 1));
    }

    private List<PredictionData.SeriesPoint> loadSeries(Dataset dataset, String timeField, String targetField) {
        List<PredictionData.SeriesPoint> points = new ArrayList<>();
        for (Map<String, String> row : dataReader.readRows(dataset)) {
            try {
                String dateStr = row.get(timeField);
                String valStr = row.get(targetField);
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

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        try {
            String normalized = value.trim().replace('/', '-').replace('.', '-');
            if (normalized.length() > 10) normalized = normalized.substring(0, 10);
            String[] parts = normalized.split("-");
            if (parts.length != 3) return null;
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return null;
        }
    }
}
