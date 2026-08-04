package com.river.agi.prediction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.BusinessException;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionAlgorithmConfig;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionAlgorithmConfigMapper;
import com.river.agi.prediction.service.DeepLearningPredictionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/prediction-admin", "/v1/prediction-admin"})
@RequiredArgsConstructor
public class PredictionAdminController {
    private final PredictionAlgorithmConfigMapper algorithmMapper;
    private final ModelVersionMapper modelMapper;

    @Autowired(required = false)
    private DeepLearningPredictionClient dlClient;

    @GetMapping("/algorithms")
    public ApiResponse<List<PredictionAlgorithmConfig>> algorithms() {
        return ApiResponse.ok(algorithmMapper.selectList(new LambdaQueryWrapper<PredictionAlgorithmConfig>()
                .orderByAsc(PredictionAlgorithmConfig::getPriority)
                .orderByAsc(PredictionAlgorithmConfig::getId)));
    }

    @PostMapping("/algorithms")
    public ApiResponse<PredictionAlgorithmConfig> createAlgorithm(@RequestBody PredictionAlgorithmConfig config) {
        validate(config);
        config.setId(null);
        config.setCreatedAt(LocalDateTime.now());
        if (config.getIsEnabled() == null) config.setIsEnabled(true);
        if (config.getIsDefault() == null) config.setIsDefault(false);
        algorithmMapper.insert(config);
        return ApiResponse.ok(config);
    }

    @PutMapping("/algorithms/{id}")
    public ApiResponse<PredictionAlgorithmConfig> updateAlgorithm(@PathVariable Long id, @RequestBody PredictionAlgorithmConfig input) {
        PredictionAlgorithmConfig current = algorithmMapper.selectById(id);
        if (current == null) throw new BusinessException("算法配置不存在");
        validate(input);
        input.setId(id);
        input.setCreatedAt(current.getCreatedAt());
        algorithmMapper.updateById(input);
        return ApiResponse.ok(algorithmMapper.selectById(id));
    }

    @PostMapping("/algorithms/{id}/enable")
    public ApiResponse<PredictionAlgorithmConfig> enableAlgorithm(@PathVariable Long id) {
        return setAlgorithmEnabled(id, true);
    }

    @PostMapping("/algorithms/{id}/disable")
    public ApiResponse<PredictionAlgorithmConfig> disableAlgorithm(@PathVariable Long id) {
        return setAlgorithmEnabled(id, false);
    }

    private ApiResponse<PredictionAlgorithmConfig> setAlgorithmEnabled(Long id, boolean enabled) {
        PredictionAlgorithmConfig item = algorithmMapper.selectById(id);
        if (item == null) throw new BusinessException("算法配置不存在");
        item.setIsEnabled(enabled);
        algorithmMapper.updateById(item);
        return ApiResponse.ok(item);
    }

    @DeleteMapping("/algorithms/{id}")
    public ApiResponse<Void> deleteAlgorithm(@PathVariable Long id) {
        if (algorithmMapper.selectById(id) == null) throw new BusinessException("算法配置不存在");
        algorithmMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/framework-status")
    public ApiResponse<Map<String, Object>> frameworkStatus() {
        boolean enabled = dlClient != null && dlClient.isDlEngineEnabled();
        boolean reachable = enabled && dlClient.isServiceAvailable();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tensorflow", Map.of("enabled", enabled, "reachable", reachable, "status", reachable ? "ONLINE" : "NOT_CONFIGURED"));
        result.put("pytorch", Map.of("enabled", enabled, "reachable", reachable, "status", reachable ? "ONLINE" : "NOT_CONFIGURED"));
        result.put("pythonEngineEnabled", enabled);
        result.put("pythonEngineReachable", reachable);
        return ApiResponse.ok(result);
    }

    @PostMapping("/models/{id}/archive")
    public ApiResponse<ModelVersion> archiveModel(@PathVariable Long id) {
        return changeModelStatus(id, "ARCHIVED");
    }

    @PostMapping("/models/{id}/lock")
    public ApiResponse<ModelVersion> lockModel(@PathVariable Long id) {
        return changeModelStatus(id, "LOCKED");
    }

    @PostMapping("/models/{id}/unlock")
    public ApiResponse<ModelVersion> unlockModel(@PathVariable Long id) {
        return changeModelStatus(id, "ACTIVE");
    }

    @PostMapping("/models/{id}/rollback")
    public ApiResponse<ModelVersion> rollbackModel(@PathVariable Long id) {
        ModelVersion target = modelMapper.selectById(id);
        if (target == null) throw new BusinessException("模型版本不存在");
        if (target.getModelName() != null) {
            modelMapper.update(null, new LambdaUpdateWrapper<ModelVersion>()
                    .eq(ModelVersion::getModelName, target.getModelName())
                    .set(ModelVersion::getIsProduction, false)
                    .ne(ModelVersion::getId, id));
        }
        target.setIsProduction(true);
        target.setStatus("PRODUCTION");
        target.setUpdatedAt(LocalDateTime.now());
        modelMapper.updateById(target);
        return ApiResponse.ok(target);
    }

    private ApiResponse<ModelVersion> changeModelStatus(Long id, String status) {
        ModelVersion model = modelMapper.selectById(id);
        if (model == null) throw new BusinessException("模型版本不存在");
        if ("ARCHIVED".equals(status) || "LOCKED".equals(status)) model.setIsProduction(false);
        model.setStatus(status);
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.updateById(model);
        return ApiResponse.ok(model);
    }

    private void validate(PredictionAlgorithmConfig config) {
        if (config.getAlgorithmType() == null || config.getAlgorithmType().isBlank()) throw new BusinessException("算法编码不能为空");
        if (config.getAlgorithmName() == null || config.getAlgorithmName().isBlank()) throw new BusinessException("算法名称不能为空");
        if (config.getAlgorithmFamily() == null || config.getAlgorithmFamily().isBlank()) throw new BusinessException("模型类型不能为空");
        if (config.getTaskType() == null || config.getTaskType().isBlank()) throw new BusinessException("任务类型不能为空");
    }
}
