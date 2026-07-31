package com.river.agi.prediction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class LogisticRegressionClassifier implements ClassificationAlgorithm {

    private final ObjectMapper objectMapper;

    @Value("${prediction.classifier.logistic.window-size:5}")
    private int defaultWindowSize;

    @Value("${prediction.classifier.logistic.learning-rate:0.01}")
    private double learningRate;

    @Value("${prediction.classifier.logistic.max-iterations:1000}")
    private int maxIterations;

    @Value("${prediction.classifier.logistic.regularization:0.001}")
    private double regularization;

    public LogisticRegressionClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAlgorithmName() {
        return "逻辑回归分类器";
    }

    @Override
    public String getAlgorithmType() {
        return "LOGISTIC_REGRESSION_CLASSIFIER";
    }

    @Override
    public boolean supportsClassification() {
        return true;
    }

    @Override
    public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
        if (series == null || series.size() < 3) {
            throw new IllegalArgumentException("At least 3 data points are required for classification training");
        }

        int windowSize = resolveWindowSize(task);
        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        int n = values.length;

        int numClasses = determineNumClasses(values);
        double[] classBoundaries = computeClassBoundaries(values, numClasses);
        int[] classLabels = assignClasses(values, classBoundaries);

        int[][] windows = createSlidingWindows(values, windowSize);
        int[] windowLabels = createWindowLabels(classLabels, windowSize);
        int numSamples = windows.length;
        int numFeatures = windowSize;

        double[][] weights = new double[numClasses][numFeatures];
        double[] biases = new double[numClasses];

        for (int iter = 0; iter < maxIterations; iter++) {
            double[][] gradW = new double[numClasses][numFeatures];
            double[] gradB = new double[numClasses];

            for (int s = 0; s < numSamples; s++) {
                double[] x = new double[numFeatures];
                for (int f = 0; f < numFeatures; f++) {
                    x[f] = (double) windows[s][f];
                }

                double[] logits = new double[numClasses];
                for (int c = 0; c < numClasses; c++) {
                    logits[c] = dotProduct(weights[c], x) + biases[c];
                }
                double[] probs = softmax(logits);

                for (int c = 0; c < numClasses; c++) {
                    double error = probs[c] - (c == windowLabels[s] ? 1.0 : 0.0);
                    for (int f = 0; f < numFeatures; f++) {
                        gradW[c][f] += error * x[f];
                    }
                    gradB[c] += error;
                }
            }

            for (int c = 0; c < numClasses; c++) {
                for (int f = 0; f < numFeatures; f++) {
                    gradW[c][f] = gradW[c][f] / numSamples + regularization * weights[c][f];
                    weights[c][f] -= learningRate * gradW[c][f];
                }
                gradB[c] /= numSamples;
                biases[c] -= learningRate * gradB[c];
            }
        }

        List<List<Double>> weightList = new ArrayList<>();
        for (int c = 0; c < numClasses; c++) {
            weightList.add(toDoubleList(weights[c]));
        }

        List<Double> biasList = new ArrayList<>();
        for (double b : biases) biasList.add(b);

        int tp = 0, fp = 0, tn = 0, fn = 0;
        List<Double> probabilities = new ArrayList<>();
        int[] predictedLabels = new int[numSamples];

        for (int s = 0; s < numSamples; s++) {
            double[] x = new double[numFeatures];
            for (int f = 0; f < numFeatures; f++) {
                x[f] = (double) windows[s][f];
            }
            double[] logits = new double[numClasses];
            for (int c = 0; c < numClasses; c++) {
                logits[c] = dotProduct(weights[c], x) + biases[c];
            }
            double[] probs = softmax(logits);
            int predClass = argmax(probs);
            predictedLabels[s] = predClass;
            probabilities.add(probs[predClass]);

            int trueClass = windowLabels[s];
            if (numClasses == 2) {
                if (predClass == 1 && trueClass == 1) tp++;
                else if (predClass == 1 && trueClass == 0) fp++;
                else if (predClass == 0 && trueClass == 0) tn++;
                else fn++;
            }
        }

        double accuracy = numSamples > 0 ? (double) countCorrect(predictedLabels, windowLabels) / numSamples : 0;
        double precision = numClasses == 2 ? (tp + fp > 0 ? (double) tp / (tp + fp) : 0) : computeMultiClassPrecision(predictedLabels, windowLabels, numClasses);
        double recall = numClasses == 2 ? (tp + fn > 0 ? (double) tp / (tp + fn) : 0) : computeMultiClassRecall(predictedLabels, windowLabels, numClasses);
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double auc = numClasses == 2 ? calculateAUC(windowLabels, probabilities) : 0.5;

        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("logistic_regression_classifier_" + task.getDatasetId());
        modelVersion.setModelType(getAlgorithmType());
        modelVersion.setAlgorithmType(getAlgorithmType());
        modelVersion.setTaskType("CLASSIFICATION");
        modelVersion.setVersionNumber(1);
        modelVersion.setStatus("ACTIVE");
        modelVersion.setTrainingSamples((long) series.size());
        modelVersion.setPredictionTaskId(task.getId());
        modelVersion.setMae(round(1 - accuracy));
        modelVersion.setRmse(round(Math.sqrt(Math.max(0, 1 - accuracy))));
        modelVersion.setMape(round((1 - accuracy) * 100));
        modelVersion.setCreatedAt(LocalDateTime.now());

        Map<String, Object> modelData = new LinkedHashMap<>();
        modelData.put("weights", weightList);
        modelData.put("biases", biasList);
        modelData.put("numClasses", numClasses);
        modelData.put("windowSize", windowSize);
        modelData.put("classBoundaries", toDoubleList(classBoundaries));
        modelData.put("algorithm", getAlgorithmType());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("accuracy", round(accuracy));
        metrics.put("precision", round(precision));
        metrics.put("recall", round(recall));
        metrics.put("f1", round(f1));
        metrics.put("auc", round(auc));
        metrics.put("numClasses", numClasses);
        metrics.put("windowSize", windowSize);
        modelData.put("metrics", metrics);

        modelVersion.setTrainingMetricsJson(safeJson(modelData));

        List<Map<String, Object>> featureImportance = computeFeatureImportance(weights, numFeatures);
        modelVersion.setFeatureImportanceJson(safeJson(featureImportance));

        return modelVersion;
    }

    @Override
    public List<PredictionResult> predict(PredictionTask task,
                                           List<PredictionData.SeriesPoint> series,
                                           ModelVersion modelVersion,
                                           int forecastDays) {
        Map<String, Object> modelData = parseJson(modelVersion.getTrainingMetricsJson());
        int numClasses = modelData.get("numClasses") instanceof Number n ? n.intValue() : 2;
        int windowSize = modelData.get("windowSize") instanceof Number n ? n.intValue() : defaultWindowSize;
        double[] classBoundaries = toDoubleArray(modelData.get("classBoundaries"));

        List<?> weightList = (List<?>) modelData.get("weights");
        List<?> biasList = (List<?>) modelData.get("biases");
        double[][] weights = new double[numClasses][windowSize];
        double[] biases = new double[numClasses];

        for (int c = 0; c < numClasses; c++) {
            List<?> w = weightList.get(c) instanceof List<?> list ? list : List.of();
            for (int f = 0; f < windowSize && f < w.size(); f++) {
                weights[c][f] = w.get(f) instanceof Number n ? n.doubleValue() : 0.0;
            }
            biases[c] = biasList.get(c) instanceof Number n ? n.doubleValue() : 0.0;
        }

        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        LocalDate lastDate = series.get(series.size() - 1).date();

        double[] recentWindow = new double[windowSize];
        for (int i = 0; i < windowSize; i++) {
            recentWindow[i] = values[values.length - windowSize + i];
        }

        List<PredictionResult> results = new ArrayList<>();
        int totalPoints = Math.min(forecastDays, 30);

        for (int step = 1; step <= totalPoints; step++) {
            double[] logits = new double[numClasses];
            for (int c = 0; c < numClasses; c++) {
                logits[c] = dotProduct(weights[c], recentWindow) + biases[c];
            }
            double[] probs = softmax(logits);
            int predictedClass = argmax(probs);
            double confidence = probs[predictedClass];

            double representativeValue = classBoundaries.length > 0
                    ? classBoundaries[Math.min(predictedClass, classBoundaries.length - 1)]
                    : predictedClass;

            PredictionResult result = new PredictionResult();
            result.setTaskId(task.getId());
            result.setPredictionDate(lastDate.plusDays(step).toString());
            result.setPredictedValue(round(representativeValue));
            result.setConfidence(round(confidence));
            result.setLowerBound(round(Math.max(0, confidence - 0.1)));
            result.setUpperBound(round(Math.min(1, confidence + 0.1)));
            result.setCreatedAt(LocalDateTime.now());
            results.add(result);

            double[] newWindow = new double[windowSize];
            System.arraycopy(recentWindow, 1, newWindow, 0, windowSize - 1);
            newWindow[windowSize - 1] = representativeValue;
            recentWindow = newWindow;
        }

        return results;
    }

    @Override
    public Map<String, Object> evaluateClassifier(PredictionTask task,
                                                   List<PredictionData.SeriesPoint> series,
                                                   ModelVersion modelVersion) {
        if (series == null || series.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accuracy", 0.0);
            result.put("error", "No data provided");
            return result;
        }

        Map<String, Object> modelData = parseJson(modelVersion.getTrainingMetricsJson());
        int numClasses = modelData.get("numClasses") instanceof Number n ? n.intValue() : 2;
        int windowSize = modelData.get("windowSize") instanceof Number n ? n.intValue() : defaultWindowSize;
        double[] classBoundaries = toDoubleArray(modelData.get("classBoundaries"));

        List<?> weightList = (List<?>) modelData.get("weights");
        List<?> biasList = (List<?>) modelData.get("biases");
        double[][] weights = new double[numClasses][windowSize];
        double[] biases = new double[numClasses];

        for (int c = 0; c < numClasses; c++) {
            List<?> w = weightList.get(c) instanceof List<?> list ? list : List.of();
            for (int f = 0; f < windowSize && f < w.size(); f++) {
                weights[c][f] = w.get(f) instanceof Number n ? n.doubleValue() : 0.0;
            }
            biases[c] = biasList.get(c) instanceof Number n ? n.doubleValue() : 0.0;
        }

        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        int[] classLabels = assignClasses(values, classBoundaries);

        int[][] windows = createSlidingWindows(values, windowSize);
        int[] windowLabels = createWindowLabels(classLabels, windowSize);
        int numSamples = windows.length;

        int tp = 0, fp = 0, tn = 0, fn = 0;
        int[] predictedLabels = new int[numSamples];
        List<Double> probabilities = new ArrayList<>();

        for (int s = 0; s < numSamples; s++) {
            double[] x = new double[windowSize];
            for (int f = 0; f < windowSize; f++) x[f] = (double) windows[s][f];

            double[] logits = new double[numClasses];
            for (int c = 0; c < numClasses; c++) {
                logits[c] = dotProduct(weights[c], x) + biases[c];
            }
            double[] probs = softmax(logits);
            int predClass = argmax(probs);
            predictedLabels[s] = predClass;
            probabilities.add(probs[predClass]);

            int trueClass = windowLabels[s];
            if (numClasses == 2) {
                if (predClass == 1 && trueClass == 1) tp++;
                else if (predClass == 1 && trueClass == 0) fp++;
                else if (predClass == 0 && trueClass == 0) tn++;
                else fn++;
            }
        }

        double accuracy = numSamples > 0 ? (double) countCorrect(predictedLabels, windowLabels) / numSamples : 0;
        double precision = numClasses == 2 ? (tp + fp > 0 ? (double) tp / (tp + fp) : 0) : computeMultiClassPrecision(predictedLabels, windowLabels, numClasses);
        double recall = numClasses == 2 ? (tp + fn > 0 ? (double) tp / (tp + fn) : 0) : computeMultiClassRecall(predictedLabels, windowLabels, numClasses);
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double auc = numClasses == 2 ? calculateAUC(windowLabels, probabilities) : 0.5;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accuracy", round(accuracy));
        result.put("precision", round(precision));
        result.put("recall", round(recall));
        result.put("f1", round(f1));
        result.put("auc", round(auc));
        result.put("numClasses", numClasses);
        result.put("numSamples", numSamples);

        if (numClasses == 2) {
            Map<String, Object> confusionMatrix = new LinkedHashMap<>();
            confusionMatrix.put("tp", tp);
            confusionMatrix.put("fp", fp);
            confusionMatrix.put("tn", tn);
            confusionMatrix.put("fn", fn);
            result.put("confusionMatrix", confusionMatrix);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getFeatureImportance(PredictionTask task, ModelVersion modelVersion) {
        Map<String, Object> modelData = parseJson(modelVersion.getTrainingMetricsJson());
        List<?> weightList = (List<?>) modelData.get("weights");
        int numClasses = modelData.get("numClasses") instanceof Number n ? n.intValue() : 2;
        int windowSize = modelData.get("windowSize") instanceof Number n ? n.intValue() : defaultWindowSize;

        double[][] weights = new double[numClasses][windowSize];
        for (int c = 0; c < numClasses; c++) {
            List<?> w = weightList.get(c) instanceof List<?> list ? list : List.of();
            for (int f = 0; f < windowSize && f < w.size(); f++) {
                weights[c][f] = w.get(f) instanceof Number n ? n.doubleValue() : 0.0;
            }
        }

        return computeFeatureImportance(weights, windowSize);
    }

    @Override
    public Map<String, Object> getAlgorithmParams(PredictionTask task) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("algorithm", getAlgorithmType());
        params.put("description", "基于软最大逻辑回归的多分类预测算法");
        params.put("learningRate", learningRate);
        params.put("maxIterations", maxIterations);
        params.put("regularization", regularization);
        params.put("defaultWindowSize", defaultWindowSize);
        return params;
    }

    @Override
    public ModelVersion trainClassification(PredictionTask task, List<double[]> features, List<Integer> labels) {
        int numClasses = countDistinct(labels);
        int numFeatures = features.get(0).length;
        int numSamples = features.size();

        double[][] weights = new double[numClasses][numFeatures];
        double[] biases = new double[numClasses];

        for (int iter = 0; iter < maxIterations; iter++) {
            double[][] gradW = new double[numClasses][numFeatures];
            double[] gradB = new double[numClasses];

            for (int s = 0; s < numSamples; s++) {
                double[] x = features.get(s);
                double[] logits = new double[numClasses];
                for (int c = 0; c < numClasses; c++) {
                    logits[c] = dotProduct(weights[c], x) + biases[c];
                }
                double[] probs = softmax(logits);

                for (int c = 0; c < numClasses; c++) {
                    double error = probs[c] - (c == labels.get(s) ? 1.0 : 0.0);
                    for (int f = 0; f < numFeatures; f++) {
                        gradW[c][f] += error * x[f];
                    }
                    gradB[c] += error;
                }
            }

            for (int c = 0; c < numClasses; c++) {
                for (int f = 0; f < numFeatures; f++) {
                    gradW[c][f] = gradW[c][f] / numSamples + regularization * weights[c][f];
                    weights[c][f] -= learningRate * gradW[c][f];
                }
                gradB[c] /= numSamples;
                biases[c] -= learningRate * gradB[c];
            }
        }

        List<List<Double>> weightList = new ArrayList<>();
        for (int c = 0; c < numClasses; c++) weightList.add(toDoubleList(weights[c]));
        List<Double> biasList = new ArrayList<>();
        for (double b : biases) biasList.add(b);

        int tp = 0, fp = 0, tn = 0, fn = 0;
        List<Double> probabilities = new ArrayList<>();
        int[] predictedLabels = new int[numSamples];

        for (int s = 0; s < numSamples; s++) {
            double[] x = features.get(s);
            double[] logits = new double[numClasses];
            for (int c = 0; c < numClasses; c++) {
                logits[c] = dotProduct(weights[c], x) + biases[c];
            }
            double[] probs = softmax(logits);
            int predClass = argmax(probs);
            predictedLabels[s] = predClass;
            probabilities.add(probs[predClass]);

            int trueClass = labels.get(s);
            if (numClasses == 2) {
                if (predClass == 1 && trueClass == 1) tp++;
                else if (predClass == 1 && trueClass == 0) fp++;
                else if (predClass == 0 && trueClass == 0) tn++;
                else fn++;
            }
        }

        double accuracy = (double) countCorrect(predictedLabels, labels.stream().mapToInt(Integer::intValue).toArray()) / numSamples;
        double precision = numClasses == 2 ? (tp + fp > 0 ? (double) tp / (tp + fp) : 0) : computeMultiClassPrecision(predictedLabels, labels.stream().mapToInt(Integer::intValue).toArray(), numClasses);
        double recall = numClasses == 2 ? (tp + fn > 0 ? (double) tp / (tp + fn) : 0) : computeMultiClassRecall(predictedLabels, labels.stream().mapToInt(Integer::intValue).toArray(), numClasses);
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;

        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("logistic_regression_classifier_" + task.getDatasetId());
        modelVersion.setModelType(getAlgorithmType());
        modelVersion.setAlgorithmType(getAlgorithmType());
        modelVersion.setTaskType("CLASSIFICATION");
        modelVersion.setVersionNumber(1);
        modelVersion.setStatus("ACTIVE");
        modelVersion.setTrainingSamples((long) numSamples);
        modelVersion.setPredictionTaskId(task.getId());
        modelVersion.setMae(round(1 - accuracy));
        modelVersion.setRmse(round(Math.sqrt(Math.max(0, 1 - accuracy))));
        modelVersion.setMape(round((1 - accuracy) * 100));
        modelVersion.setCreatedAt(LocalDateTime.now());

        Map<String, Object> modelData = new LinkedHashMap<>();
        modelData.put("weights", weightList);
        modelData.put("biases", biasList);
        modelData.put("numClasses", numClasses);
        modelData.put("numFeatures", numFeatures);
        modelData.put("algorithm", getAlgorithmType());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("accuracy", round(accuracy));
        metrics.put("precision", round(precision));
        metrics.put("recall", round(recall));
        metrics.put("f1", round(f1));
        modelData.put("metrics", metrics);

        modelVersion.setTrainingMetricsJson(safeJson(modelData));

        List<Map<String, Object>> featureImp = new ArrayList<>();
        double[] avgWeightMagnitude = new double[numFeatures];
        for (int c = 0; c < numClasses; c++) {
            for (int f = 0; f < numFeatures; f++) {
                avgWeightMagnitude[f] += Math.abs(weights[c][f]);
            }
        }
        double total = 0;
        for (double v : avgWeightMagnitude) total += v;
        for (int f = 0; f < numFeatures; f++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("featureIndex", f);
            entry.put("featureName", "feature_" + f);
            entry.put("importance", round(total > 0 ? avgWeightMagnitude[f] / total : 0));
            featureImp.add(entry);
        }
        modelVersion.setFeatureImportanceJson(safeJson(featureImp));

        return modelVersion;
    }

    @Override
    public List<Map<String, Object>> predictClassification(PredictionTask task, List<double[]> features, ModelVersion modelVersion) {
        Map<String, Object> modelData = parseJson(modelVersion.getTrainingMetricsJson());
        int numClasses = modelData.get("numClasses") instanceof Number n ? n.intValue() : 2;
        int numFeatures = modelData.get("numFeatures") instanceof Number n ? n.intValue() : features.get(0).length;

        List<?> weightList = (List<?>) modelData.get("weights");
        List<?> biasList = (List<?>) modelData.get("biases");
        double[][] weights = new double[numClasses][numFeatures];
        double[] biases = new double[numClasses];

        for (int c = 0; c < numClasses; c++) {
            List<?> w = weightList.get(c) instanceof List<?> list ? list : List.of();
            for (int f = 0; f < numFeatures && f < w.size(); f++) {
                weights[c][f] = w.get(f) instanceof Number n ? n.doubleValue() : 0.0;
            }
            biases[c] = biasList.get(c) instanceof Number n ? n.doubleValue() : 0.0;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (double[] x : features) {
            double[] logits = new double[numClasses];
            for (int c = 0; c < numClasses; c++) {
                logits[c] = dotProduct(weights[c], x) + biases[c];
            }
            double[] probs = softmax(logits);
            int predClass = argmax(probs);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("label", predClass);
            result.put("probability", round(probs[predClass]));
            result.put("confidence", round(probs[predClass]));
            results.add(result);
        }
        return results;
    }

    private int resolveWindowSize(PredictionTask task) {
        if (task.getWindowSize() != null && task.getWindowSize() >= 2) {
            return Math.min(task.getWindowSize(), 10);
        }
        return defaultWindowSize;
    }

    private int determineNumClasses(double[] values) {
        Set<Double> uniqueValues = new LinkedHashSet<>();
        for (double v : values) uniqueValues.add(round(v));
        int uniqueCount = uniqueValues.size();
        if (uniqueCount <= 2) return 2;
        if (uniqueCount <= 5) return uniqueCount;
        return 3;
    }

    private double[] computeClassBoundaries(double[] values, int numClasses) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double[] boundaries = new double[numClasses - 1];
        for (int i = 0; i < numClasses - 1; i++) {
            int idx = (int) ((i + 1) * sorted.length / numClasses) - 1;
            idx = Math.max(0, Math.min(idx, sorted.length - 2));
            boundaries[i] = (sorted[idx] + sorted[idx + 1]) / 2.0;
        }
        return boundaries;
    }

    private int[] assignClasses(double[] values, double[] boundaries) {
        int[] labels = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            int cls = 0;
            for (double boundary : boundaries) {
                if (values[i] > boundary) cls++;
            }
            labels[i] = cls;
        }
        return labels;
    }

    private int[][] createSlidingWindows(double[] values, int windowSize) {
        int n = values.length;
        int numWindows = n - windowSize;
        if (numWindows <= 0) return new int[0][windowSize];
        int[][] windows = new int[numWindows][windowSize];
        for (int i = 0; i < numWindows; i++) {
            for (int j = 0; j < windowSize; j++) {
                windows[i][j] = (int) Math.round(values[i + j] * 100.0) / 100;
            }
        }
        return windows;
    }

    private int[] createWindowLabels(int[] classLabels, int windowSize) {
        int n = classLabels.length;
        int numWindows = n - windowSize;
        if (numWindows <= 0) return new int[0];
        int[] labels = new int[numWindows];
        for (int i = 0; i < numWindows; i++) {
            labels[i] = classLabels[i + windowSize];
        }
        return labels;
    }

    private int countCorrect(int[] predicted, int[] actual) {
        int count = 0;
        for (int i = 0; i < predicted.length; i++) {
            if (predicted[i] == actual[i]) count++;
        }
        return count;
    }

    private double computeMultiClassPrecision(int[] predicted, int[] actual, int numClasses) {
        double totalPrecision = 0;
        int validClasses = 0;
        for (int c = 0; c < numClasses; c++) {
            int tp = 0, fp = 0;
            for (int i = 0; i < predicted.length; i++) {
                if (predicted[i] == c && actual[i] == c) tp++;
                else if (predicted[i] == c && actual[i] != c) fp++;
            }
            if (tp + fp > 0) {
                totalPrecision += (double) tp / (tp + fp);
                validClasses++;
            }
        }
        return validClasses > 0 ? totalPrecision / validClasses : 0;
    }

    private double computeMultiClassRecall(int[] predicted, int[] actual, int numClasses) {
        double totalRecall = 0;
        int validClasses = 0;
        for (int c = 0; c < numClasses; c++) {
            int tp = 0, fn = 0;
            for (int i = 0; i < predicted.length; i++) {
                if (predicted[i] == c && actual[i] == c) tp++;
                else if (predicted[i] != c && actual[i] == c) fn++;
            }
            if (tp + fn > 0) {
                totalRecall += (double) tp / (tp + fn);
                validClasses++;
            }
        }
        return validClasses > 0 ? totalRecall / validClasses : 0;
    }

    private double calculateAUC(int[] labels, List<Double> probabilities) {
        int n = labels.length;
        int totalPositives = 0;
        for (int l : labels) if (l == 1) totalPositives++;
        int totalNegatives = n - totalPositives;
        if (totalPositives == 0 || totalNegatives == 0) return 0.5;

        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) sortedIndices.add(i);
        sortedIndices.sort((a, b) -> Double.compare(probabilities.get(b), probabilities.get(a)));

        int tp = 0, fp = 0;
        double auc = 0.0;
        int prevTp = 0, prevFp = 0;
        for (int idx : sortedIndices) {
            if (labels[idx] == 1) tp++;
            else fp++;
            if (tp != prevTp || fp != prevFp) {
                auc += (fp - prevFp) * (tp + prevTp) / 2.0;
            }
            prevTp = tp;
            prevFp = fp;
        }
        auc /= (double) totalPositives * totalNegatives;
        return Math.max(0.0, Math.min(1.0, auc));
    }

    private List<Map<String, Object>> computeFeatureImportance(double[][] weights, int numFeatures) {
        List<Map<String, Object>> result = new ArrayList<>();
        double[] avgMagnitude = new double[numFeatures];
        for (int c = 0; c < weights.length; c++) {
            for (int f = 0; f < numFeatures; f++) {
                avgMagnitude[f] += Math.abs(weights[c][f]);
            }
        }
        double total = 0;
        for (double v : avgMagnitude) total += v;

        for (int f = 0; f < numFeatures; f++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("featureIndex", f);
            entry.put("featureName", "value_t-" + (numFeatures - 1 - f));
            entry.put("importance", round(total > 0 ? avgMagnitude[f] / total : 0));
            result.add(entry);
        }
        return result;
    }

    private int countDistinct(List<Integer> labels) {
        Set<Integer> distinct = new LinkedHashSet<>(labels);
        return distinct.size();
    }

    private double dotProduct(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private double[] softmax(double[] logits) {
        double maxLogit = logits[0];
        for (double l : logits) if (l > maxLogit) maxLogit = l;
        double[] expLogits = new double[logits.length];
        double sumExp = 0;
        for (int i = 0; i < logits.length; i++) {
            expLogits[i] = Math.exp(logits[i] - maxLogit);
            sumExp += expLogits[i];
        }
        double[] probs = new double[logits.length];
        for (int i = 0; i < logits.length; i++) probs[i] = expLogits[i] / sumExp;
        return probs;
    }

    private int argmax(double[] values) {
        int maxIdx = 0;
        double maxVal = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > maxVal) {
                maxVal = values[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private List<Double> toDoubleList(double[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add(v);
        return list;
    }

    private double[] toDoubleArray(Object obj) {
        if (obj instanceof List<?> list) {
            double[] result = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number num) result[i] = num.doubleValue();
            }
            return result;
        }
        return new double[0];
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}