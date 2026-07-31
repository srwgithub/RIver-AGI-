package com.river.agi.annotation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.annotation.entity.Annotation;
import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.entity.AnnotationQualityRule;
import com.river.agi.annotation.entity.LabelSchema;
import com.river.agi.annotation.mapper.AnnotationMapper;
import com.river.agi.annotation.mapper.AnnotationTaskMapper;
import com.river.agi.annotation.mapper.LabelSchemaMapper;
import com.river.agi.annotation.mapper.AnnotationQualityRuleMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.annotation.AuditOperation;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnnotationService {
    
    private final LabelSchemaMapper labelSchemaMapper;
    private final AnnotationTaskMapper annotationTaskMapper;
    private final AnnotationMapper annotationMapper;
    private final DatasetMapper datasetMapper;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private final DatasetDataReaderService dataReader;
    private final AnnotationQualityRuleMapper qualityRuleMapper;

    @Autowired
    public AnnotationService(LabelSchemaMapper labelSchemaMapper,
                              AnnotationTaskMapper annotationTaskMapper,
                              AnnotationMapper annotationMapper,
                              DatasetMapper datasetMapper,
                              SecurityUtils securityUtils,
                              ObjectMapper objectMapper,
                              DatasetDataReaderService dataReader,
                              AnnotationQualityRuleMapper qualityRuleMapper) {
        this.labelSchemaMapper = labelSchemaMapper;
        this.annotationTaskMapper = annotationTaskMapper;
        this.annotationMapper = annotationMapper;
        this.datasetMapper = datasetMapper;
        this.securityUtils = securityUtils;
        this.objectMapper = objectMapper;
        this.dataReader = dataReader;
        this.qualityRuleMapper = qualityRuleMapper;
    }

    // Keep the legacy unit-test constructor compatible while Spring uses the full constructor.
    public AnnotationService(LabelSchemaMapper labelSchemaMapper,
                              AnnotationTaskMapper annotationTaskMapper,
                              AnnotationMapper annotationMapper,
                              DatasetMapper datasetMapper,
                              SecurityUtils securityUtils,
                              ObjectMapper objectMapper,
                              DatasetDataReaderService dataReader) {
        this(labelSchemaMapper, annotationTaskMapper, annotationMapper, datasetMapper,
                securityUtils, objectMapper, dataReader, null);
    }
    
    // Label Schema CRUD
    
    @AuditOperation(action = "CREATE_LABEL_SCHEMA", resourceType = "LABEL_SCHEMA", description = "Create label schema")
    public LabelSchema createLabelSchema(LabelSchema schema) {
        schema.setCreatedAt(LocalDateTime.now());
        schema.setUpdatedAt(LocalDateTime.now());
        labelSchemaMapper.insert(schema);
        return schema;
    }
    
    public PageResult<LabelSchema> getLabelSchemas(int page, int size) {
        ensureDefaultLabelSchema();
        Page<LabelSchema> pageRequest = new Page<>(page, size);
        Page<LabelSchema> pageResult = labelSchemaMapper.selectPage(pageRequest, 
                new LambdaQueryWrapper<LabelSchema>()
                        .isNull(LabelSchema::getParentId)
                        .eq(LabelSchema::getDeleted, 0)
                        .orderByAsc(LabelSchema::getSortOrder));
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }

    /**
     * A fresh installation should be usable immediately. Seed one idempotent
     * root schema and its child labels when the table has no root schema.
     */
    private void ensureDefaultLabelSchema() {
        LabelSchema existing = labelSchemaMapper.selectOne(new LambdaQueryWrapper<LabelSchema>()
                .eq(LabelSchema::getCode, "DEFAULT_DATA_CLASSIFICATION")
                .isNull(LabelSchema::getParentId)
                .eq(LabelSchema::getDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) return;

        LocalDateTime now = LocalDateTime.now();
        LabelSchema root = new LabelSchema();
        root.setTenantId(1L);
        root.setName("通用数据分类");
        root.setCode("DEFAULT_DATA_CLASSIFICATION");
        root.setDescription("系统默认数据分类标签体系");
        root.setSortOrder(0);
        root.setCreatedAt(now);
        root.setUpdatedAt(now);
        root.setDeleted(0);
        labelSchemaMapper.insert(root);

        String[][] children = {
                {"phone", "手机号"}, {"email", "邮箱"}, {"id_card", "身份证号"},
                {"bank_card", "银行卡号"}, {"date", "日期"}, {"amount", "金额"},
                {"gender", "性别"}, {"address", "地址"}
        };
        for (int i = 0; i < children.length; i++) {
            LabelSchema child = new LabelSchema();
            child.setTenantId(1L);
            child.setName(children[i][1]);
            child.setCode(children[i][0]);
            child.setParentId(root.getId());
            child.setSortOrder(i);
            child.setCreatedAt(now);
            child.setUpdatedAt(now);
            child.setDeleted(0);
            labelSchemaMapper.insert(child);
        }
    }
    
    public LabelSchema getLabelSchema(Long id) {
        LabelSchema schema = labelSchemaMapper.selectById(id);
        if (schema == null) {
            throw new BusinessException("Label schema not found");
        }
        return schema;
    }

    public List<LabelSchema> getChildLabels(Long parentId) {
        return labelSchemaMapper.selectByParentId(parentId);
    }

    public List<AnnotationQualityRule> getQualityRules() {
        ensureDefaultQualityRules();
        return qualityRuleMapper.selectList(new LambdaQueryWrapper<AnnotationQualityRule>()
                .eq(AnnotationQualityRule::getTenantId, 1L)
                .eq(AnnotationQualityRule::getDeleted, 0)
                .orderByAsc(AnnotationQualityRule::getPriority));
    }

    @Transactional
    public AnnotationQualityRule saveQualityRule(AnnotationQualityRule rule) {
        if (rule.getName() == null || rule.getName().isBlank() || rule.getCode() == null || rule.getCode().isBlank()) {
            throw new BusinessException("规则名称和编码不能为空");
        }
        rule.setTenantId(1L);
        rule.setDeleted(0);
        rule.setUpdatedAt(LocalDateTime.now());
        if (rule.getPriority() == null) rule.setPriority(100);
        if (rule.getEnabled() == null) rule.setEnabled(true);
        if (rule.getVersion() == null) rule.setVersion("1.0.0");
        if (rule.getId() == null) { rule.setCreatedAt(LocalDateTime.now()); qualityRuleMapper.insert(rule); }
        else qualityRuleMapper.updateById(rule);
        return rule;
    }

    @Transactional
    public void deleteQualityRule(Long id) {
        AnnotationQualityRule rule = qualityRuleMapper.selectById(id);
        if (rule == null) throw new BusinessException("质量规则不存在");
        rule.setDeleted(1);
        qualityRuleMapper.updateById(rule);
    }

    private void ensureDefaultQualityRules() {
        if (qualityRuleMapper.selectCount(new LambdaQueryWrapper<AnnotationQualityRule>()
                .eq(AnnotationQualityRule::getTenantId, 1L).eq(AnnotationQualityRule::getDeleted, 0)) > 0) return;
        AnnotationQualityRule labels = new AnnotationQualityRule();
        labels.setName("标签合法性"); labels.setCode("LABEL_IN_SCHEMA"); labels.setRuleType("LABEL_IN_SCHEMA");
        labels.setAction("REVIEW"); labels.setPriority(10); labels.setEnabled(true); labels.setTenantId(1L); labels.setDeleted(0);
        labels.setCreatedAt(LocalDateTime.now()); labels.setUpdatedAt(LocalDateTime.now()); qualityRuleMapper.insert(labels);
        AnnotationQualityRule confidence = new AnnotationQualityRule();
        confidence.setName("最低置信度"); confidence.setCode("MIN_CONFIDENCE"); confidence.setRuleType("MIN_CONFIDENCE");
        confidence.setThreshold(0.70); confidence.setAction("REVIEW"); confidence.setPriority(20); confidence.setEnabled(true);
        confidence.setTenantId(1L); confidence.setDeleted(0); confidence.setCreatedAt(LocalDateTime.now()); confidence.setUpdatedAt(LocalDateTime.now());
        qualityRuleMapper.insert(confidence);
    }
    
    public void deleteLabelSchema(Long id) {
        labelSchemaMapper.deleteById(id);
    }
    
    // Annotation Task CRUD
    
    @AuditOperation(action = "CREATE_ANNOTATION_TASK", resourceType = "ANNOTATION", description = "Create annotation task")
    public AnnotationTask createAnnotationTask(AnnotationTask task, Authentication authentication) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }
        
        task.setTotalRows(dataset.getRowCount());
        task.setCompletedRows(0);
        task.setStatus(AnnotationTask.Status.PENDING.name());
        task.setCreatedBy(securityUtils.getCurrentUserId(authentication));
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setReviewCount(0);
        task.setArbitrationCount(0);
        task.setPassRate(1.0);
        task.setConsistencyRate(1.0);
        
        annotationTaskMapper.insert(task);
        return task;
    }
    
    public PageResult<AnnotationTask> getAnnotationTasks(int page, int size) {
        Page<AnnotationTask> pageRequest = new Page<>(page, size);
        Page<AnnotationTask> pageResult = annotationTaskMapper.selectPage(pageRequest, 
                new LambdaQueryWrapper<AnnotationTask>().orderByDesc(AnnotationTask::getCreatedAt));
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    public AnnotationTask getAnnotationTask(Long id) {
        AnnotationTask task = annotationTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("Annotation task not found");
        }
        return task;
    }
    
    @AuditOperation(action = "DELETE_ANNOTATION_TASK", resourceType = "ANNOTATION", description = "Delete annotation task")
    public void deleteAnnotationTask(Long id) {
        annotationMapper.delete(new LambdaQueryWrapper<Annotation>().eq(Annotation::getTaskId, id));
        annotationTaskMapper.deleteById(id);
    }
    
    // Rule-based pre-annotation engine
    
    private static final List<AnnotationRule> PRE_ANNOTATION_RULES = Arrays.asList(
            new AnnotationRule("phone", Pattern.compile("^1[3-9]\\d{9}$"),
                    Arrays.asList("phone", "mobile", "phone_number", "电话", "手机"), BigDecimal.valueOf(0.95)),
            new AnnotationRule("email", Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"),
                    Arrays.asList("email", "e-mail", "邮箱", "email_address"), BigDecimal.valueOf(0.98)),
            new AnnotationRule("id_card", Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$"),
                    Arrays.asList("idcard", "id_card", "身份证", "id_number", "id_no"), BigDecimal.valueOf(0.99)),
            new AnnotationRule("bank_card", Pattern.compile("^[1-9]\\d{14,18}$"),
                    Arrays.asList("bank_card", "card_no", "银行卡", "bank_account"), BigDecimal.valueOf(0.90)),
            new AnnotationRule("date", Pattern.compile("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$"),
                    Arrays.asList("date", "created_date", "日期", "time", "timestamp"), BigDecimal.valueOf(0.85)),
            new AnnotationRule("amount", Pattern.compile("^\\d+(\\.\\d{1,2})?$"),
                    Arrays.asList("amount", "price", "cost", "金额", "价格", "sales", "revenue"), BigDecimal.valueOf(0.70)),
            new AnnotationRule("gender", Pattern.compile("^(男|女|M|F|male|female)$"),
                    Arrays.asList("gender", "sex", "性别"), BigDecimal.valueOf(0.90)),
            new AnnotationRule("address", Pattern.compile(".{10,}"),
                    Arrays.asList("address", "地址", "city", "province", "地址"), BigDecimal.valueOf(0.60))
    );
    
    @AuditOperation(action = "PRE_ANNOTATE", resourceType = "ANNOTATION", description = "Pre-annotate data with rule engine")
    @Transactional
    public void preAnnotate(Long taskId) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Annotation task not found");
        }
        
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }
        
        List<LabelSchema> labels = labelSchemaMapper.selectList(
                new LambdaQueryWrapper<LabelSchema>()
                        .eq(LabelSchema::getParentId, task.getLabelSchemaId())
        );
        
        List<Map<String, String>> dataRows;
        try {
            dataRows = dataReader.readRows(dataset);
        } catch (Exception e) {
            log.error("Failed to read dataset for pre-annotation: {}", dataset.getId(), e);
            throw new BusinessException("Failed to read dataset data: " + e.getMessage());
        }
        
        int preAnnotatedCount = 0;
        int maxRows = Math.min(500, dataRows.size());
        
        for (int i = 0; i < maxRows; i++) {
            Map<String, String> row = dataRows.get(i);
            Annotation annotation = annotateRow(taskId, task.getDatasetId(), i, row, labels);
            
            if (annotation != null) {
                annotationMapper.insert(annotation);
                preAnnotatedCount++;
            }
        }
        
        task.setStatus(AnnotationTask.Status.PRE_ANNOTATED.name());
        task.setCompletedRows(preAnnotatedCount);
        task.setUpdatedAt(LocalDateTime.now());
        annotationTaskMapper.updateById(task);
        
        log.info("Rule-based pre-annotation completed for task: {}, annotated: {} rows", taskId, preAnnotatedCount);
    }
    
    private Annotation annotateRow(Long taskId, Long datasetId, int rowIndex, 
                                     Map<String, String> row, List<LabelSchema> labels) {
        String bestLabelCode = null;
        String bestLabelName = null;
        BigDecimal bestConfidence = BigDecimal.ZERO;
        
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String columnName = entry.getKey().toLowerCase();
            String cellValue = entry.getValue() != null ? entry.getValue() : "";
            
            if (cellValue.isEmpty()) continue;
            
            for (AnnotationRule rule : PRE_ANNOTATION_RULES) {
                boolean columnMatch = rule.columnKeywords.stream()
                        .anyMatch(kw -> columnName.contains(kw.toLowerCase()));
                boolean valueMatch = rule.valuePattern.matcher(cellValue).matches();
                
                if (columnMatch && valueMatch) {
                    BigDecimal confidence = rule.baseConfidence.add(BigDecimal.valueOf(0.05));
                    if (confidence.compareTo(bestConfidence) > 0) {
                        bestConfidence = confidence;
                        bestLabelCode = rule.label;
                        bestLabelName = getLabelDisplayName(rule.label, labels);
                    }
                } else if (columnMatch) {
                    BigDecimal confidence = rule.baseConfidence.multiply(BigDecimal.valueOf(0.5));
                    if (confidence.compareTo(bestConfidence) > 0) {
                        bestConfidence = confidence;
                        bestLabelCode = rule.label;
                        bestLabelName = getLabelDisplayName(rule.label, labels);
                    }
                } else if (valueMatch && bestConfidence.compareTo(BigDecimal.valueOf(0.5)) < 0) {
                    BigDecimal confidence = rule.baseConfidence.multiply(BigDecimal.valueOf(0.6));
                    if (confidence.compareTo(bestConfidence) > 0) {
                        bestConfidence = confidence;
                        bestLabelCode = rule.label;
                        bestLabelName = getLabelDisplayName(rule.label, labels);
                    }
                }
            }
        }
        
        if (bestLabelCode == null) {
            return null;
        }
        
        Annotation annotation = new Annotation();
        annotation.setTaskId(taskId);
        annotation.setDatasetId(datasetId);
        annotation.setRowIndex(rowIndex);
        annotation.setLabelCode(bestLabelCode);
        annotation.setLabelName(bestLabelName);
        annotation.setStatus(Annotation.Status.PRE_ANNOTATED.name());
        annotation.setAnnotationType(Annotation.AnnotationType.PRE_ANNOTATION.name());
        annotation.setConfidence(bestConfidence.setScale(4, RoundingMode.HALF_UP));
        annotation.setModelSource("RULE_ENGINE_V2");
        annotation.setRuleVersion("v2.0.0");
        annotation.setIsCorrected(false);
        annotation.setAnnotatedAt(LocalDateTime.now());
        
        return annotation;
    }
    
    private String getLabelDisplayName(String ruleLabel, List<LabelSchema> labels) {
        if (labels != null && !labels.isEmpty()) {
            for (LabelSchema label : labels) {
                if (ruleLabel.equalsIgnoreCase(label.getCode()) || 
                    (label.getName() != null && ruleLabel.equalsIgnoreCase(label.getName()))) {
                    return label.getName();
                }
            }
        }
        return ruleLabel;
    }
    
    private record AnnotationRule(String label, Pattern valuePattern, 
                                    List<String> columnKeywords, BigDecimal baseConfidence) {
    }
    
    // Annotation submission
    
    @AuditOperation(action = "SUBMIT_ANNOTATION", resourceType = "ANNOTATION", description = "Submit annotation")
    @Transactional
    public Annotation submitAnnotation(Long annotationId, String labelCode, String labelName, 
                                        String comment, Authentication authentication) {
        Annotation annotation = annotationMapper.selectById(annotationId);
        if (annotation == null) {
            throw new BusinessException("Annotation not found");
        }
        
        Long userId = securityUtils.getCurrentUserId(authentication);

        if (labelCode == null || labelCode.isBlank()) {
            throw new BusinessException("标签不能为空");
        }
        // Reject labels outside the task schema before they enter the quality pipeline.
        AnnotationTask task = annotationTaskMapper.selectById(annotation.getTaskId());
        if (task == null) throw new BusinessException("Annotation task not found");
        long validLabels = labelSchemaMapper.selectCount(new LambdaQueryWrapper<LabelSchema>()
                .eq(LabelSchema::getParentId, task.getLabelSchemaId())
                .eq(LabelSchema::getCode, labelCode)
                .eq(LabelSchema::getDeleted, 0));
        if (validLabels == 0) throw new BusinessException("标签不属于当前标签体系");
        
        if (Annotation.Status.PRE_ANNOTATED.name().equals(annotation.getStatus()) && 
            !labelCode.equals(annotation.getLabelCode())) {
            annotation.setOriginalLabelCode(annotation.getLabelCode());
            annotation.setOriginalConfidence(annotation.getConfidence());
            annotation.setIsCorrected(true);
            annotation.setCorrectedAt(LocalDateTime.now());
            annotation.setAnnotationType(Annotation.AnnotationType.CORRECTION.name());
        }
        
        annotation.setLabelCode(labelCode);
        annotation.setLabelName(labelName);
        annotation.setComment(comment);
        annotation.setStatus(Annotation.Status.SUBMITTED.name());
        annotation.setAnnotatedBy(userId);
        annotation.setAnnotatedAt(LocalDateTime.now());
        annotation.setConfidence(annotation.getConfidence() != null ? annotation.getConfidence() : BigDecimal.ONE);
        
        annotationMapper.updateById(annotation);
        
        updateTaskProgress(annotation.getTaskId());
        
        return annotation;
    }
    
    // Review
    
    @AuditOperation(action = "REVIEW_ANNOTATION", resourceType = "ANNOTATION", description = "Review annotation")
    @Transactional
    public Annotation reviewAnnotation(Long annotationId, String reviewComment, 
                                        boolean approved, Authentication authentication) {
        Annotation annotation = annotationMapper.selectById(annotationId);
        if (annotation == null) {
            throw new BusinessException("Annotation not found");
        }
        
        annotation.setStatus(approved ? Annotation.Status.APPROVED.name() : Annotation.Status.REJECTED.name());
        annotation.setReviewComment(reviewComment);
        annotation.setReviewedBy(securityUtils.getCurrentUserId(authentication));
        annotation.setReviewedAt(LocalDateTime.now());
        
        annotationMapper.updateById(annotation);
        
        AnnotationTask task = annotationTaskMapper.selectById(annotation.getTaskId());
        if (task != null) {
            task.setReviewCount((task.getReviewCount() == null ? 0 : task.getReviewCount()) + 1);
            if (!approved) task.setStatus(AnnotationTask.Status.IN_PROGRESS.name());
            task.setUpdatedAt(LocalDateTime.now());
            annotationTaskMapper.updateById(task);
        }
        
        return annotation;
    }
    
    // Arbitration
    
    @AuditOperation(action = "ARBITRATE_ANNOTATION", resourceType = "ANNOTATION", description = "Arbitrate annotation")
    @Transactional
    public Annotation arbitrateAnnotation(Long annotationId, String labelCode, String labelName, 
                                           String comment, Authentication authentication) {
        Annotation annotation = annotationMapper.selectById(annotationId);
        if (annotation == null) {
            throw new BusinessException("Annotation not found");
        }
        
        annotation.setLabelCode(labelCode);
        annotation.setLabelName(labelName);
        annotation.setComment(comment);
        annotation.setStatus(Annotation.Status.ARBITRATED.name());
        annotation.setAnnotationType(Annotation.AnnotationType.ARBITRATION.name());
        annotation.setReviewedBy(securityUtils.getCurrentUserId(authentication));
        annotation.setReviewedAt(LocalDateTime.now());
        annotation.setConfidence(BigDecimal.ONE);
        
        annotationMapper.updateById(annotation);
        
        AnnotationTask task = annotationTaskMapper.selectById(annotation.getTaskId());
        if (task != null) {
            task.setArbitrationCount((task.getArbitrationCount() == null ? 0 : task.getArbitrationCount()) + 1);
            task.setUpdatedAt(LocalDateTime.now());
            annotationTaskMapper.updateById(task);
        }
        
        return annotation;
    }
    
    // Get annotations for a task
    
    public List<Annotation> getAnnotations(Long taskId) {
        return annotationMapper.selectByTaskId(taskId);
    }
    
    // Get annotation quality metrics
    
    public Map<String, Object> getAnnotationQualityMetrics(Long taskId) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Annotation task not found");
        }
        
        List<Annotation> annotations = annotationMapper.selectByTaskId(taskId);
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("taskId", taskId);
        metrics.put("totalAnnotations", annotations.size());
        
        long preAnnotatedCount = annotations.stream()
                .filter(a -> Annotation.Status.PRE_ANNOTATED.name().equals(a.getStatus()))
                .count();
        long submittedCount = annotations.stream()
                .filter(a -> Annotation.Status.SUBMITTED.name().equals(a.getStatus()) || 
                            Annotation.Status.APPROVED.name().equals(a.getStatus()) ||
                            Annotation.Status.ARBITRATED.name().equals(a.getStatus()))
                .count();
        long approvedCount = annotations.stream()
                .filter(a -> Annotation.Status.APPROVED.name().equals(a.getStatus()))
                .count();
        long rejectedCount = annotations.stream()
                .filter(a -> Annotation.Status.REJECTED.name().equals(a.getStatus()))
                .count();
        long correctedCount = annotations.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsCorrected()))
                .count();
        long arbitratedCount = annotations.stream()
                .filter(a -> Annotation.Status.ARBITRATED.name().equals(a.getStatus()))
                .count();
        
        metrics.put("preAnnotatedCount", preAnnotatedCount);
        metrics.put("submittedCount", submittedCount);
        metrics.put("approvedCount", approvedCount);
        metrics.put("rejectedCount", rejectedCount);
        metrics.put("correctedCount", correctedCount);
        metrics.put("arbitratedCount", arbitratedCount);
        
        double submitRate = annotations.isEmpty() ? 0.0 : (double) submittedCount / annotations.size();
        double approveRate = submittedCount > 0 ? (double) approvedCount / submittedCount : 1.0;
        double correctionRate = preAnnotatedCount > 0 ? (double) correctedCount / preAnnotatedCount : 0.0;
        
        metrics.put("submitRate", submitRate);
        metrics.put("approveRate", approveRate);
        metrics.put("correctionRate", correctionRate);
        
        double avgConfidence = annotations.stream()
                .filter(a -> a.getConfidence() != null)
                .map(Annotation::getConfidence)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, annotations.stream().filter(a -> a.getConfidence() != null).count())), 4, RoundingMode.HALF_UP)
                .doubleValue();
        metrics.put("averageConfidence", avgConfidence);
        
        double qualityScore = calculateQualityScore(approveRate, correctionRate, avgConfidence, submitRate);
        metrics.put("qualityScore", qualityScore);
        metrics.put("qualityLevel", getQualityLevel(qualityScore));
        metrics.put("publishable", qualityScore >= 0.80 && approveRate >= 0.80 && correctionRate <= 0.20);
        metrics.put("qualityWeights", Map.of("approveRate", 0.35, "correctionRate", 0.25,
                "confidence", 0.25, "submitRate", 0.15));
        
        return metrics;
    }
    
    // Calculate quality score
    
    private double calculateQualityScore(double approveRate, double correctionRate, 
                                          double avgConfidence, double submitRate) {
        double score = 0.0;
        score += approveRate * 0.35;
        score += (1.0 - correctionRate) * 0.25;
        score += avgConfidence * 0.25;
        score += submitRate * 0.15;
        return Math.min(1.0, Math.max(0.0, score));
    }
    
    private String getQualityLevel(double score) {
        if (score >= 0.9) return "EXCELLENT";
        if (score >= 0.8) return "GOOD";
        if (score >= 0.7) return "ACCEPTABLE";
        if (score >= 0.6) return "NEEDS_IMPROVEMENT";
        return "POOR";
    }
    
    // Publish annotation results
    
    @AuditOperation(action = "PUBLISH_ANNOTATION", resourceType = "ANNOTATION", description = "Publish annotation results")
    @Transactional
    public AnnotationTask publishAnnotationTask(Long taskId, Authentication authentication) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Annotation task not found");
        }
        
        Map<String, Object> qualityMetrics = getAnnotationQualityMetrics(taskId);
        if (!Boolean.TRUE.equals(qualityMetrics.get("publishable"))) {
            throw new BusinessException("质量门禁未通过：质量分需达到80%，通过率需达到80%，纠偏率不能超过20%");
        }
        task.setQualityScore((Double) qualityMetrics.get("qualityScore"));
        task.setQualityReportJson(toJson(qualityMetrics));
        task.setStatus(AnnotationTask.Status.PUBLISHED.name());
        task.setPublishVersion("v1.0");
        task.setPublishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        annotationTaskMapper.updateById(task);
        
        List<Annotation> annotations = annotationMapper.selectByTaskId(taskId);
        for (Annotation annotation : annotations) {
            if (!Annotation.Status.PUBLISHED.name().equals(annotation.getStatus())) {
                annotation.setStatus(Annotation.Status.PUBLISHED.name());
                annotationMapper.updateById(annotation);
            }
        }
        
        log.info("Annotation task published: {} with quality score: {}", taskId, task.getQualityScore());
        return task;
    }
    
    private void updateTaskProgress(Long taskId) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task != null) {
            long submittedCount = annotationMapper.selectCount(
                    new LambdaQueryWrapper<Annotation>()
                            .eq(Annotation::getTaskId, taskId)
                            .in(Annotation::getStatus, 
                                Annotation.Status.SUBMITTED.name(), 
                                Annotation.Status.APPROVED.name(), 
                                Annotation.Status.ARBITRATED.name())
            );
            task.setCompletedRows(Math.toIntExact(submittedCount));
            task.setStatus(AnnotationTask.Status.IN_PROGRESS.name());
            
            if (submittedCount == task.getTotalRows()) {
                task.setStatus(AnnotationTask.Status.COMPLETED.name());
                calculateAndUpdateQuality(task);
            }
            
            task.setUpdatedAt(LocalDateTime.now());
            annotationTaskMapper.updateById(task);
        }
    }
    
    private void calculateAndUpdateQuality(AnnotationTask task) {
        Map<String, Object> qualityMetrics = getAnnotationQualityMetrics(task.getId());
        task.setQualityScore((Double) qualityMetrics.get("qualityScore"));
        task.setPassRate((Double) qualityMetrics.get("approveRate"));
        task.setConsistencyRate(1.0 - (Double) qualityMetrics.get("correctionRate"));
    }
    
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            return "{}";
        }
    }
    
    // Annotation Assignment
    
    @AuditOperation(action = "ASSIGN_ANNOTATORS", resourceType = "ANNOTATION", description = "Assign annotators to task")
    @Transactional
    public AnnotationTask assignAnnotators(Long taskId, List<Long> annotatorIds, Authentication authentication) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Annotation task not found");
        }
        
        task.setAssignedAnnotators(annotatorIds.size());
        task.setUpdatedAt(LocalDateTime.now());
        annotationTaskMapper.updateById(task);
        
        log.info("Assigned {} annotators to task {}", annotatorIds.size(), taskId);
        return task;
    }
    
    // Get pending annotations for an annotator
    
    public PageResult<Annotation> getAnnotatorTasks(Long annotatorId, int page, int size) {
        Page<Annotation> pageRequest = new Page<>(page, size);
        Page<Annotation> pageResult = annotationMapper.selectPage(pageRequest,
                new LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getAnnotatedBy, annotatorId)
                        .in(Annotation::getStatus, 
                            Annotation.Status.PENDING.name(),
                            Annotation.Status.PRE_ANNOTATED.name(),
                            Annotation.Status.IN_REVIEW.name())
                        .orderByAsc(Annotation::getTaskId)
                        .orderByAsc(Annotation::getRowIndex));
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    // Quality Sampling (抽检)
    
    @AuditOperation(action = "QUALITY_SAMPLING", resourceType = "ANNOTATION", description = "Sample annotations for quality check")
    public Map<String, Object> performQualitySampling(Long taskId, double sampleRate, Authentication authentication) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Annotation task not found");
        }
        
        List<Annotation> allAnnotations = annotationMapper.selectByTaskId(taskId);
        int sampleSize = (int) Math.ceil(allAnnotations.size() * sampleRate);
        
        List<Annotation> sampledAnnotations = new ArrayList<>();
        List<Annotation> candidates = allAnnotations.stream()
                .filter(a -> Annotation.Status.SUBMITTED.name().equals(a.getStatus()) ||
                            Annotation.Status.APPROVED.name().equals(a.getStatus()))
                .collect(Collectors.toList());
        
        Collections.shuffle(candidates);
        sampledAnnotations = candidates.stream().limit(sampleSize).collect(Collectors.toList());
        
        int passedCount = 0;
        int failedCount = 0;
        List<Map<String, Object>> samplingResults = new ArrayList<>();
        
        for (Annotation annotation : sampledAnnotations) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("annotationId", annotation.getId());
            result.put("rowIndex", annotation.getRowIndex());
            result.put("originalLabel", annotation.getLabelCode());
            result.put("originalAnnotator", annotation.getAnnotatedBy());
            result.put("confidence", annotation.getConfidence());
            
            boolean passed = annotation.getConfidence() != null && 
                             annotation.getConfidence().compareTo(new BigDecimal("0.7")) >= 0;
            
            if (passed) {
                passedCount++;
                annotation.setStatus(Annotation.Status.APPROVED.name());
            } else {
                failedCount++;
                annotation.setStatus(Annotation.Status.IN_REVIEW.name());
            }
            annotation.setReviewedBy(securityUtils.getCurrentUserId(authentication));
            annotation.setReviewedAt(LocalDateTime.now());
            annotationMapper.updateById(annotation);
            
            result.put("sampled", true);
            result.put("passed", passed);
            result.put("sampledBy", securityUtils.getCurrentUserId(authentication));
            result.put("sampledAt", LocalDateTime.now());
            samplingResults.add(result);
        }
        
        Map<String, Object> samplingReport = new LinkedHashMap<>();
        samplingReport.put("taskId", taskId);
        samplingReport.put("totalAnnotations", allAnnotations.size());
        samplingReport.put("sampleRate", sampleRate);
        samplingReport.put("sampleSize", sampleSize);
        samplingReport.put("passedCount", passedCount);
        samplingReport.put("failedCount", failedCount);
        samplingReport.put("passRate", allAnnotations.isEmpty() ? 1.0 : (double) passedCount / sampleSize);
        samplingReport.put("samplingResults", samplingResults);
        
        log.info("Quality sampling completed for task {}: {}/{} passed", 
                taskId, passedCount, sampleSize);
        
        return samplingReport;
    }
    
    // Consistency Check (一致性检查)
    
    @AuditOperation(action = "CONSISTENCY_CHECK", resourceType = "ANNOTATION", description = "Check annotation consistency")
    public Map<String, Object> checkConsistency(Long taskId) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Annotation task not found");
        }
        
        List<Annotation> annotations = annotationMapper.selectByTaskId(taskId);
        
        Map<Integer, List<Annotation>> rowGroups = annotations.stream()
                .collect(Collectors.groupingBy(Annotation::getRowIndex));
        
        List<Map<String, Object>> inconsistencies = new ArrayList<>();
        int consistentRows = 0;
        int inconsistentRows = 0;
        
        for (Map.Entry<Integer, List<Annotation>> entry : rowGroups.entrySet()) {
            int rowIndex = entry.getKey();
            List<Annotation> rowAnnotations = entry.getValue();
            
            if (rowAnnotations.size() < 2) {
                consistentRows++;
                continue;
            }
            
            Set<String> uniqueLabels = rowAnnotations.stream()
                    .map(Annotation::getLabelCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            if (uniqueLabels.size() <= 1) {
                consistentRows++;
            } else {
                inconsistentRows++;
                
                Map<String, Object> inconsistency = new LinkedHashMap<>();
                inconsistency.put("rowIndex", rowIndex);
                inconsistency.put("uniqueLabels", uniqueLabels);
                inconsistency.put("annotations", rowAnnotations.stream()
                        .map(a -> {
                            Map<String, Object> detail = new LinkedHashMap<>();
                            detail.put("annotationId", a.getId());
                            detail.put("labelCode", a.getLabelCode());
                            detail.put("annotatedBy", a.getAnnotatedBy());
                            detail.put("confidence", a.getConfidence());
                            return detail;
                        })
                        .collect(Collectors.toList()));
                inconsistencies.add(inconsistency);
            }
        }
        
        double consistencyRate = rowGroups.isEmpty() ? 1.0 : (double) consistentRows / rowGroups.size();
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("totalRows", rowGroups.size());
        result.put("consistentRows", consistentRows);
        result.put("inconsistentRows", inconsistentRows);
        result.put("consistencyRate", round(consistencyRate, 4));
        result.put("inconsistencies", inconsistencies);
        result.put("recommendation", generateConsistencyRecommendation(consistencyRate));
        
        task.setConsistencyRate(consistencyRate);
        task.setUpdatedAt(LocalDateTime.now());
        annotationTaskMapper.updateById(task);
        
        log.info("Consistency check for task {}: rate={}", taskId, consistencyRate);
        
        return result;
    }

    /** Per-annotator metrics used for workload, quality and coaching decisions. */
    public List<Map<String, Object>> getAnnotatorPerformance(Long taskId) {
        if (annotationTaskMapper.selectById(taskId) == null) {
            throw new BusinessException("Annotation task not found");
        }
        List<Annotation> annotations = annotationMapper.selectByTaskId(taskId);
        Map<Long, List<Annotation>> groups = annotations.stream()
                .filter(a -> a.getAnnotatedBy() != null)
                .collect(Collectors.groupingBy(Annotation::getAnnotatedBy, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Annotation>> entry : groups.entrySet()) {
            List<Annotation> items = entry.getValue();
            long approved = items.stream().filter(a -> Annotation.Status.APPROVED.name().equals(a.getStatus())
                    || Annotation.Status.PUBLISHED.name().equals(a.getStatus())).count();
            long reviewed = items.stream().filter(a -> a.getReviewedAt() != null).count();
            long corrected = items.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrected())).count();
            double avgConfidence = items.stream().filter(a -> a.getConfidence() != null)
                    .mapToDouble(a -> a.getConfidence().doubleValue()).average().orElse(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("annotatorId", entry.getKey());
            row.put("total", items.size());
            row.put("submitted", items.stream().filter(a -> a.getAnnotatedAt() != null).count());
            row.put("reviewed", reviewed);
            row.put("approved", approved);
            row.put("rejected", items.stream().filter(a -> Annotation.Status.REJECTED.name().equals(a.getStatus())).count());
            row.put("corrected", corrected);
            row.put("approvalRate", reviewed == 0 ? 0 : round((double) approved / reviewed, 4));
            row.put("correctionRate", round((double) corrected / items.size(), 4));
            row.put("averageConfidence", round(avgConfidence, 4));
            row.put("performanceScore", round((reviewed == 0 ? 0 : (double) approved / reviewed) * 0.6
                    + (1 - (double) corrected / items.size()) * 0.25 + avgConfidence * 0.15, 4));
            result.add(row);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("performanceScore"), (Double) a.get("performanceScore")));
        return result;
    }

    /** Validate labels and confidence before review; failures are routed to another round. */
    @AuditOperation(action = "AUTO_VALIDATE_ANNOTATIONS", resourceType = "ANNOTATION", description = "Automatically validate annotation results")
    @Transactional
    public Map<String, Object> autoValidate(Long taskId) {
        AnnotationTask task = annotationTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("Annotation task not found");
        List<AnnotationQualityRule> rules = getQualityRules().stream().filter(r -> Boolean.TRUE.equals(r.getEnabled())).toList();
        Set<String> validCodes = labelSchemaMapper.selectByParentId(task.getLabelSchemaId()).stream()
                .map(LabelSchema::getCode).filter(Objects::nonNull).collect(Collectors.toSet());
        double minConfidence = rules.stream().filter(r -> "MIN_CONFIDENCE".equals(r.getRuleType()) && r.getThreshold() != null)
                .mapToDouble(AnnotationQualityRule::getThreshold).findFirst().orElse(0.70);
        boolean requireSchemaLabel = rules.stream().anyMatch(r -> "LABEL_IN_SCHEMA".equals(r.getRuleType()));
        List<Pattern> labelPatterns = rules.stream().filter(r -> "REGEX".equals(r.getRuleType()) && r.getPattern() != null && !r.getPattern().isBlank())
                .map(r -> {
                    try { return Pattern.compile(r.getPattern()); } catch (Exception ignored) { return null; }
                }).filter(Objects::nonNull).toList();
        List<Annotation> annotations = annotationMapper.selectByTaskId(taskId);
        int checked = 0, passed = 0, routedToReview = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (Annotation.Status.PUBLISHED.name().equals(annotation.getStatus())) continue;
            checked++;
            boolean valid = annotation.getLabelCode() != null
                    && (!requireSchemaLabel || validCodes.contains(annotation.getLabelCode()))
                    && labelPatterns.stream().allMatch(pattern -> pattern.matcher(annotation.getLabelCode()).matches())
                    && annotation.getConfidence() != null && annotation.getConfidence().doubleValue() >= minConfidence;
            if (valid) {
                passed++;
            } else {
                routedToReview++;
                annotation.setStatus(Annotation.Status.IN_REVIEW.name());
                annotationMapper.updateById(annotation);
                failures.add(Map.of("annotationId", annotation.getId(), "rowIndex", annotation.getRowIndex(),
                        "reason", annotation.getLabelCode() == null ? "标签为空" :
                                (requireSchemaLabel && !validCodes.contains(annotation.getLabelCode()) ? "标签不在当前体系" : "置信度低于" + (int) (minConfidence * 100) + "%")));
            }
        }
        task.setStatus(routedToReview > 0 ? AnnotationTask.Status.IN_REVIEW.name() : AnnotationTask.Status.COMPLETED.name());
        task.setUpdatedAt(LocalDateTime.now());
        annotationTaskMapper.updateById(task);
        return Map.of("taskId", taskId, "checked", checked, "passed", passed,
                "routedToReview", routedToReview, "failures", failures);
    }
    
    private String generateConsistencyRecommendation(double rate) {
        if (rate >= 0.95) {
            return "标注一致性优秀，可直接用于生产";
        } else if (rate >= 0.85) {
            return "标注一致性良好，建议对少量不一致数据进行仲裁";
        } else if (rate >= 0.7) {
            return "标注一致性一般，建议组织专家组进行仲裁";
        } else {
            return "标注一致性较差，建议重新标注或进行全面仲裁";
        }
    }
    
    private double round(double value, int scale) {
        return Math.round(value * Math.pow(10, scale)) / Math.pow(10, scale);
    }
}
