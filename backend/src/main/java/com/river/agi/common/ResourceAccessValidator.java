package com.river.agi.common;

import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.mapper.AnnotationTaskMapper;
import com.river.agi.chat.entity.ChatSession;
import com.river.agi.chat.mapper.ChatSessionMapper;
import com.river.agi.chart.entity.ChartConfig;
import com.river.agi.chart.entity.Report;
import com.river.agi.chart.mapper.ChartConfigMapper;
import com.river.agi.chart.mapper.ReportMapper;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.mapper.AsyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceAccessValidator {
    
    private final DatasetMapper datasetMapper;
    private final SecurityScanTaskMapper securityScanTaskMapper;
    private final PredictionTaskMapper predictionTaskMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final AnnotationTaskMapper annotationTaskMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChartConfigMapper chartConfigMapper;
    private final ReportMapper reportMapper;
    private final AsyncTaskMapper asyncTaskMapper;
    private final RoleMapper roleMapper;
    
    private boolean isAdminUser(Long userId) {
        if (userId == null) return false;
        try {
            return roleMapper.selectCodesByUserId(userId).contains("ADMIN");
        } catch (Exception e) {
            log.warn("Failed to check admin role for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    private boolean canAccessResource(Long ownerId, Long userId) {
        if (ownerId == null) return true;
        if (ownerId.equals(userId)) return true;
        return isAdminUser(userId);
    }
    
    public void validateDatasetAccess(Long datasetId, Long userId) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("数据集不存在");
        }
        if (!canAccessResource(dataset.getCreatedBy(), userId)) {
            throw new BusinessException("无权访问此数据集");
        }
    }
    
    public void validateSecurityScanAccess(Long scanTaskId, Long userId) {
        SecurityScanTask task = securityScanTaskMapper.selectById(scanTaskId);
        if (task == null) {
            throw new BusinessException("安全扫描任务不存在");
        }
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset != null && !canAccessResource(dataset.getCreatedBy(), userId)) {
            throw new BusinessException("无权访问此安全扫描结果");
        }
    }
    
    public void validatePredictionAccess(Long predictionId, Long userId) {
        PredictionTask task = predictionTaskMapper.selectById(predictionId);
        if (task == null) {
            throw new BusinessException("预测任务不存在");
        }
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset != null && !canAccessResource(dataset.getCreatedBy(), userId)) {
            throw new BusinessException("无权访问此预测结果");
        }
    }
    
    public void validateModelVersionAccess(Long modelVersionId, Long userId) {
        ModelVersion mv = modelVersionMapper.selectById(modelVersionId);
        if (mv == null) {
            throw new BusinessException("模型版本不存在");
        }
        if (mv.getPredictionTaskId() != null) {
            PredictionTask task = predictionTaskMapper.selectById(mv.getPredictionTaskId());
            if (task != null) {
                Dataset dataset = datasetMapper.selectById(task.getDatasetId());
                if (dataset != null && !canAccessResource(dataset.getCreatedBy(), userId)) {
                    throw new BusinessException("无权访问此模型版本");
                }
            }
        } else if (mv.getCreatedBy() != null && !canAccessResource(mv.getCreatedBy(), userId)) {
            throw new BusinessException("无权访问此模型版本");
        }
    }
    
    public void validateAnnotationAccess(Long annotationTaskId, Long userId) {
        AnnotationTask task = annotationTaskMapper.selectById(annotationTaskId);
        if (task == null) {
            throw new BusinessException("标注任务不存在");
        }
        if (!canAccessResource(task.getCreatedBy(), userId)) {
            throw new BusinessException("无权访问此标注任务");
        }
    }
    
    public void validateChatSessionAccess(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("对话会话不存在");
        }
        if (!session.getUserId().equals(userId) && !isAdminUser(userId)) {
            throw new BusinessException("无权访问此对话");
        }
    }
    
    public void validateChartConfigAccess(Long chartConfigId, Long userId) {
        ChartConfig config = chartConfigMapper.selectById(chartConfigId);
        if (config == null) {
            throw new BusinessException("图表配置不存在");
        }
        if (config.getDatasetId() != null) {
            Dataset dataset = datasetMapper.selectById(config.getDatasetId());
            if (dataset != null && !canAccessResource(dataset.getCreatedBy(), userId)) {
                throw new BusinessException("无权访问此图表配置");
            }
        }
    }
    
    public void validateReportAccess(Long reportId, Long userId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("报告不存在");
        }
        if (report.getDatasetId() != null) {
            Dataset dataset = datasetMapper.selectById(report.getDatasetId());
            if (dataset != null && !canAccessResource(dataset.getCreatedBy(), userId)) {
                throw new BusinessException("无权访问此报告");
            }
        }
    }
    
    public void validateAsyncTaskAccess(Long taskId, Long userId) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("异步任务不存在");
        }
        if (!canAccessResource(task.getCreatedBy(), userId)) {
            throw new BusinessException("无权访问此任务");
        }
    }
    
    public void validateDatasetOwnership(Long datasetId, Long userId) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("数据集不存在");
        }
        if (dataset.getCreatedBy() != null && !dataset.getCreatedBy().equals(userId) && !isAdminUser(userId)) {
            throw new BusinessException("无权操作此数据集");
        }
    }
}
