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
public class RandomForestClassifier implements ClassificationAlgorithm {

    private final ObjectMapper objectMapper;

    @Value("${prediction.classifier.random-forest.num-trees:100}")
    private int numTrees;

    @Value("${prediction.classifier.random-forest.max-depth:10}")
    private int maxDepth;

    @Value("${prediction.classifier.random-forest.min-samples-split:2}")
    private int minSamplesSplit;

    @Value("${prediction.classifier.random-forest.min-samples-leaf:1}")
    private int minSamplesLeaf;

    @Value("${prediction.classifier.random-forest.window-size:5}")
    private int defaultWindowSize;

    @Value("${prediction.classifier.random-forest.random-seed:42}")
    private long randomSeed;

    public RandomForestClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAlgorithmName() {
        return "随机森林分类器";
    }

    @Override
    public String getAlgorithmType() {
        return "RANDOM_FOREST_CLASSIFIER";
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

        double[][] features = createFeatureMatrix(values, windowSize);
        int[] labels = createWindowLabels(classLabels, windowSize);
        int numSamples = features.length;
        int numFeatures = windowSize;

        Random random = new Random(randomSeed);

        List<Map<String, Object>> treeStructures = new ArrayList<>();
        double[] featureImportances = new double[numFeatures];

        for (int t = 0; t < numTrees; t++) {
            BootstrapResult bootstrap = bootstrapSample(features, labels, random);

            TreeNode tree = buildTreeWithRandomFeatures(
                    bootstrap.features(), bootstrap.labels(),
                    0, numClasses, numFeatures, random);

            treeStructures.add(serializeTree(tree));

            double[] treeImportance = new double[numFeatures];
            accumulateImportance(tree, treeImportance, 1.0);
            for (int f = 0; f < numFeatures; f++) {
                featureImportances[f] += treeImportance[f];
            }
        }

        for (int f = 0; f < numFeatures; f++) {
            featureImportances[f] /= numTrees;
        }

        List<TreeNode> forest = buildForestFromStructures(treeStructures);

        int tp = 0, fp = 0, tn = 0, fn = 0;
        List<Double> probabilities = new ArrayList<>();
        int[] predictedLabels = new int[numSamples];

        for (int s = 0; s < numSamples; s++) {
            TreeNode.LeafResult result = predictForestVote(forest, features[s]);
            int predClass = result.predictedClass();
            predictedLabels[s] = predClass;
            probabilities.add(result.classProbabilities().getOrDefault(String.valueOf(predClass), 0.0));

            int trueClass = labels[s];
            if (numClasses == 2) {
                if (predClass == 1 && trueClass == 1) tp++;
                else if (predClass == 1 && trueClass == 0) fp++;
                else if (predClass == 0 && trueClass == 0) tn++;
                else fn++;
            }
        }

        double accuracy = numSamples > 0 ? (double) countCorrect(predictedLabels, labels) / numSamples : 0;
        double precision = numClasses == 2 ? (tp + fp > 0 ? (double) tp / (tp + fp) : 0) : computeMultiClassPrecision(predictedLabels, labels, numClasses);
        double recall = numClasses == 2 ? (tp + fn > 0 ? (double) tp / (tp + fn) : 0) : computeMultiClassRecall(predictedLabels, labels, numClasses);
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double auc = numClasses == 2 ? calculateAUC(labels, probabilities) : 0.5;

        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("random_forest_classifier_" + task.getDatasetId());
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
        modelData.put("trees", treeStructures);
        modelData.put("numClasses", numClasses);
        modelData.put("windowSize", windowSize);
        modelData.put("classBoundaries", toDoubleList(classBoundaries));
        modelData.put("numTrees", numTrees);
        modelData.put("algorithm", getAlgorithmType());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("accuracy", round(accuracy));
        metrics.put("precision", round(precision));
        metrics.put("recall", round(recall));
        metrics.put("f1", round(f1));
        metrics.put("auc", round(auc));
        metrics.put("numClasses", numClasses);
        metrics.put("numFeatures", numFeatures);
        metrics.put("numTrees", numTrees);
        modelData.put("metrics", metrics);

        modelVersion.setTrainingMetricsJson(safeJson(modelData));

        List<Map<String, Object>> featureImportanceList = new ArrayList<>();
        double totalImp = 0;
        for (double v : featureImportances) totalImp += v;
        for (int f = 0; f < numFeatures; f++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("featureIndex", f);
            entry.put("featureName", "value_t-" + (numFeatures - 1 - f));
            entry.put("importance", round(totalImp > 0 ? featureImportances[f] / totalImp : 0));
            featureImportanceList.add(entry);
        }
        modelVersion.setFeatureImportanceJson(safeJson(featureImportanceList));

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

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> treeStructures = (List<Map<String, Object>>) modelData.get("trees");
        List<TreeNode> forest = buildForestFromStructures(treeStructures);

        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        LocalDate lastDate = series.get(series.size() - 1).date();

        double[] recentWindow = new double[windowSize];
        for (int i = 0; i < windowSize; i++) {
            recentWindow[i] = values[values.length - windowSize + i];
        }

        List<PredictionResult> results = new ArrayList<>();
        int totalPoints = Math.min(forecastDays, 30);

        for (int step = 1; step <= totalPoints; step++) {
            TreeNode.LeafResult voteResult = predictForestVote(forest, recentWindow);
            int predictedClass = voteResult.predictedClass();
            double confidence = voteResult.classProbabilities().getOrDefault(String.valueOf(predictedClass), 0.0);

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

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> treeStructures = (List<Map<String, Object>>) modelData.get("trees");
        List<TreeNode> forest = buildForestFromStructures(treeStructures);

        double[] values = series.stream().mapToDouble(PredictionData.SeriesPoint::value).toArray();
        int[] classLabels = assignClasses(values, classBoundaries);

        double[][] features = createFeatureMatrix(values, windowSize);
        int[] labels = createWindowLabels(classLabels, windowSize);
        int numSamples = features.length;

        int tp = 0, fp = 0, tn = 0, fn = 0;
        int[] predictedLabels = new int[numSamples];
        List<Double> probabilities = new ArrayList<>();

        for (int s = 0; s < numSamples; s++) {
            TreeNode.LeafResult result = predictForestVote(forest, features[s]);
            int predClass = result.predictedClass();
            predictedLabels[s] = predClass;
            probabilities.add(result.classProbabilities().getOrDefault(String.valueOf(predClass), 0.0));

            int trueClass = labels[s];
            if (numClasses == 2) {
                if (predClass == 1 && trueClass == 1) tp++;
                else if (predClass == 1 && trueClass == 0) fp++;
                else if (predClass == 0 && trueClass == 0) tn++;
                else fn++;
            }
        }

        double accuracy = numSamples > 0 ? (double) countCorrect(predictedLabels, labels) / numSamples : 0;
        double precision = numClasses == 2 ? (tp + fp > 0 ? (double) tp / (tp + fp) : 0) : computeMultiClassPrecision(predictedLabels, labels, numClasses);
        double recall = numClasses == 2 ? (tp + fn > 0 ? (double) tp / (tp + fn) : 0) : computeMultiClassRecall(predictedLabels, labels, numClasses);
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;
        double auc = numClasses == 2 ? calculateAUC(labels, probabilities) : 0.5;

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
        int numFeatures = modelData.get("numFeatures") instanceof Number n ? n.intValue() : defaultWindowSize;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> treeStructures = (List<Map<String, Object>>) modelData.get("trees");
        List<TreeNode> forest = buildForestFromStructures(treeStructures);

        double[] avgImportance = new double[numFeatures];
        for (TreeNode tree : forest) {
            double[] treeImportance = new double[numFeatures];
            accumulateImportance(tree, treeImportance, 1.0);
            for (int f = 0; f < numFeatures; f++) {
                avgImportance[f] += treeImportance[f];
            }
        }
        for (int f = 0; f < numFeatures; f++) {
            avgImportance[f] /= forest.size();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        double total = 0;
        for (double v : avgImportance) total += v;
        for (int f = 0; f < numFeatures; f++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("featureIndex", f);
            entry.put("featureName", "value_t-" + (numFeatures - 1 - f));
            entry.put("importance", round(total > 0 ? avgImportance[f] / total : 0));
            result.add(entry);
        }
        return result;
    }

    @Override
    public Map<String, Object> getAlgorithmParams(PredictionTask task) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("algorithm", getAlgorithmType());
        params.put("description", "基于多棵决策树集成的随机森林分类算法");
        params.put("numTrees", numTrees);
        params.put("maxDepth", maxDepth);
        params.put("minSamplesSplit", minSamplesSplit);
        params.put("defaultWindowSize", defaultWindowSize);
        return params;
    }

    @Override
    public ModelVersion trainClassification(PredictionTask task, List<double[]> features, List<Integer> labels) {
        int numClasses = countDistinct(labels);
        int numFeatures = features.get(0).length;
        int numSamples = features.size();

        double[][] featureArray = features.toArray(new double[0][]);
        int[] labelArray = labels.stream().mapToInt(Integer::intValue).toArray();

        Random random = new Random(randomSeed);
        List<Map<String, Object>> treeStructures = new ArrayList<>();

        for (int t = 0; t < numTrees; t++) {
            BootstrapResult bootstrap = bootstrapSample(featureArray, labelArray, random);
            TreeNode tree = buildTreeWithRandomFeatures(
                    bootstrap.features(), bootstrap.labels(),
                    0, numClasses, numFeatures, random);
            treeStructures.add(serializeTree(tree));
        }

        List<TreeNode> forest = buildForestFromStructures(treeStructures);

        int tp = 0, fp = 0, tn = 0, fn = 0;
        int[] predictedLabels = new int[numSamples];

        for (int s = 0; s < numSamples; s++) {
            TreeNode.LeafResult result = predictForestVote(forest, featureArray[s]);
            int predClass = result.predictedClass();
            predictedLabels[s] = predClass;

            int trueClass = labelArray[s];
            if (numClasses == 2) {
                if (predClass == 1 && trueClass == 1) tp++;
                else if (predClass == 1 && trueClass == 0) fp++;
                else if (predClass == 0 && trueClass == 0) tn++;
                else fn++;
            }
        }

        double accuracy = (double) countCorrect(predictedLabels, labelArray) / numSamples;
        double precision = numClasses == 2 ? (tp + fp > 0 ? (double) tp / (tp + fp) : 0) : computeMultiClassPrecision(predictedLabels, labelArray, numClasses);
        double recall = numClasses == 2 ? (tp + fn > 0 ? (double) tp / (tp + fn) : 0) : computeMultiClassRecall(predictedLabels, labelArray, numClasses);
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;

        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setModelName("random_forest_classifier_" + task.getDatasetId());
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
        modelData.put("trees", treeStructures);
        modelData.put("numClasses", numClasses);
        modelData.put("numFeatures", numFeatures);
        modelData.put("numTrees", numTrees);
        modelData.put("algorithm", getAlgorithmType());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("accuracy", round(accuracy));
        metrics.put("precision", round(precision));
        metrics.put("recall", round(recall));
        metrics.put("f1", round(f1));
        modelData.put("metrics", metrics);

        modelVersion.setTrainingMetricsJson(safeJson(modelData));

        List<Map<String, Object>> featureImp = getFeatureImportance(task, modelVersion);
        modelVersion.setFeatureImportanceJson(safeJson(featureImp));

        return modelVersion;
    }

    @Override
    public List<Map<String, Object>> predictClassification(PredictionTask task, List<double[]> features, ModelVersion modelVersion) {
        Map<String, Object> modelData = parseJson(modelVersion.getTrainingMetricsJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> treeStructures = (List<Map<String, Object>>) modelData.get("trees");
        List<TreeNode> forest = buildForestFromStructures(treeStructures);

        List<Map<String, Object>> results = new ArrayList<>();
        for (double[] x : features) {
            TreeNode.LeafResult voteResult = predictForestVote(forest, x);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("label", voteResult.predictedClass());
            result.put("probability", round(voteResult.classProbabilities().getOrDefault(String.valueOf(voteResult.predictedClass()), 0.0)));
            result.put("confidence", round(voteResult.classProbabilities().getOrDefault(String.valueOf(voteResult.predictedClass()), 0.0)));
            results.add(result);
        }
        return results;
    }

    private BootstrapResult bootstrapSample(double[][] features, int[] labels, Random random) {
        int n = features.length;
        List<Integer> sampleIndices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sampleIndices.add(random.nextInt(n));
        }

        double[][] sampledFeatures = new double[n][];
        int[] sampledLabels = new int[n];
        for (int i = 0; i < n; i++) {
            int idx = sampleIndices.get(i);
            sampledFeatures[i] = features[idx].clone();
            sampledLabels[i] = labels[idx];
        }

        return new BootstrapResult(sampledFeatures, sampledLabels);
    }

    private TreeNode buildTreeWithRandomFeatures(double[][] features, int[] labels,
                                                  int depth, int numClasses, int totalFeatures, Random random) {
        int n = features.length;

        if (depth >= maxDepth || n < minSamplesSplit || numClasses <= 1) {
            return createLeaf(labels, numClasses);
        }

        int numFeatures = features[0].length;
        int featuresPerSplit = Math.max(1, (int) Math.sqrt(numFeatures));

        List<Integer> candidateFeatures = new ArrayList<>();
        for (int i = 0; i < numFeatures; i++) candidateFeatures.add(i);
        Collections.shuffle(candidateFeatures, random);
        candidateFeatures = candidateFeatures.subList(0, Math.min(featuresPerSplit, numFeatures));

        double bestGini = gini(labels, numClasses);
        if (bestGini == 0) {
            return createLeaf(labels, numClasses);
        }

        int bestFeature = -1;
        double bestThreshold = 0;
        double bestScore = -1;

        for (int f : candidateFeatures) {
            double[] values = new double[n];
            for (int i = 0; i < n; i++) values[i] = features[i][f];

            double[] sortedValues = values.clone();
            Arrays.sort(sortedValues);

            Set<Double> thresholds = new LinkedHashSet<>();
            for (int i = 1; i < sortedValues.length; i++) {
                double threshold = (sortedValues[i - 1] + sortedValues[i]) / 2.0;
                thresholds.add(threshold);
            }

            for (double threshold : thresholds) {
                List<Integer> leftIndices = new ArrayList<>();
                List<Integer> rightIndices = new ArrayList<>();

                for (int i = 0; i < n; i++) {
                    if (features[i][f] <= threshold) leftIndices.add(i);
                    else rightIndices.add(i);
                }

                if (leftIndices.size() < minSamplesLeaf || rightIndices.size() < minSamplesLeaf) {
                    continue;
                }

                double weightedGini = calculateWeightedGini(labels, leftIndices, rightIndices, numClasses);
                double gain = bestGini - weightedGini;

                if (gain > bestScore) {
                    bestScore = gain;
                    bestFeature = f;
                    bestThreshold = threshold;
                }
            }
        }

        if (bestFeature == -1) {
            return createLeaf(labels, numClasses);
        }

        List<double[]> leftFeatures = new ArrayList<>();
        List<Integer> leftLabels = new ArrayList<>();
        List<double[]> rightFeatures = new ArrayList<>();
        List<Integer> rightLabels = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (features[i][bestFeature] <= bestThreshold) {
                leftFeatures.add(features[i]);
                leftLabels.add(labels[i]);
            } else {
                rightFeatures.add(features[i]);
                rightLabels.add(labels[i]);
            }
        }

        TreeNode left = buildTreeWithRandomFeatures(
                leftFeatures.toArray(new double[0][]),
                leftLabels.stream().mapToInt(Integer::intValue).toArray(),
                depth + 1, numClasses, totalFeatures, random);
        TreeNode right = buildTreeWithRandomFeatures(
                rightFeatures.toArray(new double[0][]),
                rightLabels.stream().mapToInt(Integer::intValue).toArray(),
                depth + 1, numClasses, totalFeatures, random);

        return new TreeNode.SplitNode(bestFeature, bestThreshold, left, right);
    }

