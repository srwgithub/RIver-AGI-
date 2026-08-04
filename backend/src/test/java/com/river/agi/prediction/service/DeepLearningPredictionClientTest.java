package com.river.agi.prediction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepLearningPredictionClientTest {

    @Mock private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private DeepLearningPredictionClient client;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        client = new DeepLearningPredictionClient(restTemplate, objectMapper);
        setField(client, "dlEngineUrl", "http://test:5000");
        setField(client, "dlEngineEnabled", true);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private DeepLearningPredictionClient disabledClient() throws Exception {
        DeepLearningPredictionClient c = new DeepLearningPredictionClient(restTemplate, objectMapper);
        setField(c, "dlEngineUrl", "http://test:5000");
        setField(c, "dlEngineEnabled", false);
        return c;
    }

    private ResponseEntity<String> ok(String body) {
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    private ResponseEntity<String> fail(HttpStatus status) {
        return new ResponseEntity<>(null, status);
    }

    @Test
    @DisplayName("isDlEngineEnabled and getEngineUrl return configured values")
    void configAccessors() {
        assertTrue(client.isDlEngineEnabled());
        assertEquals("http://test:5000", client.getEngineUrl());
    }

    @Test
    @DisplayName("isServiceAvailable returns true on 2xx health check")
    void isServiceAvailable_ok() {
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ok("{}"));
        assertTrue(client.isServiceAvailable());
    }

    @Test
    @DisplayName("isServiceAvailable returns false on exception")
    void isServiceAvailable_exception() {
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenThrow(new RestClientException("down"));
        assertFalse(client.isServiceAvailable());
    }

    @Test
    @DisplayName("isServiceAvailable returns false when engine disabled")
    void isServiceAvailable_disabled() throws Exception {
        assertFalse(disabledClient().isServiceAvailable());
    }

    @Test
    @DisplayName("train succeeds and parses response envelope with data payload")
    void train_success() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("X", List.of(1, 2, 3));
        features.put("y", List.of(4, 5, 6));
        DeepLearningPredictionClient.DeepLearningTrainRequest req =
                new DeepLearningPredictionClient.DeepLearningTrainRequest(
                        "MLP_DL", "CLASSIFICATION", features, new LinkedHashMap<>(Map.of("windowSize", 5)));

        String body = "{\"data\":{\"model_id\":\"m1\",\"model_name\":\"n\",\"algorithm\":\"mlp\"," +
                "\"task_type\":\"classification\",\"training_metrics\":{\"mae\":0.1},\"params\":{\"epochs\":10}," +
                "\"status\":\"ACTIVE\"}}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ok(body));

        DeepLearningPredictionClient.DeepLearningTrainResponse resp = client.train(req);
        assertEquals("m1", resp.modelId());
        assertEquals("n", resp.modelName());
        assertEquals("ACTIVE", resp.status());
        assertNotNull(resp.metrics());
        assertNotNull(resp.parameters());
    }

    @Test
    @DisplayName("train succeeds using values fallback when X/y absent")
    void train_success_valuesFallback() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("values", List.of(1, 2, 3));
        DeepLearningPredictionClient.DeepLearningTrainRequest req =
                new DeepLearningPredictionClient.DeepLearningTrainRequest(
                        "lstm", "regression", features, Map.of());

        String body = "{\"model_id\":\"m2\",\"algorithm\":\"lstm\",\"status\":\"ACTIVE\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ok(body));

        DeepLearningPredictionClient.DeepLearningTrainResponse resp = client.train(req);
        assertEquals("m2", resp.modelId());
    }

    @Test
    @DisplayName("train with missing features throws BusinessException")
    void train_missingFeatures() {
        DeepLearningPredictionClient.DeepLearningTrainRequest req =
                new DeepLearningPredictionClient.DeepLearningTrainRequest(
                        "mlp", "regression", Map.of(), Map.of());
        assertThrows(BusinessException.class, () -> client.train(req));
    }

    @Test
    @DisplayName("train on RestClientException throws SERVICE_UNAVAILABLE")
    void train_restClientException() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("X", List.of(1, 2, 3));
        features.put("y", List.of(4, 5, 6));
        DeepLearningPredictionClient.DeepLearningTrainRequest req =
                new DeepLearningPredictionClient.DeepLearningTrainRequest("mlp", "regression", features, Map.of());

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("conn"));
        assertThrows(BusinessException.class, () -> client.train(req));
    }

    @Test
    @DisplayName("train on non-2xx throws BusinessException")
    void train_non2xx() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("X", List.of(1, 2, 3));
        features.put("y", List.of(4, 5, 6));
        DeepLearningPredictionClient.DeepLearningTrainRequest req =
                new DeepLearningPredictionClient.DeepLearningTrainRequest("mlp", "regression", features, Map.of());

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(fail(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(BusinessException.class, () -> client.train(req));
    }

    @Test
    @DisplayName("train when disabled throws SERVICE_UNAVAILABLE")
    void train_disabled() throws Exception {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("X", List.of(1));
        features.put("y", List.of(1));
        DeepLearningPredictionClient.DeepLearningTrainRequest req =
                new DeepLearningPredictionClient.DeepLearningTrainRequest("mlp", "regression", features, Map.of());
        assertThrows(BusinessException.class, () -> disabledClient().train(req));
    }

    @Test
    @DisplayName("predict succeeds and normalizes map predictions")
    void predict_success_maps() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("values", List.of(1, 2, 3));
        DeepLearningPredictionClient.DeepLearningPredictRequest req =
                new DeepLearningPredictionClient.DeepLearningPredictRequest("m1", features);

        String body = "{\"predictions\":[{\"predictedValue\":1.0,\"confidence\":0.9},{\"predictedValue\":2.0}]}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ok(body));

        DeepLearningPredictionClient.DeepLearningPredictResponse resp = client.predict(req);
        assertEquals(2, resp.predictions().size());
    }

    @Test
    @DisplayName("predict succeeds and normalizes scalar predictions")
    void predict_success_scalars() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("X", List.of(1, 2, 3));
        DeepLearningPredictionClient.DeepLearningPredictRequest req =
                new DeepLearningPredictionClient.DeepLearningPredictRequest("m1", features);

        String body = "{\"predictions\":[1.0,2.0,3.0]}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ok(body));

        DeepLearningPredictionClient.DeepLearningPredictResponse resp = client.predict(req);
        assertEquals(3, resp.predictions().size());
    }

    @Test
    @DisplayName("predict with missing features throws BusinessException")
    void predict_missingFeatures() {
        DeepLearningPredictionClient.DeepLearningPredictRequest req =
                new DeepLearningPredictionClient.DeepLearningPredictRequest("m1", Map.of());
        assertThrows(BusinessException.class, () -> client.predict(req));
    }

    @Test
    @DisplayName("predict on RestClientException throws SERVICE_UNAVAILABLE")
    void predict_restClientException() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("values", List.of(1));
        DeepLearningPredictionClient.DeepLearningPredictRequest req =
                new DeepLearningPredictionClient.DeepLearningPredictRequest("m1", features);

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("conn"));
        assertThrows(BusinessException.class, () -> client.predict(req));
    }

    @Test
    @DisplayName("predict on non-2xx throws BusinessException")
    void predict_non2xx() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("values", List.of(1));
        DeepLearningPredictionClient.DeepLearningPredictRequest req =
                new DeepLearningPredictionClient.DeepLearningPredictRequest("m1", features);

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(fail(HttpStatus.BAD_REQUEST));
        assertThrows(BusinessException.class, () -> client.predict(req));
    }

    @Test
    @DisplayName("predict with null features uses empty map and throws")
    void predict_nullFeatures() {
        DeepLearningPredictionClient.DeepLearningPredictRequest req =
                new DeepLearningPredictionClient.DeepLearningPredictRequest("m1", null);
        assertThrows(BusinessException.class, () -> client.predict(req));
    }

    @Test
    @DisplayName("getModel returns parsed model info")
    void getModel_success() {
        String body = "{\"model_id\":\"m1\",\"modelName\":\"n\",\"modelType\":\"mlp\"," +
                "\"taskType\":\"regression\",\"status\":\"ACTIVE\",\"metrics\":{},\"parameters\":{},\"trainingSamples\":100}";
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ok(body));

        DeepLearningPredictionClient.DeepLearningModelInfo info = client.getModel("m1");
        assertEquals("m1", info.modelId());
        assertEquals("n", info.modelName());
    }

    @Test
    @DisplayName("getModel on non-2xx throws NOT_FOUND BusinessException")
    void getModel_non2xx() {
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(fail(HttpStatus.NOT_FOUND));
        assertThrows(BusinessException.class, () -> client.getModel("m1"));
    }

    @Test
    @DisplayName("getModel on RestClientException throws SERVICE_UNAVAILABLE")
    void getModel_restClientException() {
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenThrow(new RestClientException("down"));
        assertThrows(BusinessException.class, () -> client.getModel("m1"));
    }

    @Test
    @DisplayName("deleteModel succeeds on 2xx")
    void deleteModel_success() {
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.DELETE), any(), eq(String.class)))
                .thenReturn(ok("{}"));
        assertDoesNotThrow(() -> client.deleteModel("m1"));
    }

    @Test
    @DisplayName("deleteModel on non-2xx throws BusinessException")
    void deleteModel_non2xx() {
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.DELETE), any(), eq(String.class)))
                .thenReturn(fail(HttpStatus.BAD_REQUEST));
        assertThrows(BusinessException.class, () -> client.deleteModel("m1"));
    }

    @Test
    @DisplayName("deleteModel on RestClientException throws SERVICE_UNAVAILABLE")
    void deleteModel_restClientException() {
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.DELETE), any(), eq(String.class)))
                .thenThrow(new RestClientException("down"));
        assertThrows(BusinessException.class, () -> client.deleteModel("m1"));
    }

    @Test
    @DisplayName("crossValidate returns parsed map")
    void crossValidate_success() {
        DeepLearningPredictionClient.DeepLearningCrossValidateRequest req =
                new DeepLearningPredictionClient.DeepLearningCrossValidateRequest(
                        "mlp", "regression", Map.of("values", List.of(1)), 5, null, null);
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ok("{\"cv\":5,\"score\":0.9}"));

        Map<String, Object> result = client.crossValidate(req);
        assertEquals(5, result.get("cv"));
    }

    @Test
    @DisplayName("crossValidate on non-2xx throws BusinessException")
    void crossValidate_non2xx() {
        DeepLearningPredictionClient.DeepLearningCrossValidateRequest req =
                new DeepLearningPredictionClient.DeepLearningCrossValidateRequest(
                        "mlp", "regression", Map.of("values", List.of(1)), 0, null, null);
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(fail(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(BusinessException.class, () -> client.crossValidate(req));
    }

    @Test
    @DisplayName("compareModels returns parsed map")
    void compareModels_success() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ok("{\"diff\":1.5}"));
        Map<String, Object> result = client.compareModels("m1", "m2", Map.of("metric", "mae"));
        assertEquals(1.5, result.get("diff"));
    }

    @Test
    @DisplayName("compareModels on non-2xx throws BusinessException")
    void compareModels_non2xx() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(fail(HttpStatus.BAD_REQUEST));
        assertThrows(BusinessException.class, () -> client.compareModels("m1", "m2", null));
    }

    @Test
    @DisplayName("listAlgorithms parses algorithms map")
    void listAlgorithms_success() {
        String body = "{\"algorithms\":{\"mlp\":{\"name\":\"MLP\",\"description\":\"d\",\"tasks\":[\"regression\"]}}}";
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ok(body));

        List<DeepLearningPredictionClient.DeepLearningAlgorithm> algos = client.listAlgorithms();
        assertEquals(1, algos.size());
        assertEquals("MLP", algos.get(0).name());
        assertEquals(1, algos.get(0).supportedTasks().size());
    }

    @Test
    @DisplayName("listAlgorithms on non-2xx throws BusinessException")
    void listAlgorithms_non2xx() {
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(fail(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(BusinessException.class, () -> client.listAlgorithms());
    }

    @Test
    @DisplayName("listModels parses models list")
    void listModels_success() {
        String body = "{\"models\":[{\"model_id\":\"m1\"},{\"model_id\":\"m2\"}]}";
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ok(body));

        List<DeepLearningPredictionClient.DeepLearningModelInfo> models = client.listModels();
        assertEquals(2, models.size());
    }

    @Test
    @DisplayName("listModels on non-2xx throws BusinessException")
    void listModels_non2xx() {
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(fail(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(BusinessException.class, () -> client.listModels());
    }

    @Test
    @DisplayName("all engine operations throw when disabled")
    void disabled_throws() throws Exception {
        DeepLearningPredictionClient c = disabledClient();
        assertThrows(BusinessException.class, () -> c.train(new DeepLearningPredictionClient.DeepLearningTrainRequest("mlp", "regression", Map.of("X", List.of(1), "y", List.of(1)), Map.of())));
        assertThrows(BusinessException.class, () -> c.predict(new DeepLearningPredictionClient.DeepLearningPredictRequest("m1", Map.of("values", List.of(1)))));
        assertThrows(BusinessException.class, () -> c.getModel("m1"));
        assertThrows(BusinessException.class, () -> c.deleteModel("m1"));
        assertThrows(BusinessException.class, () -> c.crossValidate(new DeepLearningPredictionClient.DeepLearningCrossValidateRequest("mlp", "regression", Map.of(), 5, null, null)));
        assertThrows(BusinessException.class, () -> c.compareModels("m1", "m2", null));
        assertThrows(BusinessException.class, () -> c.listAlgorithms());
        assertThrows(BusinessException.class, () -> c.listModels());
    }
}
