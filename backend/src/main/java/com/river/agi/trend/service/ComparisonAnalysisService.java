package com.river.agi.trend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.prediction.service.PredictionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonAnalysisService {

    private final PredictionTaskMapper predictionTaskMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetDataReaderService dataReader;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getActualVsPredicted(Long predictionTaskId) {
        PredictionTask task = predictionTaskMapper.selectById(predictionTaskId);
        if (task == null) throw new BusinessException("预测任务不存在");

        List<PredictionData.SeriesPoint> history = loadHistorySeries(task);
        List<PredictionResult> predictions = predictionResultMapper.selectList(
                new LambdaQueryWrapper<PredictionResult>()
                        .eq(PredictionResult::getTaskId, predictionTaskId)
                        .orderByAsc(PredictionResult::getPredictionDate)
        );

        List<Map<String, Object>> series = new ArrayList<>();
        List<String> xAxis = new ArrayList<>();
        List<Double> actualData = new ArrayList<>();
        List<Double> predictedData = new ArrayList<>();
        List<Double> lowerBoundData = new ArrayList<>();
        List<Double> upperBoundData = new ArrayList<>();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (PredictionData.SeriesPoint p : history) {
            xAxis.add(p.date().format(fmt));
            actualData.add(Math.round(p.value() * 100.0) / 100.0);
            predictedData.add(null);
            lowerBoundData.add(null);
            upperBoundData.add(null);
        }

        for (PredictionResult pr : predictions) {
            LocalDate date = parseDate(pr.getPredictionDate());
            xAxis.add(pr.getPredictionDate());
            Double actualForDate = findActualValue(history, date);
            actualData.add(actualForDate != null ? Math.round(actualForDate * 100.0) / 100.0 : null);
            predictedData.add(Math.round(pr.getPredictedValue() * 100.0) / 100.0);
            lowerBoundData.add(pr.getLowerBound() != null ? Math.round(pr.getLowerBound() * 100.0) / 100.0 : null);
            upperBoundData.add(pr.getUpperBound() != null ? Math.round(pr.getUpperBound() * 100.0) / 100.0 : null);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("xAxis", xAxis);
        result.put("actual", actualData);
        result.put("predicted", predictedData);
        result.put("lowerBound", lowerBoundData);
        result.put("upperBound", upperBoundData);

        List<Map<String, Object>> deviations = new ArrayList<>();
        for (int i = 0; i < xAxis.size(); i++) {
            if (actualData.get(i) != null && predictedData.get(i) != null) {
                double actual = actualData.get(i);
                double predicted = predictedData.get(i);
                double deviation = actual != 0 ? Math.round((actual - predicted) / actual * 10000.0) / 100.0 : 0;
                Map<String, Object> dev = new LinkedHashMap<>();
                dev.put("date", xAxis.get(i));
                dev.put("actual", actual);
                dev.put("predicted", predicted);
                dev.put("deviationPercent", deviation);
                dev.put("withinBounds", lowerBoundData.get(i) == null || (actual >= lowerBoundData.get(i) && actual <= upperBoundData.get(i)));
                deviations.add(dev);
            }
        }
        result.put("deviations", deviations);

        double avgDeviation = deviations.stream()
                .mapToDouble(d -> Math.abs((Double) d.get("deviationPercent")))
                .average().orElse(0);
        long withinBoundsCount = deviations.stream().filter(d -> Boolean.TRUE.equals(d.get("withinBounds"))).count();
        result.put("summary", Map.of(
                "averageDeviationPercent", Math.round(avgDeviation * 100.0) / 100.0,
                "withinBoundsRate", deviations.isEmpty() ? 100 : Math.round((double) withinBoundsCount / deviations.size() * 10000.0) / 100.0,
                "comparedPoints", deviations.size()
        ));

        return result;
    }

    public Map<String, Object> getYearOverYear(Long datasetId, String timeField, String targetField, String currentPeriod) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<PredictionData.SeriesPoint> series = loadSeries(dataset, timeField, targetField);
        Map<String, Double> byMonth = series.stream()
                .collect(Collectors.groupingBy(
                        p -> String.format("%02d", p.date().getMonthValue()),
                        Collectors.summingDouble(PredictionData.SeriesPoint::value)
                ));

        Map<String, Double> byYearMonth = series.stream()
                .collect(Collectors.groupingBy(
                        p -> p.date().getYear() + "-" + String.format("%02d", p.date().getMonthValue()),
                        Collectors.summingDouble(PredictionData.SeriesPoint::value)
                ));

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> yoyData = new ArrayList<>();
        for (Map.Entry<String, Double> entry : byYearMonth.entrySet()) {
            String[] parts = entry.getKey().split("-");
            int year = Integer.parseInt(parts[0]);
            String month = parts[1];
            String lastYearKey = (year - 1) + "-" + month;
            Double lastYearValue = byYearMonth.get(lastYearKey);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("period", entry.getKey());
            item.put("value", Math.round(entry.getValue() * 100.0) / 100.0);
            item.put("lastYearValue", lastYearValue != null ? Math.round(lastYearValue * 100.0) / 100.0 : null);
            if (lastYearValue != null && lastYearValue != 0) {
                item.put("yoyPercent", Math.round((entry.getValue() - lastYearValue) / lastYearValue * 10000.0) / 100.0);
            } else {
                item.put("yoyPercent", null);
            }
            yoyData.add(item);
        }
        yoyData.sort(Comparator.comparing(m -> (String) m.get("period")));

        result.put("yoyData", yoyData);
        result.put("type", "YoY");
        return result;
    }

    public Map<String, Object> getMonthOverMonth(Long datasetId, String timeField, String targetField) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<PredictionData.SeriesPoint> series = loadSeries(dataset, timeField, targetField);
        series.sort(Comparator.comparing(PredictionData.SeriesPoint::date));

        List<Map<String, Object>> momData = new ArrayList<>();
        Double prevValue = null;
        for (PredictionData.SeriesPoint p : series) {
            Map<String, Object> item = new LinkedHashMap<>();
            double currentValue = p.value();
            item.put("date", p.date().toString());
            item.put("value", Math.round(currentValue * 100.0) / 100.0);
            if (prevValue != null && prevValue != 0) {
                item.put("momPercent", Math.round((currentValue - prevValue) / prevValue * 10000.0) / 100.0);
            }
            momData.add(item);
            prevValue = currentValue;
        }
        return Map.of("momData", momData, "type", "MoM");
    }

    public Map<String, Object> getMultiAlgorithmComparison(Long datasetId, String timeField, String targetField, int forecastDays) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new BusinessException("Dataset not found");

        List<PredictionData.SeriesPoint> series = loadSeries(dataset, timeField, targetField);
        if (series.size() < 10) {
            throw new BusinessException("数据量不足，无法进行多算法对比（至少需要10个数据点）");
        }

        String[] algorithms = {"MOVING_AVERAGE", "EXPONENTIAL_SMOOTHING", "LINEAR_REGRESSION"};
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> xAxis = series.stream().map(p -> p.date().toString()).collect(Collectors.toList());
        Map<String, List<Double>> algorithmPredictions = new LinkedHashMap<>();
        List<Map<String, Object>> algorithmMetrics = new ArrayList<>();

        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        double meanValue = Arrays.stream(values).average().orElse(0);

        for (String algo : algorithms) {
            List<Double> predictions = new ArrayList<>();
            double mae = 0, rmse = 0, mape = 0;
            int testSize = Math.min(forecastDays, values.length / 3);

            for (int i = 0; i < values.length; i++) {
                if (i < values.length - testSize) {
                    predictions.add(null);
                } else {
                    double predicted = predictWithAlgorithm(algo, values, i, values.length - testSize);
                    predictions.add(Math.round(predicted * 100.0) / 100.0);
                    double error = values[i] - predicted;
                    mae += Math.abs(error);
                    rmse += error * error;
                    if (values[i] != 0) mape += Math.abs(error / values[i]);
                }
            }

            mae /= testSize;
            rmse = Math.sqrt(rmse / testSize);
            mape = mape / testSize * 100;

            algorithmPredictions.put(algo, predictions);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("algorithm", algo);
            metrics.put("algorithmName", getAlgorithmName(algo));
            metrics.put("mae", Math.round(mae * 100.0) / 100.0);
            metrics.put("rmse", Math.round(rmse * 100.0) / 100.0);
            metrics.put("mape", Math.round(mape * 100.0) / 100.0);
            algorithmMetrics.add(metrics);
        }

        algorithmMetrics.sort(Comparator.comparingDouble(m -> (Double) m.get("rmse")));

        result.put("xAxis", xAxis);
        result.put("actualValues", series.stream().map(p -> Math.round(p.value() * 100.0) / 100.0).collect(Collectors.toList()));
        result.put("algorithmPredictions", algorithmPredictions);
        result.put("algorithmMetrics", algorithmMetrics);
        result.put("bestAlgorithm", algorithmMetrics.get(0).get("algorithm"));
        return result;
    }

    private double predictWithAlgorithm(String algorithm, double[] values, int index, int trainEnd) {
        double[] train = Arrays.copyOfRange(values, 0, trainEnd);
        return switch (algorithm) {
            case "MOVING_AVERAGE" -> {
                int window = Math.min(7, train.length);
                double sum = 0;
                for (int i = train.length - window; i < train.length; i++) sum += train[i];
                double avg = sum / window;
                yield avg;
            }
            case "EXPONENTIAL_SMOOTHING" -> {
                double alpha = 0.3;
                double smooth = train[0];
                for (int i = 1; i < train.length; i++) smooth = alpha * train[i] + (1 - alpha) * smooth;
                yield smooth;
            }
            case "LINEAR_REGRESSION" -> {
                int n = train.length;
                double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
                for (int i = 0; i < n; i++) {
                    sumX += i; sumY += train[i]; sumXY += i * train[i]; sumX2 += i * i;
                }
                double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
                double intercept = (sumY - slope * sumX) / n;
                yield intercept + slope * index;
            }
            default -> mean(train);
        };
    }

    private String getAlgorithmName(String algo) {
        return switch (algo) {
            case "MOVING_AVERAGE" -> "移动平均法";
            case "EXPONENTIAL_SMOOTHING" -> "指数平滑法";
            case "LINEAR_REGRESSION" -> "线性回归法";
            case "HOLT_WINTERS" -> "Holt-Winters季节预测";
            default -> algo;
        };
    }

    private double mean(double[] arr) {
        return Arrays.stream(arr).average().orElse(0);
    }

    private List<PredictionData.SeriesPoint> loadHistorySeries(PredictionTask task) {
        return loadSeries(datasetMapper.selectById(task.getDatasetId()), task.getTimeField(), task.getTargetField());
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

    private Double findActualValue(List<PredictionData.SeriesPoint> series, LocalDate date) {
        if (date == null) return null;
        return series.stream()
                .filter(p -> p.date().equals(date))
                .findFirst()
                .map(PredictionData.SeriesPoint::value)
                .orElse(null);
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