    private TreeNode.LeafResult predictForestVote(List<TreeNode> forest, double[] x) {
        Map<String, double[]> classVotes = new LinkedHashMap<>();

        for (TreeNode tree : forest) {
            TreeNode.LeafResult result = predictTree(tree, x);
            int predClass = result.predictedClass();
            String key = String.valueOf(predClass);
            classVotes.merge(key, new double[]{1.0}, (a, b) -> { a[0] += b[0]; return a; });
        }

        int maxClass = 0;
        double maxVotes = -1;
        for (Map.Entry<String, double[]> entry : classVotes.entrySet()) {
            double votes = entry.getValue()[0];
            if (votes > maxVotes) {
                maxVotes = votes;
                maxClass = Integer.parseInt(entry.getKey());
            }
        }

        Map<String, Double> classProbs = new LinkedHashMap<>();
        double totalVotes = forest.size();
        for (Map.Entry<String, double[]> entry : classVotes.entrySet()) {
            classProbs.put(entry.getKey(), entry.getValue()[0] / totalVotes);
        }

        return new TreeNode.LeafResult(classProbs, maxClass);
    }

    private TreeNode.LeafResult predictTree(TreeNode node, double[] x) {
        if (node instanceof TreeNode.LeafNode leaf) {
            return new TreeNode.LeafResult(leaf.classProbabilities(), leaf.predictedClass());
        }
        TreeNode.SplitNode split = (TreeNode.SplitNode) node;
        if (x[split.featureIndex()] <= split.threshold()) {
            return predictTree(split.left(), x);
        } else {
            return predictTree(split.right(), x);
        }
    }

