package com.river.agi.dataset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.entity.DatasetField;
import com.river.agi.dataset.mapper.DatasetFieldMapper;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.annotation.AuditOperation;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {
    
    private final DatasetMapper datasetMapper;
    private final DatasetFieldMapper datasetFieldMapper;
    private final LocalStorageService localStorageService;
    private final DatasetParserService datasetParserService;
    private final SecurityUtils securityUtils;
    private final ResourceAccessValidator accessValidator;
    private final AsyncTaskService asyncTaskService;
    
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("xlsx", "xls", "csv", "json");
    
    @AuditOperation(action = "UPLOAD", resourceType = "DATASET", description = "Upload dataset file")
    public Dataset uploadFile(MultipartFile file, Authentication authentication) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException("File name is required");
        }
        
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Unsupported file type. Allowed types: xlsx, xls, csv, json");
        }
        
        // Upload to local storage
        String fileUrl = localStorageService.uploadFile(file);
        
        // Create dataset record
        Dataset dataset = new Dataset();
        dataset.setName(originalFilename);
        dataset.setFileType(extension);
        dataset.setFilePath(originalFilename);
        dataset.setFileUrl(fileUrl);
        dataset.setFileSize(file.getSize());
        dataset.setStatus("UPLOADED");
        dataset.setCreatedBy(securityUtils.getCurrentUserId(authentication));
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setUpdatedAt(LocalDateTime.now());
        
        datasetMapper.insert(dataset);
        
        // Create async parse task (do NOT block on synchronous parse)
        try {
            AsyncTask parseTask = asyncTaskService.createTask(
                    AsyncTask.TaskType.DATASET_PARSE.name(),
                    "Auto-parse dataset: " + dataset.getName(),
                    java.util.Map.of("datasetId", dataset.getId(), "autoTriggered", true),
                    "DATASET",
                    dataset.getId(),
                    authentication
            );
            // Start the short task asynchronously so upload remains responsive.
            asyncTaskService.executeTask(parseTask.getId());
        } catch (Exception e) {
            log.warn("Failed to create auto-parse task, dataset can be parsed manually later: {}", e.getMessage());
        }
        
        log.info("Dataset uploaded successfully: {}", dataset.getId());
        return dataset;
    }
    
    public PageResult<Dataset> getDatasets(int page, int size) {
        Page<Dataset> pageRequest = new Page<>(page, size);
        Page<Dataset> pageResult = datasetMapper.selectPage(pageRequest, 
                new LambdaQueryWrapper<Dataset>().orderByDesc(Dataset::getCreatedAt));
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    public Dataset getDataset(Long id, Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        accessValidator.validateDatasetAccess(id, userId);
        
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }
        return dataset;
    }
    
    @AuditOperation(action = "DELETE", resourceType = "DATASET", description = "Delete dataset")
    public void deleteDataset(Long id, Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        accessValidator.validateDatasetOwnership(id, userId);
        
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }
        
        String fileName = dataset.getFileUrl().substring(dataset.getFileUrl().lastIndexOf("/") + 1);
        localStorageService.deleteFile(fileName);
        
        datasetMapper.deleteById(id);
        datasetFieldMapper.delete(new LambdaQueryWrapper<DatasetField>().eq(DatasetField::getDatasetId, id));
        
        log.info("Dataset deleted successfully: {}", id);
    }
    
    public List<DatasetField> getDatasetFields(Long datasetId, Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        accessValidator.validateDatasetAccess(datasetId, userId);
        return datasetFieldMapper.selectByDatasetId(datasetId);
    }
    
    @AuditOperation(action = "PARSE", resourceType = "DATASET", description = "Parse/process dataset asynchronously")
    public AsyncTask parseDataset(Long datasetId, Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        accessValidator.validateDatasetAccess(datasetId, userId);
        
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }
        
        // Idempotency: check for existing active task
        List<AsyncTask> existingTasks = asyncTaskService.getTasksByResource("DATASET", datasetId);
        for (AsyncTask existing : existingTasks) {
            if ("DATASET_PARSE".equals(existing.getTaskType())) {
                String status = existing.getStatus();
                if ("RUNNING".equals(status) || "PENDING".equals(status)) {
                    log.info("Returning existing parse task: {} for dataset: {}", existing.getId(), datasetId);
                    return existing;
                }
                if ("COMPLETED".equals(status)) {
                    log.info("Dataset already parsed: {}, returning completed task", datasetId);
                    return existing;
                }
            }
        }
        
        // Check if dataset is already parsed
        if ("PARSED".equals(dataset.getStatus())) {
            throw new BusinessException("Dataset is already parsed. Status: PARSED");
        }
        
        AsyncTask task = asyncTaskService.createTask(
                AsyncTask.TaskType.DATASET_PARSE.name(),
                "Parse dataset: " + dataset.getName(),
                java.util.Map.of("datasetId", datasetId),
                "DATASET",
                datasetId,
                authentication
        );
        
        try {
            asyncTaskService.executeTask(task.getId());
        } catch (Exception e) {
            log.warn("Failed to execute parse task immediately, will be retried by scheduler: {}", e.getMessage());
        }
        
        log.info("Dataset parse task created: {} for dataset: {}", task.getId(), datasetId);
        return task;
    }
    
    @AuditOperation(action = "UPDATE", resourceType = "DATASET", description = "Update dataset")
    public void updateDataset(Long id, Dataset updateRequest, Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        accessValidator.validateDatasetOwnership(id, userId);
        
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }
        
        if (updateRequest.getName() != null) {
            dataset.setName(updateRequest.getName());
        }
        if (updateRequest.getDescription() != null) {
            dataset.setDescription(updateRequest.getDescription());
        }
        dataset.setUpdatedAt(LocalDateTime.now());
        
        datasetMapper.updateById(dataset);
    }
}
