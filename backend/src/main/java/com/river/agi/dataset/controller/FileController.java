package com.river.agi.dataset.controller;

import com.river.agi.common.BusinessException;
import com.river.agi.dataset.service.LocalStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "File download APIs")
public class FileController {
    
    private final LocalStorageService localStorageService;

    @Value("${app.storage.local-path:./uploads}")
    private String localPath;
    
    @GetMapping("/{filename}")
    @Operation(summary = "Download file", description = "Download a file from local storage")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "Filename to download") @PathVariable String filename) {
        try {
            Path uploadRoot = Paths.get(localPath).toAbsolutePath().normalize();
            Path filePath = uploadRoot.resolve(filename).normalize();
            if (!filePath.startsWith(uploadRoot)) {
                throw new BusinessException("Invalid file path");
            }
            Resource resource = new UrlResource(filePath.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException("File not found: " + filename);
            }
            
            String contentType = determineContentType(filename);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new BusinessException("Invalid file path");
        }
    }
    
    private String determineContentType(String filename) {
        String extension = "";
        if (filename != null && filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        }
        return switch (extension) {
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".xls" -> "application/vnd.ms-excel";
            case ".csv" -> "text/csv";
            case ".json" -> "application/json";
            default -> "application/octet-stream";
        };
    }
}
