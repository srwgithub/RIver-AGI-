package com.river.agi.collection.controller;

import com.river.agi.collection.entity.CollectionTask;
import com.river.agi.collection.service.CollectionTaskService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import com.river.agi.media.entity.MediaAnnotation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/collection-tasks")
@RequiredArgsConstructor
public class CollectionTaskController {
    private final CollectionTaskService service;

    @GetMapping public ApiResponse<PageResult<CollectionTask>> list(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) { return ApiResponse.ok(service.list(page, size)); }
    @PostMapping public ApiResponse<CollectionTask> create(@RequestBody CollectionTask task, Authentication authentication) { return ApiResponse.ok(service.create(task, authentication)); }
    @GetMapping("/{id}") public ApiResponse<CollectionTask> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }
    @GetMapping("/{id}/progress") public ApiResponse<CollectionTask> progress(@PathVariable Long id) { return ApiResponse.ok(service.refreshProgress(id)); }
    @PutMapping("/{id}") public ApiResponse<CollectionTask> update(@PathVariable Long id, @RequestBody CollectionTask task) { return ApiResponse.ok(service.update(id, task)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(null); }
    @PostMapping("/{id}/clean-preview") public ApiResponse<Map<String,Object>> cleanPreview(@PathVariable Long id, @RequestBody(required=false) Map<String,Object> config) { return ApiResponse.ok(service.cleanPreview(id, config)); }
    @PostMapping("/{id}/clean-apply") public ApiResponse<Map<String,Object>> applyCleaning(@PathVariable Long id, @RequestBody(required=false) Map<String,Object> config, Authentication authentication) { return ApiResponse.ok(service.applyCleaning(id, config, authentication)); }
    @PostMapping("/media-upload") public ApiResponse<Map<String,Object>> uploadMedia(@RequestParam("file") MultipartFile file) { return ApiResponse.ok(service.uploadMedia(file)); }
    @PostMapping("/{id}/media-items") public ApiResponse<MediaAnnotation> attachMedia(@PathVariable Long id, @RequestBody Map<String,String> body, Authentication authentication) { return ApiResponse.ok(service.attachMedia(id, body.get("mediaType"), body.get("mediaUrl"), authentication)); }
}
