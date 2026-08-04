package com.river.agi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.mapper.AsyncTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("异步任务服务测试")
class AsyncTaskServiceTest {

    @Mock
    private AsyncTaskMapper asyncTaskMapper;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private Authentication authentication;

    private AsyncTaskService service;

    @BeforeEach
    void setUp() {
        service = new AsyncTaskService(asyncTaskMapper, new ObjectMapper(), securityUtils);
    }

    @Test
    @DisplayName("registerHandler - 注册任务处理器")
    void registerHandler_success() {
        assertDoesNotThrow(() -> service.registerHandler("TEST_TYPE", task -> {}));
    }

    @Test
    @DisplayName("createTask - 成功创建任务")
    void createTask_success() {
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);
        when(asyncTaskMapper.insert(any())).thenAnswer(inv -> {
            AsyncTask t = inv.getArgument(0);
            t.setId(100L);
            return 1;
        });

        AsyncTask task = service.createTask(
                "DATASET_PARSE", "Parse dataset",
                Map.of("datasetId", 1L), "DATASET", 1L, authentication);

        assertNotNull(task);
        assertEquals(100L, task.getId());
        assertEquals("PENDING", task.getStatus());
        assertEquals(0, task.getProgress());
        assertEquals("DATASET_PARSE", task.getTaskType());
        assertEquals(1L, task.getResourceId());
        assertEquals("DATASET", task.getResourceType());
        verify(asyncTaskMapper).insert(any());
    }

    @Test
    @DisplayName("createTask - params 为 null")
    void createTask_nullParams() {
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);
        when(asyncTaskMapper.insert(any())).thenAnswer(inv -> {
            AsyncTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        AsyncTask task = service.createTask("TYPE", "Name", null, "RES", 1L, authentication);

        assertNotNull(task);
        // ObjectMapper.writeValueAsString(null) 返回 "null"
        assertEquals("null", task.getParamsJson());
    }

    @Test
    @DisplayName("executeTask - 任务不存在不抛异常")
    void executeTask_notFound() {
        when(asyncTaskMapper.selectById(99L)).thenReturn(null);

        assertDoesNotThrow(() -> service.executeTask(99L));
    }

    @Test
    @DisplayName("executeTask - 处理器未注册抛异常，重试")
    void executeTask_noHandler_retries() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setTaskType("UNREGISTERED");
        task.setRetryCount(0);
        task.setMaxRetries(1);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        // executeTask 会重试，最终 FAILED。selectById 在重试时再次返回同一 task
        assertDoesNotThrow(() -> service.executeTask(1L));
        // 最终应该标记为 FAILED (maxRetries=1，所以第一次失败就 FAILED)
        verify(asyncTaskMapper, atLeast(2)).updateById(any());
    }

    @Test
    @DisplayName("executeTask - 处理器抛异常且超过重试次数标记 FAILED")
    void executeTask_handlerThrows_maxRetriesExceeded() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setTaskType("FAILING");
        task.setRetryCount(5);
        task.setMaxRetries(3);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        assertDoesNotThrow(() -> service.executeTask(1L));
        assertEquals("FAILED", task.getStatus());
        assertNotNull(task.getErrorMessage());
        verify(asyncTaskMapper, atLeast(2)).updateById(any());
    }

    @Test
    @DisplayName("executeTask - 成功执行任务")
    void executeTask_success() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setTaskType("SUCCESS_TYPE");
        task.setRetryCount(0);
        task.setMaxRetries(3);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        service.registerHandler("SUCCESS_TYPE", t -> {
            // 处理逻辑
        });

        service.executeTask(1L);

        assertEquals("COMPLETED", task.getStatus());
        assertEquals(100, task.getProgress());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    @DisplayName("updateTaskProgress - 任务存在且 RUNNING 时更新")
    void updateTaskProgress_runningTask() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setStatus("RUNNING");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        service.updateTaskProgress(1L, 50, Map.of("step", "halfway"));

        assertEquals(50, task.getProgress());
        assertNotNull(task.getResultJson());
        verify(asyncTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("updateTaskProgress - 任务不存在不更新")
    void updateTaskProgress_notFound() {
        when(asyncTaskMapper.selectById(99L)).thenReturn(null);

        assertDoesNotThrow(() -> service.updateTaskProgress(99L, 50, Map.of()));

        verify(asyncTaskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateTaskProgress - 非 RUNNING 状态不更新")
    void updateTaskProgress_notRunning() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setStatus("PENDING");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        service.updateTaskProgress(1L, 50, null);

        verify(asyncTaskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateTaskProgress - result 为 null 不设置 resultJson")
    void updateTaskProgress_nullResult() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setStatus("RUNNING");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        service.updateTaskProgress(1L, 30, null);

        assertEquals(30, task.getProgress());
        assertNull(task.getResultJson());
        verify(asyncTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("cancelTask - 任务不存在抛异常")
    void cancelTask_notFound() {
        when(asyncTaskMapper.selectById(99L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.cancelTask(99L, 1L));
    }

    @Test
    @DisplayName("cancelTask - 非本人任务抛异常")
    void cancelTask_notOwner() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(2L);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        assertThrows(RuntimeException.class, () -> service.cancelTask(1L, 1L));
    }

    @Test
    @DisplayName("cancelTask - PENDING 状态成功取消")
    void cancelTask_pending() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("PENDING");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        service.cancelTask(1L, 1L);

        assertEquals("CANCELLED", task.getStatus());
        verify(asyncTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("cancelTask - RUNNING 状态成功取消")
    void cancelTask_running() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("RUNNING");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        service.cancelTask(1L, 1L);

        assertEquals("CANCELLED", task.getStatus());
        verify(asyncTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("cancelTask - COMPLETED 状态不取消")
    void cancelTask_completed() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("COMPLETED");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        service.cancelTask(1L, 1L);

        assertEquals("COMPLETED", task.getStatus());
        verify(asyncTaskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("getTask(Long) - 任务不存在抛 BusinessException")
    void getTask_notFound() {
        when(asyncTaskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getTask(99L));
    }

    @Test
    @DisplayName("getTask(Long) - 返回任务")
    void getTask_success() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        AsyncTask result = service.getTask(1L);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getTask(Long, Long) - 非本人任务抛异常")
    void getTaskWithUser_notOwner() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(2L);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> service.getTask(1L, 1L));
    }

    @Test
    @DisplayName("getTask(Long, Long) - createdBy 为 null 允许访问")
    void getTaskWithUser_nullCreatedBy() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(null);
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        AsyncTask result = service.getTask(1L, 1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTasksByUser - 返回用户任务列表")
    void getTasksByUser_success() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(asyncTaskMapper.selectByUserId(1L)).thenReturn(List.of(task));

        List<AsyncTask> result = service.getTasksByUser(1L);

        assertEquals(1, result.size());
        verify(asyncTaskMapper).selectByUserId(1L);
    }

    @Test
    @DisplayName("getTaskProgress - 返回进度信息")
    void getTaskProgress_success() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("RUNNING");
        task.setProgress(50);
        task.setTaskType("TYPE");
        task.setTaskName("Name");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        Map<String, Object> progress = service.getTaskProgress(1L, 1L);

        assertEquals(1L, progress.get("taskId"));
        assertEquals("RUNNING", progress.get("status"));
        assertEquals(50, progress.get("progress"));
        assertEquals("TYPE", progress.get("taskType"));
        assertEquals("Name", progress.get("taskName"));
    }

    @Test
    @DisplayName("getTaskProgress - 包含 result 解析")
    void getTaskProgress_withResult() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("COMPLETED");
        task.setProgress(100);
        task.setResultJson("{\"key\":\"value\"}");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        Map<String, Object> progress = service.getTaskProgress(1L, 1L);

        assertNotNull(progress.get("result"));
    }

    @Test
    @DisplayName("getTaskProgress - 非法 resultJson 回退原字符串")
    void getTaskProgress_invalidResultJson() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("COMPLETED");
        task.setProgress(100);
        task.setResultJson("invalid json");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        Map<String, Object> progress = service.getTaskProgress(1L, 1L);

        // fromJson 内部捕获异常返回 null，所以 result 为 null
        assertNull(progress.get("result"));
    }

    @Test
    @DisplayName("getTaskList - 带过滤条件")
    void getTaskList_withFilters() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        Page<AsyncTask> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(task));
        pageResult.setTotal(1);
        when(asyncTaskMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<AsyncTask> result = service.getTaskList(1, 10, "RUNNING", "TYPE", 1L);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("getTaskList - 无过滤条件")
    void getTaskList_noFilters() {
        Page<AsyncTask> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of());
        pageResult.setTotal(0);
        when(asyncTaskMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<AsyncTask> result = service.getTaskList(1, 10, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("getTaskList - 空字符串过滤条件")
    void getTaskList_emptyStringFilters() {
        Page<AsyncTask> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of());
        pageResult.setTotal(0);
        when(asyncTaskMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<AsyncTask> result = service.getTaskList(1, 10, "", "", 1L);

        assertNotNull(result);
    }

    @Test
    @DisplayName("getTasksByResource - 返回资源相关任务")
    void getTasksByResource_success() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(asyncTaskMapper.selectList(any())).thenReturn(List.of(task));

        List<AsyncTask> result = service.getTasksByResource("DATASET", 1L);

        assertEquals(1, result.size());
        verify(asyncTaskMapper).selectList(any());
    }

    @Test
    @DisplayName("getPendingTasks - 返回待处理任务")
    void getPendingTasks_success() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        when(asyncTaskMapper.selectPendingTasks(10)).thenReturn(List.of(task));

        List<AsyncTask> result = service.getPendingTasks(10);

        assertEquals(1, result.size());
        verify(asyncTaskMapper).selectPendingTasks(10);
    }

    @Test
    @DisplayName("retryTask - 非 FAILED 状态抛异常")
    void retryTask_notFailed() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("COMPLETED");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> service.retryTask(1L, 1L));
    }

    @Test
    @DisplayName("retryTask - FAILED 状态成功重置")
    void retryTask_failed_resetsAndExecutes() {
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setCreatedBy(1L);
        task.setStatus("FAILED");
        task.setRetryCount(3);
        task.setErrorMessage("error");
        task.setResultJson("{}");
        when(asyncTaskMapper.selectById(1L)).thenReturn(task);

        // 注册一个处理器避免执行时报错
        service.registerHandler("RETRY_TYPE", t -> {});
        task.setTaskType("RETRY_TYPE");

        service.retryTask(1L, 1L);

        // 任务先被重置为 PENDING，然后执行（处理器成功），最终状态为 COMPLETED
        assertEquals("COMPLETED", task.getStatus());
        assertEquals(100, task.getProgress());
        // updateById 被调用多次: retryTask 重置 + executeTask 状态更新
        verify(asyncTaskMapper, atLeast(1)).updateById(task);
    }

    @Test
    @DisplayName("toJson - 序列化失败返回 {}")
    void toJson_serializeFailure() {
        // 使用一个会抛异常的 ObjectMapper 来触发序列化失败
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        try {
            when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialize error"));
        } catch (Exception ignored) {}
        AsyncTaskService serviceWithFailingMapper = new AsyncTaskService(asyncTaskMapper, failingMapper, securityUtils);
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);
        when(asyncTaskMapper.insert(any())).thenAnswer(inv -> {
            AsyncTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        // 序列化失败时 paramsJson 为 "{}"
        AsyncTask task = serviceWithFailingMapper.createTask("TYPE", "Name", Map.of("k", "v"), "RES", 1L, authentication);
        assertEquals("{}", task.getParamsJson());
    }
}
