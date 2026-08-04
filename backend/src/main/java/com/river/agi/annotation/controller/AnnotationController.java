package com.river.agi.annotation.controller;

import com.river.agi.annotation.entity.Annotation;
import com.river.agi.annotation.entity.AnnotationTask;
import com.river.agi.annotation.entity.LabelSchema;
import com.river.agi.annotation.entity.AnnotationQualityRule;
import com.river.agi.annotation.entity.AnnotationHistory;
import com.river.agi.annotation.service.AnnotationService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Annotation", description = "Data annotation APIs")
public class AnnotationController {
    
    private final AnnotationService annotationService;

    @GetMapping({"/annotation-quality-rules", "/annotation-quality/rules"})
    @Operation(summary = "List annotation quality rules", description = "List configurable annotation validation rules")
    public ApiResponse<List<AnnotationQualityRule>> getQualityRules() {
        return ApiResponse.ok(annotationService.getQualityRules());
    }

    @PostMapping({"/annotation-quality-rules", "/annotation-quality/rules"})
    @Operation(summary = "Save annotation quality rule", description = "Create or update an annotation quality rule")
    public ApiResponse<AnnotationQualityRule> saveQualityRule(@RequestBody AnnotationQualityRule rule) {
        return ApiResponse.ok(annotationService.saveQualityRule(rule));
    }

    @DeleteMapping({"/annotation-quality-rules/{id}", "/annotation-quality/rules/{id}"})
    public ApiResponse<Void> deleteQualityRule(@PathVariable Long id) {
        annotationService.deleteQualityRule(id);
        return ApiResponse.ok(null);
    }
    
    // Label Schema endpoints
    
    @PostMapping("/label-schemas")
    @Operation(summary = "Create label schema", description = "Create a new label schema")
    public ApiResponse<LabelSchema> createLabelSchema(@RequestBody LabelSchema schema) {
        return ApiResponse.ok(annotationService.createLabelSchema(schema));
    }
    
    @GetMapping("/label-schemas")
    @Operation(summary = "List label schemas", description = "Get paginated list of label schemas")
    public ApiResponse<PageResult<LabelSchema>> getLabelSchemas(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(annotationService.getLabelSchemas(page, size));
    }
    
    @GetMapping("/label-schemas/{id}")
    @Operation(summary = "Get label schema", description = "Get label schema by ID")
    public ApiResponse<LabelSchema> getLabelSchema(@Parameter(description = "Schema ID") @PathVariable Long id) {
        return ApiResponse.ok(annotationService.getLabelSchema(id));
    }

    @GetMapping("/label-schemas/{id}/children")
    public ApiResponse<List<LabelSchema>> getChildLabels(@PathVariable Long id) {
        return ApiResponse.ok(annotationService.getChildLabels(id));
    }
    
    @DeleteMapping("/label-schemas/{id}")
    @Operation(summary = "Delete label schema", description = "Delete label schema by ID")
    public ApiResponse<Void> deleteLabelSchema(@Parameter(description = "Schema ID") @PathVariable Long id) {
        annotationService.deleteLabelSchema(id);
        return ApiResponse.ok(null);
    }
    
    // Annotation Task endpoints
    
    @PostMapping("/annotation-tasks")
    @Operation(summary = "Create annotation task", description = "Create a new annotation task and run pre-annotation")
    public ApiResponse<AnnotationTask> createAnnotationTask(
            @RequestBody AnnotationTask task,
            Authentication authentication) {
        AnnotationTask created = annotationService.createAnnotationTask(task, authentication);
        try {
            annotationService.preAnnotate(created.getId());
        } catch (Exception e) {
            // Pre-annotation failure should not block task creation
            // Task remains in PENDING status for manual annotation
        }
        return ApiResponse.ok(created);
    }
    
    @GetMapping("/annotation-tasks")
    @Operation(summary = "List annotation tasks", description = "Get paginated list of annotation tasks")
    public ApiResponse<PageResult<AnnotationTask>> getAnnotationTasks(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(annotationService.getAnnotationTasks(page, size));
    }
    
    @GetMapping("/annotation-tasks/{id}")
    @Operation(summary = "Get annotation task", description = "Get annotation task by ID")
    public ApiResponse<AnnotationTask> getAnnotationTask(@Parameter(description = "Task ID") @PathVariable Long id) {
        return ApiResponse.ok(annotationService.getAnnotationTask(id));
    }
    
    @PostMapping("/annotation-tasks/{id}/pre-annotate")
    @Operation(summary = "Pre-annotate", description = "Run AI pre-annotation for the task")
    public ApiResponse<Void> preAnnotate(@Parameter(description = "Task ID") @PathVariable Long id) {
        annotationService.preAnnotate(id);
        return ApiResponse.ok(null);
    }
    
