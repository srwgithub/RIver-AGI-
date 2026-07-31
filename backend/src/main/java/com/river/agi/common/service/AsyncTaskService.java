package com.river.agi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.mapper.AsyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskService {
    
    private final AsyncTaskMapper asyncTaskMapper;
    private final ObjectMapper objectMapper;
    private final SecurityUtils securityUtils;
    
    private final Map<String, Consumer<AsyncTask>> taskHandlers = new ConcurrentHashMap<>();
    
    public void registerHandler(String taskType, Consumer<AsyncTask> handler) {
        taskHandlers.put(taskType, handler);
        log.info("Registered task handler: {}", taskType);
    }
    
    @Transactional
    public AsyncTask createTask(String taskType, String taskName, Map<String, Object> params, 
                                String resourceType, Long resourceId, Authentication authentication) {
        AsyncTask task = new AsyncTask();
        task.setTaskType(taskType);
        task.setTaskName(taskName);
        task.setStatus(AsyncTask.Status.PENDING.name());
        task.setProgress(0);
        task.setParamsJson(toJson(params));
        task.setResourceType(resourceType);
        task.setResourceId(resourceId);
        task.setCreatedBy(securityUtils.getCurrentUserId(authentication));
        task.setTenantId(1L);
        task.setRetryCount(0);
        task.setMaxRetries(3);
        task.setPriority("NORMAL");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        asyncTaskMapper.insert(task);
        log.info("Task created: {} - {}", taskType, taskName);
        
        return task;
    }
    
    @Async("taskExecutor")
    @Transactional
    public void executeTask(Long taskId) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("Task not found: {}", taskId);
            return;
        }
        
        try {
            task.setStatus(AsyncTask.Status.RUNNING.name());
            task.setStartedAt(LocalDateTime.now());
            asyncTaskMapper.updateById(task);
            
            Consumer<AsyncTask> handler = taskHandlers.get(task.getTaskType());
            if (handler == null) {
                throw new RuntimeException("No handler registered for task type: " + task.getTaskType());
            }
            
            handler.accept(task);
            
            task.setStatus(AsyncTask.Status.COMPLETED.name());
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            asyncTaskMapper.updateById(task);
            
            log.info("Task completed: {} - {}", task.getTaskType(), task.getTaskName());
        } catch (Exception e) {
            log.error("Task failed: {} - {}", task.getTaskType(), task.getTaskName(), e);
            
            int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
            int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : 3;
            
            if (retryCount < maxRetries) {
                task.setRetryCount(retryCount + 1);
                task.setStatus(AsyncTask.Status.PENDING.name());
                task.setErrorMessage(e.getMessage());
                task.setUpdatedAt(LocalDateTime.now());
                asyncTaskMapper.updateById(task);
                
                log.info("Task will be retried: {} - {}, attempt {}/{}", 
                        task.getTaskType(), task.getTaskName(), retryCount + 1, maxRetries);
                
                executeTaskLater(taskId);
            } else {
                task.setStatus(AsyncTask.Status.FAILED.name());
                task.setErrorMessage(e.getMessage());
                task.setCompletedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                asyncTaskMapper.updateById(task);
                
                log.error("Task failed permanently: {} - {}", task.getTaskType(), task.getTaskName());
            }
        }
    }
    
    private void executeTaskLater(Long taskId) {
        try {
            Thread.sleep(1000);
            executeTask(taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Transactional
    public void updateTaskProgress(Long taskId, int progress, Map<String, Object> result) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        if (task != null && AsyncTask.Status.RUNNING.name().equals(task.getStatus())) {
            task.setProgress(progress);
            if (result != null) {
                task.setResultJson(toJson(result));
            }
            task.setUpdatedAt(LocalDateTime.now());
            asyncTaskMapper.updateById(task);
        }
    }
    
    @Transactional
    public void cancelTask(Long taskId, Long userId) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        
        if (!task.getCreatedBy().equals(userId)) {
            throw new RuntimeException("Not authorized to cancel this task");
        }
        
        if (AsyncTask.Status.PENDING.name().equals(task.getStatus()) || 
            AsyncTask.Status.RUNNING.name().equals(task.getStatus())) {
            task.setStatus(AsyncTask.Status.CANCELLED.name());
            task.setUpdatedAt(LocalDateTime.now());
            asyncTaskMapper.updateById(task);
            log.info("Task cancelled: {} - {}", task.getTaskType(), task.getTaskName());
        }
    }
    
    public AsyncTask getTask(Long taskId) {
        AsyncTask task = asyncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        return task;
    }
    
    public List<AsyncTask> getTasksByUser(Long userId) {
        return asyncTaskMapper.selectByUserId(userId);
    }
    
    public Map<String, Object> getTaskProgress(Long taskId) {
        AsyncTask task = getTask(taskId);
        Map<String, Object> progress = new HashMap<>();
        progress.put("taskId", task.getId());
        progress.put("status", task.getStatus());
        progress.put("progress", task.getProgress());
        progress.put("taskType", task.getTaskType());
        progress.put("taskName", task.getTaskName());
        progress.put("errorMessage", task.getErrorMessage());
        progress.put("startedAt", task.getStartedAt());
        progress.put("completedAt", task.getCompletedAt());
        
        if (task.getResultJson() != null) {
            try {
                progress.put("result", fromJson(task.getResultJson()));
            } catch (Exception e) {
                progress.put("result", task.getResultJson());
            }
        }
        
        return progress;
    }
    
    public PageResult<AsyncTask> getTaskList(int page, int size, String status, String taskType) {
        Page<AsyncTask> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<AsyncTask> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AsyncTask::getStatus, status);
        }
        if (taskType != null && !taskType.isEmpty()) {
            wrapper.eq(AsyncTask::getTaskType, taskType);
        }
        
        wrapper.orderByDesc(AsyncTask::getCreatedAt);
        
        Page<AsyncTask> pageResult = asyncTaskMapper.selectPage(pageRequest, wrapper);
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    public List<AsyncTask> getTasksByResource(String resourceType, Long resourceId) {
        return asyncTaskMapper.selectList(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getResourceType, resourceType)
                .eq(AsyncTask::getResourceId, resourceId)
                .orderByDesc(AsyncTask::getCreatedAt));
    }
    
    public List<AsyncTask> getPendingTasks(int limit) {
        return asyncTaskMapper.selectPendingTasks(limit);
    }
    
    public void retryTask(Long taskId) {
        AsyncTask task = getTask(taskId);
        if (!AsyncTask.Status.FAILED.name().equals(task.getStatus())) {
            throw new RuntimeException("Only failed tasks can be retried");
        }
        
        task.setStatus(AsyncTask.Status.PENDING.name());
        task.setProgress(0);
        task.setErrorMessage(null);
        task.setResultJson(null);
        task.setRetryCount(0);
        task.setUpdatedAt(LocalDateTime.now());
        asyncTaskMapper.updateById(task);
        
        executeTask(taskId);
    }
    
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            return "{}";
        }
    }
    
    private Object fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize JSON", e);
            return null;
        }
    }
}
