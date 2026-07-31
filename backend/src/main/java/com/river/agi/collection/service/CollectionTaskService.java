package com.river.agi.collection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.collection.entity.CollectionTask;
import com.river.agi.collection.mapper.CollectionTaskMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.LocalStorageService;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.media.entity.MediaAnnotation;
import com.river.agi.media.mapper.MediaAnnotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CollectionTaskService {
    private final CollectionTaskMapper taskMapper;
    private final DatasetMapper datasetMapper;
    private final LocalStorageService localStorageService;
    private final SecurityUtils securityUtils;
    private final DatasetDataReaderService dataReader;
    private final MediaAnnotationMapper mediaAnnotationMapper;

    public PageResult<CollectionTask> list(int page, int size) {
        var result = taskMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<CollectionTask>().orderByDesc(CollectionTask::getCreatedAt));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public CollectionTask create(CollectionTask task, Authentication authentication) {
        if (task.getName() == null || task.getName().isBlank()) throw new BusinessException("任务名称不能为空");
        if (task.getSourceType() == null || task.getSourceType().isBlank()) throw new BusinessException("请选择数据源类型");
        if (task.getDatasetId() != null && datasetMapper.selectById(task.getDatasetId()) == null) throw new BusinessException("数据集不存在");
        task.setStatus("DRAFT");
        task.setCollaborationMode(task.getCollaborationMode() == null ? "SINGLE" : task.getCollaborationMode());
        // Use the parsed dataset size as the initial denominator for progress.
        if (task.getTotalItems() == null || task.getTotalItems() == 0) {
            task.setTotalItems(task.getDatasetId() == null ? 0 :
                    Optional.ofNullable(datasetMapper.selectById(task.getDatasetId()))
                            .map(Dataset::getRowCount).orElse(0));
        }
        task.setCompletedItems(0);
        task.setCreatedBy(securityUtils.getCurrentUserId(authentication));
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    public CollectionTask refreshProgress(Long id) {
        CollectionTask task = get(id);
        int total = task.getTotalItems() == null ? 0 : task.getTotalItems();
        int completed = task.getCompletedItems() == null ? 0 : task.getCompletedItems();

        if (task.getDatasetId() != null && total == 0) {
            total = Optional.ofNullable(datasetMapper.selectById(task.getDatasetId()))
                    .map(Dataset::getRowCount).orElse(0);
        }
        if (task.getId() != null) {
            completed = Math.toIntExact(mediaAnnotationMapper.selectCount(
                    new LambdaQueryWrapper<MediaAnnotation>()
                            .eq(MediaAnnotation::getTaskId, task.getId())
                            .eq(MediaAnnotation::getStatus, "COMPLETED")));
        }
        task.setTotalItems(total);
        task.setCompletedItems(Math.min(completed, Math.max(total, completed)));
        if (total > 0 && completed >= total) task.setStatus("COMPLETED");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    public CollectionTask get(Long id) {
        CollectionTask task = taskMapper.selectById(id);
        if (task == null) throw new BusinessException("采集任务不存在");
        return task;
    }

    public CollectionTask update(Long id, CollectionTask updates) {
        CollectionTask task = get(id);
        if (updates.getName() != null) task.setName(updates.getName());
        if (updates.getLabelSchemaId() != null) task.setLabelSchemaId(updates.getLabelSchemaId());
        if (updates.getCleaningConfigJson() != null) task.setCleaningConfigJson(updates.getCleaningConfigJson());
        if (updates.getAnnotationRuleJson() != null) task.setAnnotationRuleJson(updates.getAnnotationRuleJson());
        if (updates.getCollaborationMode() != null) task.setCollaborationMode(updates.getCollaborationMode());
        if (updates.getAssignedAnnotators() != null) task.setAssignedAnnotators(updates.getAssignedAnnotators());
        if (updates.getStatus() != null) task.setStatus(updates.getStatus());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    public Map<String, Object> cleanPreview(Long id, Map<String, Object> config) {
        CollectionTask task = get(id);
        Dataset dataset = task.getDatasetId() == null ? null : datasetMapper.selectById(task.getDatasetId());
        Map<String, Object> summary = new LinkedHashMap<>();
        if (dataset == null) throw new BusinessException("任务关联的数据集不存在");
        List<Map<String, String>> input = dataReader.readRows(dataset);
        int rows = input.size();
        int columns = dataset == null || dataset.getColumnCount() == null ? 0 : dataset.getColumnCount();
        boolean removeEmpty = config != null && Boolean.TRUE.equals(config.get("removeEmpty"));
        boolean removeDuplicate = config != null && Boolean.TRUE.equals(config.get("removeDuplicate"));
        boolean validateFormat = config != null && Boolean.TRUE.equals(config.get("validateFormat"));
        int emptyRows = (int) input.stream().filter(row -> row.values().stream().allMatch(v -> v == null || v.isBlank())).count();
        Set<String> seen = new HashSet<>();
        int duplicateRows = 0;
        int invalidRows = 0;
        for (Map<String, String> row : input) {
            String fingerprint = toJson(row);
            if (!seen.add(fingerprint)) duplicateRows++;
            if (validateFormat && row.values().stream().anyMatch(v -> v != null && v.length() > 10000)) invalidRows++;
        }
        int removed = (removeEmpty ? emptyRows : 0) + (removeDuplicate ? duplicateRows : 0) + (validateFormat ? invalidRows : 0);
        summary.put("inputRows", rows);
        summary.put("inputColumns", columns);
        summary.put("duplicateRows", duplicateRows);
        summary.put("emptyRows", emptyRows);
        summary.put("invalidRows", invalidRows);
        summary.put("outputRows", Math.max(0, rows - removed));
        summary.put("actions", config == null ? Map.of() : config);
        task.setCleaningConfigJson(toJson(config == null ? Map.of() : config));
        task.setCleaningSummaryJson(toJson(summary));
        task.setTotalItems(rows);
        task.setStatus("CLEANED");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return summary;
    }

    public Map<String, Object> uploadMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择文件");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        String type = name.endsWith(".mp4") || name.endsWith(".mov") ? "VIDEO" : name.endsWith(".mp3") || name.endsWith(".wav") ? "AUDIO" : "IMAGE";
        return Map.of("name", file.getOriginalFilename(), "mediaType", type, "url", localStorageService.uploadFile(file), "size", file.getSize());
    }

    public MediaAnnotation attachMedia(Long taskId, String mediaType, String mediaUrl, Authentication authentication) {
        CollectionTask task = get(taskId);
        if (mediaType == null || mediaUrl == null || mediaUrl.isBlank()) throw new BusinessException("媒体类型和地址不能为空");
        MediaAnnotation annotation = new MediaAnnotation();
        annotation.setTaskId(taskId);
        annotation.setMediaType(mediaType);
        annotation.setMediaUrl(mediaUrl);
        annotation.setAnnotatedBy(securityUtils.getCurrentUserId(authentication));
        annotation.setStatus("PENDING");
        annotation.setCreatedAt(LocalDateTime.now());
        annotation.setUpdatedAt(LocalDateTime.now());
        mediaAnnotationMapper.insert(annotation);
        task.setMediaType(mediaType);
        task.setTotalItems((task.getTotalItems() == null ? 0 : task.getTotalItems()) + 1);
        task.setStatus("READY");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return annotation;
    }

    private String toJson(Object value) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }
}
