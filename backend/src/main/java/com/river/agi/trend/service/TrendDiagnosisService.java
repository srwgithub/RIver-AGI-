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
import com.river.agi.trend.entity.TrendDiagnosis;
import com.river.agi.trend.mapper.TrendDiagnosisMapper;
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
public class TrendDiagnosisService {

    private final TrendDiagnosisMapper trendDiagnosisMapper;
    private final PredictionTaskMapper predictionTaskMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetDataReaderService dataReader;
    private final ObjectMapper objectMapper;

    public TrendDiagnosis diagnose(Long predictionTaskId) {
        PredictionTask task = predictionTaskMapper.selectById(predictionTaskId);
        if (task == null) {
            throw new BusinessException("预测任务不存在");
        }

        List<PredictionData.SeriesPoint> historySeries = loadHistorySeries(task);
        List<PredictionResult> predictions = predictionResultMapper.selectByTaskId(predictionTaskId);

        TrendDiagnosis diagnosis = new TrendDiagnosis();
        diagnosis.setPredictionTaskId(predictionTaskId);
        diagnosis.setDatasetId(task.getDatasetId());
        diagnosis.setTargetField(task.getTargetField());
        diagnosis.setTenantId(1L);

        double[] values = historySeries.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        double slope = calculateSlope(values);
        double rSquared = calculateRSquared(values, slope);
        double volatility = calculateVolatility(values);

        String direction;
        double strength;
        if (Math.abs(slope) < 0.001) {
            direction = "FLAT";
            strength = 1 - Math.abs(slope) / Math.max(Math.abs(values[values.length - 1] - values[0]) / values.length, 0.001);
        } else if (slope > 0) {
            direction = "UP";
            strength = Math.min(1.0, rSquared * (1 + Math.abs(slope) / Math.abs(mean(values))));
        } else {
            direction = "DOWN";
            strength = Math.min(1.0, rSquared * (1 + Math.abs(slope) / Math.abs(mean(values))));
        }
        strength = Math.round(strength * 100.0) / 100.0;

        diagnosis.setTrendDirection(direction);
        diagnosis.setTrendSlope(Math.round(slope * 10000.0) / 10000.0);
        diagnosis.setTrendStrength(strength);
        diagnosis.setRSquared(Math.round(rSquared * 1000.0) / 1000.0);

        detectSeasonality(historySeries, diagnosis);
        diagnosis.setVolatilityLevel(volatility < 0.1 ? "LOW" : volatility < 0.25 ? "MEDIUM" : "HIGH");
        diagnosis.setVolatilityCoefficient(Math.round(volatility * 1000.0) / 1000.0);

        List<Map<String, Object>> turningPoints = detectTurningPoints(historySeries);
        diagnosis.setTurningPointsJson(safeJson(turningPoints));

        Map<String, Object> decomposition = decompose(historySeries, diagnosis.getSeasonalPeriod() != null ? diagnosis.getSeasonalPeriod() : 7);
        diagnosis.setDecompositionJson(safeJson(decomposition));

        String summary = generateSummary(diagnosis, historySeries.size(), predictions.size());
        diagnosis.setTrendSummary(summary);

        trendDiagnosisMapper.insert(diagnosis);
        return diagnosis;
    }

