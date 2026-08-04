package com.river.agi.dataset.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.entity.AsyncTask;
import com.river.agi.common.service.AsyncTaskService;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.entity.DatasetField;
import com.river.agi.dataset.mapper.DatasetFieldMapper;
import com.river.agi.dataset.mapper.DatasetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据集服务测试")
class DatasetServiceTest {

    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetFieldMapper datasetFieldMapper;
    @Mock
    private LocalStorageService localStorageService;
    @Mock
    private DatasetParserService datasetParserService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private ResourceAccessValidator accessValidator;
    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private Authentication authentication;
    @Mock
    private MultipartFile file;

    private DatasetService service;

    @BeforeEach
    void setUp() {
        service = new DatasetService(datasetMapper, datasetFieldMapper, localStorageService,
                datasetParserService, securityUtils, accessValidator, asyncTaskService);
        lenient().when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);
    }

    @Test
    @DisplayName("uploadFile - 成功上传数据集")
    void uploadFile_success() {
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.getSize()).thenReturn(100L);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/files/test.csv");
        when(datasetMapper.insert(any())).thenAnswer(inv -> {
            Dataset d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        AsyncTask task = new AsyncTask();
        task.setId(10L);
        when(asyncTaskService.createTask(any(), any(), any(), any(), any(), any())).thenReturn(task);

        Dataset result = service.uploadFile(file, authentication);

        assertNotNull(result);
        assertEquals("test.csv", result.getName());
        assertEquals("csv", result.getFileType());
        assertEquals("UPLOADED", result.getStatus());
        assertEquals("http://test/files/test.csv", result.getFileUrl());
        assertEquals(100L, result.getFileSize());
        verify(asyncTaskService).executeTask(10L);
    }

    @Test
    @DisplayName("uploadFile - 文件名为 null 抛异常")
    void uploadFile_nullFilename_throws() {
        when(file.getOriginalFilename()).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.uploadFile(file, authentication));
        verify(localStorageService, never()).uploadFile(any());
    }

    @Test
    @DisplayName("uploadFile - 不支持的扩展名抛异常")
    void uploadFile_unsupportedExtension_throws() {
        when(file.getOriginalFilename()).thenReturn("test.txt");

        assertThrows(BusinessException.class, () -> service.uploadFile(file, authentication));
        verify(localStorageService, never()).uploadFile(any());
    }

    @Test
    @DisplayName("uploadFile - json 文件支持")
    void uploadFile_jsonSupported() {
        when(file.getOriginalFilename()).thenReturn("data.json");
        when(file.getSize()).thenReturn(50L);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/files/data.json");
        when(datasetMapper.insert(any())).thenAnswer(inv -> {
            Dataset d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        AsyncTask task = new AsyncTask();
        task.setId(10L);
        when(asyncTaskService.createTask(any(), any(), any(), any(), any(), any())).thenReturn(task);

        Dataset result = service.uploadFile(file, authentication);

        assertEquals("json", result.getFileType());
    }

    @Test
    @DisplayName("uploadFile - xlsx 文件支持")
    void uploadFile_xlsxSupported() {
        when(file.getOriginalFilename()).thenReturn("data.xlsx");
        when(file.getSize()).thenReturn(50L);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/files/data.xlsx");
        when(datasetMapper.insert(any())).thenAnswer(inv -> {
            Dataset d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        AsyncTask task = new AsyncTask();
        task.setId(10L);
        when(asyncTaskService.createTask(any(), any(), any(), any(), any(), any())).thenReturn(task);

        Dataset result = service.uploadFile(file, authentication);

        assertEquals("xlsx", result.getFileType());
    }

    @Test
    @DisplayName("uploadFile - 异步任务创建失败不影响上传")
    void uploadFile_asyncTaskFailure_doesNotAffectUpload() {
        when(file.getOriginalFilename()).thenReturn("test.csv");
        when(file.getSize()).thenReturn(100L);
        when(localStorageService.uploadFile(file)).thenReturn("http://test/files/test.csv");
        when(datasetMapper.insert(any())).thenAnswer(inv -> {
            Dataset d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        when(asyncTaskService.createTask(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("async error"));

        Dataset result = service.uploadFile(file, authentication);

        assertNotNull(result);
        assertEquals("UPLOADED", result.getStatus());
    }

    @Test
    @DisplayName("getDatasets - 返回分页数据集")
    void getDatasets_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        Page<Dataset> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(dataset));
        pageResult.setTotal(1);
        when(datasetMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<Dataset> result = service.getDatasets(1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("getDatasets - 空列表")
    void getDatasets_empty() {
        Page<Dataset> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of());
        pageResult.setTotal(0);
        when(datasetMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<Dataset> result = service.getDatasets(1, 10);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getDataset - 成功获取数据集")
    void getDataset_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Dataset result = service.getDataset(1L, authentication);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(accessValidator).validateDatasetAccess(1L, 1L);
    }

    @Test
    @DisplayName("getDataset - 数据集不存在抛异常")
    void getDataset_notFound_throws() {
        when(datasetMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getDataset(99L, authentication));
    }

    @Test
    @DisplayName("deleteDataset - 成功删除数据集")
    void deleteDataset_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileUrl("http://test/files/test.csv");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        service.deleteDataset(1L, authentication);

        verify(localStorageService).deleteFile("test.csv");
        verify(datasetMapper).deleteById(1L);
        verify(datasetFieldMapper).delete(any());
        verify(accessValidator).validateDatasetOwnership(1L, 1L);
    }

    @Test
    @DisplayName("deleteDataset - 数据集不存在抛异常")
    void deleteDataset_notFound_throws() {
        when(datasetMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.deleteDataset(99L, authentication));
        verify(localStorageService, never()).deleteFile(any());
    }

    @Test
    @DisplayName("getDatasetFields - 返回字段列表")
    void getDatasetFields_success() {
        DatasetField field = new DatasetField();
        field.setId(1L);
        field.setFieldName("col1");
        when(datasetFieldMapper.selectByDatasetId(1L)).thenReturn(List.of(field));

        List<DatasetField> result = service.getDatasetFields(1L, authentication);

        assertEquals(1, result.size());
        assertEquals("col1", result.get(0).getFieldName());
        verify(accessValidator).validateDatasetAccess(1L, 1L);
    }

    @Test
    @DisplayName("parseDataset - 成功创建解析任务")
    void parseDataset_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test");
        dataset.setStatus("UPLOADED");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(asyncTaskService.getTasksByResource("DATASET", 1L)).thenReturn(List.of());
        AsyncTask task = new AsyncTask();
        task.setId(10L);
        when(asyncTaskService.createTask(any(), any(), any(), any(), any(), any())).thenReturn(task);

        AsyncTask result = service.parseDataset(1L, authentication);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        verify(asyncTaskService).executeTask(10L);
    }

    @Test
    @DisplayName("parseDataset - 数据集不存在抛异常")
    void parseDataset_notFound_throws() {
        when(datasetMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.parseDataset(99L, authentication));
    }

    @Test
    @DisplayName("parseDataset - 已存在 RUNNING 任务返回该任务")
    void parseDataset_existingRunningTask() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test");
        dataset.setStatus("UPLOADED");
        AsyncTask existing = new AsyncTask();
        existing.setId(50L);
        existing.setTaskType("DATASET_PARSE");
        existing.setStatus("RUNNING");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(asyncTaskService.getTasksByResource("DATASET", 1L)).thenReturn(List.of(existing));

        AsyncTask result = service.parseDataset(1L, authentication);

        assertEquals(50L, result.getId());
        verify(asyncTaskService, never()).createTask(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("parseDataset - 已存在 PENDING 任务返回该任务")
    void parseDataset_existingPendingTask() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test");
        dataset.setStatus("UPLOADED");
        AsyncTask existing = new AsyncTask();
        existing.setId(50L);
        existing.setTaskType("DATASET_PARSE");
        existing.setStatus("PENDING");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(asyncTaskService.getTasksByResource("DATASET", 1L)).thenReturn(List.of(existing));

        AsyncTask result = service.parseDataset(1L, authentication);

        assertEquals(50L, result.getId());
        verify(asyncTaskService, never()).createTask(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("parseDataset - 已存在 COMPLETED 任务返回该任务")
    void parseDataset_existingCompletedTask() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test");
        dataset.setStatus("UPLOADED");
        AsyncTask existing = new AsyncTask();
        existing.setId(50L);
        existing.setTaskType("DATASET_PARSE");
        existing.setStatus("COMPLETED");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(asyncTaskService.getTasksByResource("DATASET", 1L)).thenReturn(List.of(existing));

        AsyncTask result = service.parseDataset(1L, authentication);

        assertEquals(50L, result.getId());
        verify(asyncTaskService, never()).createTask(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("parseDataset - 已 PARSED 状态抛异常")
    void parseDataset_alreadyParsed_throws() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test");
        dataset.setStatus("PARSED");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(asyncTaskService.getTasksByResource("DATASET", 1L)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.parseDataset(1L, authentication));
    }

    @Test
    @DisplayName("parseDataset - executeTask 异常不影响任务创建")
    void parseDataset_executeException_doesNotAffectTask() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test");
        dataset.setStatus("UPLOADED");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(asyncTaskService.getTasksByResource("DATASET", 1L)).thenReturn(List.of());
        AsyncTask task = new AsyncTask();
        task.setId(10L);
        when(asyncTaskService.createTask(any(), any(), any(), any(), any(), any())).thenReturn(task);
        doThrow(new RuntimeException("execute error")).when(asyncTaskService).executeTask(10L);

        AsyncTask result = service.parseDataset(1L, authentication);

        assertNotNull(result);
        assertEquals(10L, result.getId());
    }

    @Test
    @DisplayName("updateDataset - 成功更新")
    void updateDataset_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("old");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Dataset updateRequest = new Dataset();
        updateRequest.setName("new name");
        updateRequest.setDescription("new description");

        service.updateDataset(1L, updateRequest, authentication);

        assertEquals("new name", dataset.getName());
        assertEquals("new description", dataset.getDescription());
        assertNotNull(dataset.getUpdatedAt());
        verify(datasetMapper).updateById(dataset);
        verify(accessValidator).validateDatasetOwnership(1L, 1L);
    }

    @Test
    @DisplayName("updateDataset - 仅更新 name")
    void updateDataset_onlyName() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("old");
        dataset.setDescription("keep");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        Dataset updateRequest = new Dataset();
        updateRequest.setName("new name");

        service.updateDataset(1L, updateRequest, authentication);

        assertEquals("new name", dataset.getName());
        assertEquals("keep", dataset.getDescription());
        verify(datasetMapper).updateById(dataset);
    }

    @Test
    @DisplayName("updateDataset - 数据集不存在抛异常")
    void updateDataset_notFound_throws() {
        when(datasetMapper.selectById(99L)).thenReturn(null);

        Dataset updateRequest = new Dataset();
        updateRequest.setName("new");

        assertThrows(BusinessException.class, () -> service.updateDataset(99L, updateRequest, authentication));
    }

    @Test
    @DisplayName("updateDataset - 空更新请求")
    void updateDataset_emptyRequest() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("original");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        service.updateDataset(1L, new Dataset(), authentication);

        assertEquals("original", dataset.getName());
        verify(datasetMapper).updateById(dataset);
    }
}