    @DeleteMapping("/annotation-tasks/{id}")
    @Operation(summary = "Delete annotation task", description = "Delete annotation task by ID")
    public ApiResponse<Void> deleteAnnotationTask(@Parameter(description = "Task ID") @PathVariable Long id) {
        annotationService.deleteAnnotationTask(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/annotation-tasks/{id}/publish")
    @Operation(summary = "Publish annotation task", description = "Publish annotation results after quality gate validation")
    public ApiResponse<AnnotationTask> publishAnnotationTask(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(annotationService.publishAnnotationTask(id, authentication));
    }
    
    // Annotation endpoints
    
    @GetMapping("/annotation-tasks/{taskId}/annotations")
    @Operation(summary = "Get annotations", description = "Get all annotations for a task")
    public ApiResponse<List<Annotation>> getAnnotations(@Parameter(description = "Task ID") @PathVariable Long taskId) {
        return ApiResponse.ok(annotationService.getAnnotations(taskId));
    }

    @PostMapping("/annotation-tasks/{id}/export")
    @Operation(summary = "Export annotated dataset", description = "Export source rows with final annotation fields")
    public ApiResponse<Map<String, Object>> exportAnnotations(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(annotationService.exportAnnotations(id, authentication));
    }


    @GetMapping("/annotation-tasks/{taskId}/history")
    @Operation(summary = "Get annotation history", description = "Get persistent multi-round, review and arbitration history")
    public ApiResponse<List<AnnotationHistory>> getAnnotationHistory(@PathVariable Long taskId) {
        return ApiResponse.ok(annotationService.getAnnotationHistory(taskId));
    }
    
    @PostMapping("/annotations/{id}/submit")
    @Operation(summary = "Submit annotation", description = "Submit an annotation")
    public ApiResponse<Annotation> submitAnnotation(
            @Parameter(description = "Annotation ID") @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        return ApiResponse.ok(annotationService.submitAnnotation(
                id,
                request.get("labelCode"),
                request.get("labelName"),
                request.get("comment"),
                authentication
        ));
    }
    
    @PostMapping("/annotations/{id}/review")
    @Operation(summary = "Review annotation", description = "Review and approve/reject an annotation")
    public ApiResponse<Annotation> reviewAnnotation(
            @Parameter(description = "Annotation ID") @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Object approvedValue = request.get("approved");
        if (!(approvedValue instanceof Boolean)) {
            throw new IllegalArgumentException("approved 必须是布尔值");
        }
        return ApiResponse.ok(annotationService.reviewAnnotation(
                id,
                (String) request.get("reviewComment"),
                (Boolean) approvedValue,
                authentication
        ));
    }
    
    @PostMapping("/annotations/{id}/arbitrate")
    @Operation(summary = "Arbitrate annotation", description = "Resolve annotation conflicts")
    public ApiResponse<Annotation> arbitrateAnnotation(
            @Parameter(description = "Annotation ID") @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        return ApiResponse.ok(annotationService.arbitrateAnnotation(
                id,
                request.get("labelCode"),
                request.get("labelName"),
                request.get("comment"),
                authentication
        ));
    }
    
    // Assignment endpoints
    
    @PostMapping("/annotation-tasks/{id}/assign")
    @Operation(summary = "Assign annotators", description = "Assign annotators to the task")
    public ApiResponse<AnnotationTask> assignAnnotators(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @RequestBody Map<String, List<Long>> request,
            Authentication authentication) {
        return ApiResponse.ok(annotationService.assignAnnotators(id, request.get("annotatorIds"), authentication));
    }
    
    @GetMapping("/annotators/{annotatorId}/tasks")
    @Operation(summary = "Get annotator tasks", description = "Get pending annotations for an annotator")
    public ApiResponse<PageResult<Annotation>> getAnnotatorTasks(
            @Parameter(description = "Annotator ID") @PathVariable Long annotatorId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(annotationService.getAnnotatorTasks(annotatorId, page, size));
    }
    
    // Quality sampling endpoint
    
    @PostMapping("/annotation-tasks/{id}/quality-sampling")
    @Operation(summary = "Quality sampling", description = "Perform quality sampling on annotations")
    public ApiResponse<Map<String, Object>> performQualitySampling(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Object rawRate = request.getOrDefault("sampleRate", 0.1);
        double sampleRate = rawRate instanceof Number number
                ? number.doubleValue() : Double.parseDouble(String.valueOf(rawRate));
        Object rawDecisions = request.get("reviewDecisions");
        Map<String, Object> reviewDecisions = rawDecisions instanceof Map<?, ?> map
                ? map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()), Map.Entry::getValue))
                : Map.of();
        return ApiResponse.ok(annotationService.performQualitySampling(id, sampleRate, reviewDecisions, authentication));
    }
    
    // Consistency check endpoint
    
    @PostMapping("/annotation-tasks/{id}/consistency-check")
    @Operation(summary = "Consistency check", description = "Check annotation consistency")
    public ApiResponse<Map<String, Object>> checkConsistency(
            @Parameter(description = "Task ID") @PathVariable Long id) {
        return ApiResponse.ok(annotationService.checkConsistency(id));
    }
    
    // Quality metrics endpoint
    
    @GetMapping("/annotation-tasks/{id}/quality-metrics")
    @Operation(summary = "Quality metrics", description = "Get annotation quality metrics")
    public ApiResponse<Map<String, Object>> getQualityMetrics(
            @Parameter(description = "Task ID") @PathVariable Long id) {
        return ApiResponse.ok(annotationService.getAnnotationQualityMetrics(id));
    }

    @GetMapping("/annotation-tasks/{id}/annotator-performance")
    @Operation(summary = "Annotator performance", description = "Get annotator workload and quality metrics")
    public ApiResponse<List<Map<String, Object>>> getAnnotatorPerformance(@PathVariable Long id) {
        return ApiResponse.ok(annotationService.getAnnotatorPerformance(id));
    }

    @PostMapping("/annotation-tasks/{id}/auto-validate")
    @Operation(summary = "Automatically validate annotations", description = "Validate label membership and confidence and route failures to review")
    public ApiResponse<Map<String, Object>> autoValidate(@PathVariable Long id) {
        return ApiResponse.ok(annotationService.autoValidate(id));
    }
}