    public TrendDiagnosis getLatestDiagnosis(Long predictionTaskId) {
        return trendDiagnosisMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TrendDiagnosis>()
                        .eq(TrendDiagnosis::getPredictionTaskId, predictionTaskId)
                        .orderByDesc(TrendDiagnosis::getCreatedAt)
                        .last("LIMIT 1")
        ).stream().findFirst().orElse(null);
    }

    private List<PredictionData.SeriesPoint> loadHistorySeries(PredictionTask task) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) throw new BusinessException("Dataset not found");

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

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace('/', '-').replace('.', '-');
        if (normalized.length() > 10) normalized = normalized.substring(0, 10);
        try {
            String[] parts = normalized.split("-");
            if (parts.length != 3) return null;
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return null;
        }
    }

    private double calculateSlope(double[] y) {
        int n = y.length;
        if (n < 2) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y[i];
            sumXY += i * y[i];
            sumX2 += i * i;
        }
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    private double calculateRSquared(double[] y, double slope) {
        int n = y.length;
        double intercept = mean(y) - slope * (n - 1) / 2.0;
        double ssTot = 0, ssRes = 0;
        double yMean = mean(y);
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * i;
            ssRes += (y[i] - predicted) * (y[i] - predicted);
            ssTot += (y[i] - yMean) * (y[i] - yMean);
        }
        return ssTot == 0 ? 1 : Math.max(0, 1 - ssRes / ssTot);
    }

    private double calculateVolatility(double[] y) {
        if (y.length < 2) return 0;
        double m = mean(y);
        if (m == 0) return 0;
        double sumSq = 0;
        for (double v : y) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / (y.length - 1)) / Math.abs(m);
    }

    private double mean(double[] arr) {
        return Arrays.stream(arr).average().orElse(0);
    }

    private void detectSeasonality(List<PredictionData.SeriesPoint> series, TrendDiagnosis diagnosis) {
        if (series.size() < 14) {
            diagnosis.setSeasonalityStatus("INSUFFICIENT_DATA");
            return;
        }
        int period = 7;
        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        double[] seasonalAvg = new double[period];
        int[] count = new int[period];
        double overallAvg = mean(values);
        for (int i = 0; i < values.length; i++) {
            seasonalAvg[i % period] += values[i];
            count[i % period]++;
        }
        double seasonVariance = 0;
        for (int i = 0; i < period; i++) {
            seasonalAvg[i] /= count[i];
            seasonVariance += (seasonalAvg[i] - overallAvg) * (seasonalAvg[i] - overallAvg);
        }
        double seasonalStrength = Math.sqrt(seasonVariance / period) / (Math.abs(overallAvg) + 1e-10);
        diagnosis.setSeasonalPeriod(period);
        diagnosis.setSeasonalStrength(Math.round(seasonalStrength * 1000.0) / 1000.0);
        if (seasonalStrength > 0.15) {
            diagnosis.setSeasonalityStatus("STRONG");
        } else if (seasonalStrength > 0.05) {
            diagnosis.setSeasonalityStatus("MODERATE");
        } else {
            diagnosis.setSeasonalityStatus("WEAK");
        }
    }

    private List<Map<String, Object>> detectTurningPoints(List<PredictionData.SeriesPoint> series) {
        List<Map<String, Object>> turningPoints = new ArrayList<>();
        if (series.size() < 5) return turningPoints;
        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        int window = Math.max(3, series.size() / 10);
        for (int i = window; i < values.length - window; i++) {
            double leftAvg = 0, rightAvg = 0;
            for (int j = 1; j <= window; j++) {
                leftAvg += values[i - j];
                rightAvg += values[i + j];
            }
            leftAvg /= window;
            rightAvg /= window;
            boolean isPeak = values[i] > leftAvg * 1.02 && values[i] > rightAvg * 1.02;
            boolean isValley = values[i] < leftAvg * 0.98 && values[i] < rightAvg * 0.98;
            if (isPeak || isValley) {
                Map<String, Object> tp = new LinkedHashMap<>();
                tp.put("index", i);
                tp.put("date", series.get(i).date().toString());
                tp.put("value", Math.round(values[i] * 100.0) / 100.0);
                tp.put("type", isPeak ? "PEAK" : "VALLEY");
                turningPoints.add(tp);
            }
        }
        return turningPoints;
    }

    private Map<String, Object> decompose(List<PredictionData.SeriesPoint> series, int period) {
        Map<String, Object> decomposition = new LinkedHashMap<>();
        if (series.size() < period * 2) {
            decomposition.put("note", "数据量不足，无法完成季节性分解");
            return decomposition;
        }
        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        int n = values.length;

        double[] trend = new double[n];
        int halfWindow = period / 2;
        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - halfWindow);
            int end = Math.min(n - 1, i + halfWindow);
            double sum = 0;
            for (int j = start; j <= end; j++) sum += values[j];
            trend[i] = sum / (end - start + 1);
        }

        double[] seasonal = new double[n];
        double[] periodAverages = new double[period];
        int[] periodCounts = new int[period];
        for (int i = 0; i < n; i++) {
            double detrended = values[i] - trend[i];
            periodAverages[i % period] += detrended;
            periodCounts[i % period]++;
        }
        double totalSeasonalAvg = 0;
        for (int i = 0; i < period; i++) {
            periodAverages[i] = periodCounts[i] > 0 ? periodAverages[i] / periodCounts[i] : 0;
            totalSeasonalAvg += periodAverages[i];
        }
        totalSeasonalAvg /= period;
        for (int i = 0; i < n; i++) {
            seasonal[i] = periodAverages[i % period] - totalSeasonalAvg;
        }

        double[] residual = new double[n];
        List<Map<String, Object>> trendPoints = new ArrayList<>();
        List<Map<String, Object>> seasonalPoints = new ArrayList<>();
        List<Map<String, Object>> residualPoints = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            residual[i] = values[i] - trend[i] - seasonal[i];
            String date = series.get(i).date().toString();
            trendPoints.add(Map.of("date", date, "value", Math.round(trend[i] * 100.0) / 100.0));
            seasonalPoints.add(Map.of("date", date, "value", Math.round(seasonal[i] * 100.0) / 100.0));
            residualPoints.add(Map.of("date", date, "value", Math.round(residual[i] * 100.0) / 100.0));
        }

        decomposition.put("trend", trendPoints);
        decomposition.put("seasonal", seasonalPoints);
        decomposition.put("residual", residualPoints);
        decomposition.put("period", period);
        return decomposition;
    }

    private String generateSummary(TrendDiagnosis d, int historySize, int forecastSize) {
        StringBuilder sb = new StringBuilder();
        String dirText = switch (d.getTrendDirection()) {
            case "UP" -> "上升趋势";
            case "DOWN" -> "下降趋势";
            default -> "平稳趋势";
        };
        sb.append(String.format("基于%d个历史数据点分析，目标指标呈%s（斜率=%.4f，趋势强度=%.2f，R²=%.3f）。",
                historySize, dirText, d.getTrendSlope(), d.getTrendStrength(), d.getRSquared()));

        if ("STRONG".equals(d.getSeasonalityStatus())) {
            sb.append(String.format("存在明显的%d天周期性（季节强度=%.3f）。", d.getSeasonalPeriod(), d.getSeasonalStrength()));
        } else if ("MODERATE".equals(d.getSeasonalityStatus())) {
            sb.append("存在弱到中等的周期性波动。");
        }

        sb.append(String.format("波动率%s（CV=%.3f）。",
                "LOW".equals(d.getVolatilityLevel()) ? "较低" : "MEDIUM".equals(d.getVolatilityLevel()) ? "中等" : "较高",
                d.getVolatilityCoefficient()));

        sb.append(String.format("已生成未来%d个时间点的预测。", forecastSize));
        return sb.toString();
    }

    private String safeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }
}
