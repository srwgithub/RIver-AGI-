package com.river.agi.collection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.collection.entity.CollectionTask;
import com.river.agi.collection.mapper.CollectionTaskMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.annotation.entity.Annotation;
import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.mapper.AnnotationMapper;
import com.river.agi.annotation.mapper.AnnotationTaskMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.LocalStorageService;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.dataset.service.DatasetParserService;
import com.river.agi.media.entity.MediaAnnotation;
import com.river.agi.media.mapper.MediaAnnotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import com.opencsv.CSVWriter;

@Service
@RequiredArgsConstructor
public class CollectionTaskService {
    private final CollectionTaskMapper taskMapper;
    private final DatasetMapper datasetMapper;
    private final LocalStorageService localStorageService;
    private final SecurityUtils securityUtils;
    private final DatasetDataReaderService dataReader;
    private final DatasetParserService datasetParserService;
    private final MediaAnnotationMapper mediaAnnotationMapper;
    private final AnnotationTaskMapper annotationTaskMapper;
    private final AnnotationMapper annotationMapper;

    public PageResult<CollectionTask> list(int page, int size) {
        var result = taskMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<CollectionTask>().orderByDesc(CollectionTask::getCreatedAt));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public CollectionTask create(CollectionTask task, Authentication authentication) {
        if (task.getName() == null || task.getName().isBlank()) throw new BusinessException("任务名称不能为空");
        if (task.getSourceType() == null || task.getSourceType().isBlank()) throw new BusinessException("请选择数据源类型");
        if (task.getDatasetId() != null && datasetMapper.selectById(task.getDatasetId()) == null) throw new BusinessException("数据集不存在");
        // A dataset task with a selected label schema is ready for annotation;
        // media-only tasks remain drafts until a media item is attached.
        task.setStatus(task.getDatasetId() != null && task.getLabelSchemaId() != null ? "READY" : "DRAFT");
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
        if (task.getDatasetId() != null) {
            // The collection and annotation pages use separate task records. Keep
            // their progress synchronized through the shared dataset id.
            AnnotationTask annotationTask = annotationTaskMapper.selectOne(
                    new LambdaQueryWrapper<AnnotationTask>()
                            .eq(AnnotationTask::getDatasetId, task.getDatasetId())
                            .eq(AnnotationTask::getDeleted, 0)
                            .orderByDesc(AnnotationTask::getCreatedAt)
                            .last("LIMIT 1"));
            if (annotationTask != null) {
                completed = Math.toIntExact(annotationMapper.selectCount(
                        new LambdaQueryWrapper<Annotation>()
                                .eq(Annotation::getTaskId, annotationTask.getId())
                                .eq(Annotation::getDeleted, 0)
                                .in(Annotation::getStatus,
                                        Annotation.Status.SUBMITTED.name(),
                                        Annotation.Status.IN_REVIEW.name(),
                                        Annotation.Status.APPROVED.name(),
                                        Annotation.Status.ARBITRATED.name(),
                                        Annotation.Status.PUBLISHED.name())));
                if (completed > 0 && completed < total) task.setStatus("RUNNING");
                if (total > 0 && completed >= total) task.setStatus("COMPLETED");
            }
        } else if (task.getId() != null) {
            completed = Math.toIntExact(mediaAnnotationMapper.selectCount(
                    new LambdaQueryWrapper<MediaAnnotation>()
                            .eq(MediaAnnotation::getTaskId, task.getId())
                            .eq(MediaAnnotation::getStatus, "COMPLETED")));
        }
        task.setTotalItems(total);
        // Never expose a count larger than the task denominator, even if an
        // older pre-annotation run left duplicate annotation records behind.
        task.setCompletedItems(total > 0 ? Math.min(Math.max(completed, 0), total) : Math.max(completed, 0));
        // Media tasks use completed media items as their denominator. Dataset task
        // progress is synchronized from the annotation records above.
        if (total > 0 && completed >= total && task.getDatasetId() == null) task.setStatus("COMPLETED");
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

    public void delete(Long id) {
        get(id);
        mediaAnnotationMapper.delete(new LambdaQueryWrapper<MediaAnnotation>().eq(MediaAnnotation::getTaskId, id));
        taskMapper.deleteById(id);
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
        // Return a bounded sample of the actual cleaned rows so the import page can
        // show the result immediately instead of displaying only aggregate counts.
        Set<String> outputSeen = new HashSet<>();
        List<Map<String, String>> previewRows = new ArrayList<>();
        for (Map<String, String> row : input) {
            boolean empty = row.values().stream().allMatch(v -> v == null || v.isBlank());
            String fingerprint = toJson(row);
            boolean duplicate = !outputSeen.add(fingerprint);
            boolean invalid = validateFormat && row.values().stream().anyMatch(v -> v != null && v.length() > 10000);
            if ((removeEmpty && empty) || (removeDuplicate && duplicate) || (validateFormat && invalid)) continue;
            if (previewRows.size() < 50) previewRows.add(row);
        }
        summary.put("inputRows", rows);
        summary.put("inputColumns", columns);
        summary.put("duplicateRows", duplicateRows);
        summary.put("emptyRows", emptyRows);
        summary.put("invalidRows", invalidRows);
        summary.put("outputRows", Math.max(0, rows - removed));
        summary.put("previewRows", previewRows);
        summary.put("previewFields", input.isEmpty() ? List.of() : new ArrayList<>(input.get(0).keySet()));
        summary.put("actions", config == null ? Map.of() : config);
        task.setCleaningConfigJson(toJson(config == null ? Map.of() : config));
        task.setCleaningSummaryJson(toJson(summary));
        task.setTotalItems(rows);
        task.setStatus("CLEANED");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return summary;
    }

    public Map<String, Object> applyCleaning(Long id, Map<String, Object> config, Authentication authentication) {
        CollectionTask task = get(id);
        Dataset source = task.getDatasetId() == null ? null : datasetMapper.selectById(task.getDatasetId());
        if (source == null) throw new BusinessException("任务关联的数据集不存在");
        List<Map<String, String>> input = dataReader.readRows(source);
        Map<String, Object> options = config == null ? Map.of() : config;
        boolean removeEmpty = Boolean.TRUE.equals(options.get("removeEmpty"));
        boolean removeDuplicate = Boolean.TRUE.equals(options.get("removeDuplicate"));
        boolean validateFormat = Boolean.TRUE.equals(options.get("validateFormat"));
        Set<String> seen = new HashSet<>();
        List<Map<String, String>> output = new ArrayList<>();
        int emptyRows = 0;
        int duplicateRows = 0;
        int invalidRows = 0;
        for (Map<String, String> row : input) {
            boolean empty = row.values().stream().allMatch(v -> v == null || v.isBlank());
            if (empty) emptyRows++;
            String fingerprint = toJson(row);
            boolean duplicate = !seen.add(fingerprint);
            if (duplicate) duplicateRows++;
            boolean invalid = validateFormat && row.values().stream().anyMatch(v -> v != null && v.length() > 10000);
            if (invalid) invalidRows++;
            if ((removeEmpty && empty) || (removeDuplicate && duplicate) || (validateFormat && invalid)) continue;
            output.add(row);
        }
        if (input.isEmpty()) throw new BusinessException("数据集没有可清洗的数据");
        List<String> headers = new ArrayList<>(input.get(0).keySet());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8))) {
            writer.writeNext(headers.toArray(String[]::new));
            for (Map<String, String> row : output) {
                writer.writeNext(headers.stream().map(h -> Optional.ofNullable(row.get(h)).orElse("")).toArray(String[]::new));
            }
        } catch (Exception e) {
            throw new BusinessException("清洗结果文件生成失败: " + e.getMessage());
        }
        String filename = localStorageService.writeFile(bytes.toByteArray(), source.getName() + "_cleaned.csv");
        Dataset cleaned = new Dataset();
        cleaned.setName(source.getName() + "_cleaned.csv");
        cleaned.setFileType("csv");
        cleaned.setFilePath(filename);
        cleaned.setFileUrl(localStorageService.fileUrl(filename));
        cleaned.setFileSize((long) bytes.size());
        cleaned.setStatus("UPLOADED");
        cleaned.setCreatedBy(securityUtils.getCurrentUserId(authentication));
        cleaned.setCreatedAt(LocalDateTime.now());
        cleaned.setUpdatedAt(LocalDateTime.now());
        datasetMapper.insert(cleaned);
        datasetParserService.parseDataset(cleaned.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceDatasetId", source.getId());
        result.put("outputDatasetId", cleaned.getId());
        result.put("inputRows", input.size());
        result.put("outputRows", output.size());
        result.put("emptyRows", emptyRows);
        result.put("duplicateRows", duplicateRows);
        result.put("invalidRows", invalidRows);
        result.put("fileUrl", cleaned.getFileUrl());
        task.setCleaningConfigJson(toJson(options));
        task.setCleaningSummaryJson(toJson(result));
        task.setTotalItems(output.size());
        task.setStatus("CLEANED");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return result;
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