    private List<TreeNode> buildForestFromStructures(List<Map<String, Object>> treeStructures) {
        List<TreeNode> forest = new ArrayList<>();
        if (treeStructures == null) return forest;
        for (Map<String, Object> ts : treeStructures) {
            forest.add(deserializeTree(ts));
        }
        return forest;
    }

    private double gini(int[] labels, int numClasses) {
        int n = labels.length;
        if (n == 0) return 0;
        double impurity = 1.0;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int label : labels) counts.merge(label, 1, Integer::sum);

        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            double prob = (double) entry.getValue() / n;
            impurity -= prob * prob;
        }
        return impurity;
    }

    private double calculateWeightedGini(int[] labels, List<Integer> leftIndices, List<Integer> rightIndices, int numClasses) {
        int n = labels.length;
        int[] leftLabels = new int[leftIndices.size()];
        for (int i = 0; i < leftIndices.size(); i++) leftLabels[i] = labels[leftIndices.get(i)];
        int[] rightLabels = new int[rightIndices.size()];
        for (int i = 0; i < rightIndices.size(); i++) rightLabels[i] = labels[rightIndices.get(i)];

        double leftGini = gini(leftLabels, numClasses);
        double rightGini = gini(rightLabels, numClasses);
        return (leftIndices.size() * leftGini + rightIndices.size() * rightGini) / n;
    }

    private TreeNode createLeaf(int[] labels, int numClasses) {
        Map<String, Double> classProbs = new LinkedHashMap<>();
        int n = labels.length;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int label : labels) counts.merge(label, 1, Integer::sum);

        int predictedClass = 0;
        int maxCount = -1;
        for (int c = 0; c < numClasses; c++) {
            int count = counts.getOrDefault(c, 0);
            double prob = n > 0 ? (double) count / n : 0;
            classProbs.put(String.valueOf(c), prob);
            if (count > maxCount) {
                maxCount = count;
                predictedClass = c;
            }
        }

        return new TreeNode.LeafNode(classProbs, predictedClass);
    }

    private Map<String, Object> serializeTree(TreeNode node) {
        if (node instanceof TreeNode.LeafNode leaf) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("isLeaf", true);
            map.put("classProbabilities", leaf.classProbabilities());
            map.put("predictedClass", leaf.predictedClass());
            return map;
        }
        TreeNode.SplitNode split = (TreeNode.SplitNode) node;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("isLeaf", false);
        map.put("featureIndex", split.featureIndex());
        map.put("threshold", split.threshold());
        map.put("left", serializeTree(split.left()));
        map.put("right", serializeTree(split.right()));
        return map;
    }

    private TreeNode deserializeTree(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            boolean isLeaf = Boolean.TRUE.equals(map.get("isLeaf"));
            if (isLeaf) {
                @SuppressWarnings("unchecked")
                Map<String, Double> classProbs = (Map<String, Double>) map.get("classProbabilities");
                int predictedClass = map.get("predictedClass") instanceof Number num ? num.intValue() : 0;
                return new TreeNode.LeafNode(classProbs != null ? classProbs : new LinkedHashMap<>(), predictedClass);
            } else {
                int featureIndex = map.get("featureIndex") instanceof Number num ? num.intValue() : 0;
                double threshold = map.get("threshold") instanceof Number num ? num.doubleValue() : 0.0;
                TreeNode left = deserializeTree(map.get("left"));
                TreeNode right = deserializeTree(map.get("right"));
                return new TreeNode.SplitNode(featureIndex, threshold, left, right);
            }
        }
        return new TreeNode.LeafNode(new LinkedHashMap<>(), 0);
    }

    private void accumulateImportance(TreeNode node, double[] importance, double weight) {
        if (node instanceof TreeNode.SplitNode split) {
            importance[split.featureIndex()] += weight;
            accumulateImportance(split.left(), importance, weight * 0.5);
            accumulateImportance(split.right(), importance, weight * 0.5);
        }
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

    private double[][] createFeatureMatrix(double[] values, int windowSize) {
        int n = values.length;
        int numWindows = n - windowSize;
        if (numWindows <= 0) return new double[0][windowSize];
        double[][] features = new double[numWindows][windowSize];
        for (int i = 0; i < numWindows; i++) {
            for (int j = 0; j < windowSize; j++) {
                features[i][j] = values[i + j];
            }
        }
        return features;
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

    private int countDistinct(List<Integer> labels) {
        Set<Integer> distinct = new LinkedHashSet<>(labels);
        return distinct.size();
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

    private record BootstrapResult(double[][] features, int[] labels) {}

    private sealed interface TreeNode {
        record LeafNode(Map<String, Double> classProbabilities, int predictedClass) implements TreeNode {}
        record SplitNode(int featureIndex, double threshold, TreeNode left, TreeNode right) implements TreeNode {}
        record LeafResult(Map<String, Double> classProbabilities, int predictedClass) {}
    }
}