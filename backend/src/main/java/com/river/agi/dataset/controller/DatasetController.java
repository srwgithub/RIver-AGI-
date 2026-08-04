package com.river.agi.dataset.controller;

import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.entity.DatasetField;
import com.river.agi.dataset.service.DatasetService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import com.river.agi.common.entity.AsyncTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
@Tag(name = "Datasets", description = "Dataset management APIs")
public class DatasetController {
    
    private final DatasetService datasetService;
    
    @PostMapping("/upload")
    @Operation(summary = "Upload file", description = "Upload Excel/CSV/JSON file to create a dataset")
    public ApiResponse<Dataset> uploadFile(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return ApiResponse.ok(datasetService.uploadFile(file, authentication));
    }
    
    @GetMapping
    @Operation(summary = "List datasets", description = "Get paginated list of datasets")
    public ApiResponse<PageResult<Dataset>> getDatasets(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(datasetService.getDatasets(page, size));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get dataset", description = "Get dataset by ID")
    public ApiResponse<Dataset> getDataset(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        return ApiResponse.ok(datasetService.getDataset(id, authentication));
    }
    
    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview dataset", description = "Get preview data of dataset")
    public ApiResponse<String> getDatasetPreview(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        Dataset dataset = datasetService.getDataset(id, authentication);
        return ApiResponse.ok(dataset.getPreviewJson());
    }
    
    @GetMapping("/{id}/schema")
    @Operation(summary = "Get dataset schema", description = "Get schema/structure of dataset")
    public ApiResponse<String> getDatasetSchema(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        Dataset dataset = datasetService.getDataset(id, authentication);
        return ApiResponse.ok(dataset.getSchemaJson());
    }
    
    @GetMapping("/{id}/fields")
    @Operation(summary = "Get dataset fields", description = "Get list of fields/columns in dataset")
    public ApiResponse<List<DatasetField>> getDatasetFields(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        return ApiResponse.ok(datasetService.getDatasetFields(id, authentication));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete dataset", description = "Delete dataset by ID")
    public ApiResponse<Void> deleteDataset(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        datasetService.deleteDataset(id, authentication);
        return ApiResponse.ok(null);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update dataset", description = "Update dataset information")
    public ApiResponse<Dataset> updateDataset(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            @RequestBody Dataset dataset,
            Authentication authentication) {
        datasetService.updateDataset(id, dataset, authentication);
        return ApiResponse.ok(datasetService.getDataset(id, authentication));
    }
    
    @PostMapping("/{id}/parse")
    @Operation(summary = "Parse dataset", description = "Trigger async dataset parsing")
    public ApiResponse<AsyncTask> parseDataset(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        return ApiResponse.ok(datasetService.parseDataset(id, authentication));
    }
}
