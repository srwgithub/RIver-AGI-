package com.river.agi.prediction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class DeepLearningPredictionClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${prediction.dl-engine.url:${DL_ENGINE_URL:http://localhost:5000}}")
    private String dlEngineUrl;

    @Value("${prediction.dl-engine.enabled:${DL_ENGINE_ENABLED:false}}")
    private boolean dlEngineEnabled;

    public DeepLearningPredictionClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isDlEngineEnabled() {
        return dlEngineEnabled;
    }

    public String getEngineUrl() {
        return dlEngineUrl;
    }

    public DeepLearningTrainResponse train(DeepLearningTrainRequest request) {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用 (prediction.dl-engine.enabled=false)");
        }
        String url = dlEngineUrl + "/api/v1/predictions/train";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("algorithm", normalizeAlgorithm(request.modelType()));
            body.put("task_type", normalizeTaskType(request.taskType()));
            Map<String, Object> features = request.features();
            Object values = features.get("values");
            Object x = features.getOrDefault("X", features.get("x"));
            Object y = features.getOrDefault("y", features.get("target"));
            if (x == null) x = values;
            if (y == null) y = values;
            if (x == null || y == null) {
                throw new BusinessException(ErrorCode.MODEL_TRAINING_FAILED,
                        "深度学习训练数据缺少数值特征和目标值");
            }
            // Python engine accepts X/y (or dataset/target) at the top level.
            // The Java domain keeps dates and values together, so adapt that
            // representation here instead of leaking Java field names into
            // the Python API contract.
            body.put("X", x);
            body.put("y", y);
            Map<String, Object> params = new LinkedHashMap<>(request.parameters());
            // The Java UI calls this windowSize while the Python sequence
            // models use sequence_length. Keep both APIs compatible.
            if (!params.containsKey("sequence_length") && params.containsKey("windowSize")) {
                params.put("sequence_length", params.get("windowSize"));
            }
            body.put("params", params);
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> envelope = objectMapper.readValue(response.getBody(), Map.class);
                Object data = envelope.get("data");
                Map<String, Object> payload = data instanceof Map<?, ?>
                        ? objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {})
                        : envelope;
                return new DeepLearningTrainResponse(
                        stringValue(payload, "model_id", "modelId"),
                        stringValue(payload, "model_name", "modelName"),
                        stringValue(payload, "algorithm", "modelType"),
                        stringValue(payload, "task_type", "taskType"),
                        mapValue(payload, "training_metrics", "metrics"),
                        mapValue(payload, "params", "parameters"),
                        stringValue(payload, "status"));
            }
            throw new BusinessException(ErrorCode.MODEL_TRAINING_FAILED,
                    "训练失败: HTTP " + response.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("调用 Python 训练服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        } catch (Exception e) {
            log.error("解析训练响应失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.MODEL_TRAINING_FAILED,
                    "解析训练响应失败: " + e.getMessage());
        }
    }

    public DeepLearningPredictResponse predict(DeepLearningPredictRequest request) {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用 (prediction.dl-engine.enabled=false)");
        }
        String url = dlEngineUrl + "/api/v1/predictions/predict";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model_id", request.modelId());
            Map<String, Object> features = request.features() == null ? Map.of() : request.features();
            body.putAll(features);
            // The Python prediction endpoint parses dataset/X/features. The
            // Java domain payload also contains dates and values, so expose
            // the numeric values through the endpoint's actual contract.
            if (!body.containsKey("X") && !body.containsKey("dataset") && !body.containsKey("features")) {
                Object values = features.get("values");
                if (values == null) {
                    throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                            "深度学习预测数据缺少 X/dataset/features 数值输入");
                }
                body.put("X", values);
            }
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> payload = objectMapper.readValue(response.getBody(), Map.class);
                List<Map<String, Object>> predictions = normalizePredictions(payload.get("predictions"));
                return new DeepLearningPredictResponse(
                        predictions,
                        payload);
            }
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "预测失败: HTTP " + response.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("调用 Python 预测服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        } catch (Exception e) {
            log.error("解析预测响应失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "解析预测响应失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> normalizePredictions(Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                normalized.add(objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {}));
            } else if (value instanceof Number || value instanceof String) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("predictedValue", value);
                normalized.add(item);
            }
        }
        return normalized;
    }

    public DeepLearningModelInfo getModel(String modelId) {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用");
        }
        String url = dlEngineUrl + "/api/v1/predictions/models/" + modelId;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), DeepLearningModelInfo.class);
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型不存在: " + modelId);
        } catch (RestClientException e) {
            log.error("调用 Python 获取模型服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        } catch (Exception e) {
            log.error("解析模型信息失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "解析模型信息失败: " + e.getMessage());
        }
    }

    public void deleteModel(String modelId) {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用");
        }
        String url = dlEngineUrl + "/api/v1/predictions/models/" + modelId;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "删除模型失败: HTTP " + response.getStatusCode().value());
            }
        } catch (RestClientException e) {
            log.error("调用 Python 删除模型服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        }
    }

    public Map<String, Object> crossValidate(DeepLearningCrossValidateRequest request) {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用");
        }
        String url = dlEngineUrl + "/api/v1/predictions/cross-validate";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
            }
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "交叉验证失败: HTTP " + response.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("调用 Python 交叉验证服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        } catch (Exception e) {
            log.error("解析交叉验证响应失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "解析交叉验证响应失败: " + e.getMessage());
        }
    }

    public Map<String, Object> compareModels(String modelId1, String modelId2, Map<String, Object> params) {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用");
        }
        String url = dlEngineUrl + "/api/v1/predictions/models/" + modelId1 + "/compare";
        Map<String, Object> body = new LinkedHashMap<>();
        // Python's compare endpoint requires compare_model_id. The former
        // targetModelId name made the UI's A/B action fail with HTTP 400.
        body.put("compare_model_id", modelId2);
        if (params != null) {
            body.putAll(params);
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
            }
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "模型对比失败: HTTP " + response.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("调用 Python 模型对比服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        } catch (Exception e) {
            log.error("解析模型对比响应失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PREDICTION_TASK_FAILED,
                    "解析模型对比响应失败: " + e.getMessage());
        }
    }

    public List<DeepLearningAlgorithm> listAlgorithms() {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用");
        }
        String url = dlEngineUrl + "/api/v1/predictions/algorithms";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> payload = objectMapper.readValue(response.getBody(), Map.class);
                Object algorithms = payload.get("algorithms");
                List<DeepLearningAlgorithm> result = new ArrayList<>();
                if (algorithms instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> {
                        Map<?, ?> item = (Map<?, ?>) value;
                        Object name = item.get("name");
                        Object description = item.get("description");
                        Object tasks = item.get("tasks");
                        result.add(new DeepLearningAlgorithm(
                                String.valueOf(key),
                                String.valueOf(name != null ? name : key),
                                String.valueOf(description != null ? description : ""),
                                objectMapper.convertValue(tasks != null ? tasks : List.of(), new TypeReference<List<String>>() {})));
                    });
                }
                return result;
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "获取算法列表失败: HTTP " + response.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("调用 Python 算法列表服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        } catch (Exception e) {
            log.error("解析算法列表响应失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "解析算法列表响应失败: " + e.getMessage());
        }
    }

    public List<DeepLearningModelInfo> listModels() {
        if (!dlEngineEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎未启用");
        }
        String url = dlEngineUrl + "/api/v1/predictions/models";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> payload = objectMapper.readValue(response.getBody(), Map.class);
                return objectMapper.convertValue(payload.getOrDefault("models", List.of()),
                        new TypeReference<List<DeepLearningModelInfo>>() {});
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Python 深度学习引擎模型列表不可用: HTTP " + response.getStatusCode().value());
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法连接到 Python 深度学习引擎: " + e.getMessage());
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) throw businessException;
            log.error("解析模型列表响应失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "解析 Python 模型列表失败: " + e.getMessage());
        }
    }

    public boolean isServiceAvailable() {
        if (!dlEngineEnabled) {
            return false;
        }
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    dlEngineUrl + "/api/v1/predictions/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Python 深度学习引擎健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    public record DeepLearningTrainRequest(
            String modelType,
            String taskType,
            Map<String, Object> features,
            Map<String, Object> parameters
    ) {
        public DeepLearningTrainRequest {
            features = features != null ? features : Map.of();
            parameters = parameters != null ? parameters : Map.of();
        }
    }

    public record DeepLearningTrainResponse(
            @JsonAlias("model_id") String modelId,
            String modelName,
            String modelType,
            String taskType,
            Map<String, Object> metrics,
            Map<String, Object> parameters,
            String status
    ) {
        public DeepLearningTrainResponse {
            metrics = metrics != null ? metrics : Map.of();
            parameters = parameters != null ? parameters : Map.of();
        }
    }

    private String normalizeAlgorithm(String modelType) {
        if (modelType == null || modelType.isBlank()) return "mlp";
        String value = modelType.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.endsWith("_dl")) value = value.substring(0, value.length() - 3);
        return switch (value) {
            case "lstm", "transformer", "mlp", "random_forest", "gradient_boosting", "svm" -> value;
            case "randomforest" -> "random_forest";
            case "gradientboosting", "gbdt" -> "gradient_boosting";
            default -> value;
        };
    }

    private String normalizeTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) return "regression";
        return "CLASSIFICATION".equalsIgnoreCase(taskType)
                ? "classification" : "regression";
    }

    private static String stringValue(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    private static Map<String, Object> mapValue(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof Map<?, ?>) {
                Map<String, Object> result = new LinkedHashMap<>();
                ((Map<?, ?>) value).forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
        }
        return Map.of();
    }

    public record DeepLearningPredictRequest(
            @JsonAlias("model_id") String modelId,
            Map<String, Object> features
    ) {
        public DeepLearningPredictRequest {
            features = features != null ? features : Map.of();
        }
    }

    public record DeepLearningPredictResponse(
            List<Map<String, Object>> predictions,
            Map<String, Object> modelInfo
    ) {
        public DeepLearningPredictResponse {
            predictions = predictions != null ? predictions : List.of();
            modelInfo = modelInfo != null ? modelInfo : Map.of();
        }
    }

    public record DeepLearningModelInfo(
            @JsonAlias("model_id") String modelId,
            String modelName,
            String modelType,
            String taskType,
            String status,
            Map<String, Object> metrics,
            Map<String, Object> parameters,
            Long trainingSamples
    ) {
        public DeepLearningModelInfo {
            metrics = metrics != null ? metrics : Map.of();
            parameters = parameters != null ? parameters : Map.of();
        }
    }

    public record DeepLearningAlgorithm(
            String type,
            String name,
            String description,
            List<String> supportedTasks
    ) {
        public DeepLearningAlgorithm {
            supportedTasks = supportedTasks != null ? supportedTasks : List.of();
        }
    }

    public record DeepLearningCrossValidateRequest(
            String modelType,
            String taskType,
            Map<String, Object> features,
            int cvFolds,
            String cvStrategy,
            Map<String, Object> parameters
    ) {
        public DeepLearningCrossValidateRequest {
            if (cvFolds <= 0) cvFolds = 5;
            if (cvStrategy == null || cvStrategy.isBlank()) cvStrategy = "WALK_FORWARD";
            features = features != null ? features : Map.of();
            parameters = parameters != null ? parameters : Map.of();
        }
    }
}
