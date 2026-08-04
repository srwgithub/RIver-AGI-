package com.river.agi.dataset.service;

import com.river.agi.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class LocalStorageService {
    
    @Value("${app.storage.local-path:./uploads}")
    private String localPath;
    
    @Value("${app.storage.base-url:http://localhost:8080}")
    private String baseUrl;
    
    public String uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + extension;
        
        try {
            Path uploadDir = Paths.get(localPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            
            Path filePath = uploadDir.resolve(newFilename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, filePath);
            }
            
            log.info("File saved to: {}", filePath);
            return baseUrl + "/api/v1/files/" + newFilename;
        } catch (IOException e) {
            log.error("Failed to save file locally", e);
            throw new BusinessException("Failed to save file");
        }
    }
    
    public byte[] readFile(String filename) {
        try {
            Path filePath = Paths.get(localPath, filename);
            if (!Files.exists(filePath)) {
                throw new BusinessException("File not found: " + filename);
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Failed to read file", e);
            throw new BusinessException("Failed to read file");
        }
    }
    
    public InputStream getFileInputStream(String filename) {
        try {
            Path filePath = Paths.get(localPath, filename);
            if (!Files.exists(filePath)) {
                throw new BusinessException("File not found: " + filename);
            }
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            log.error("Failed to open file", e);
            throw new BusinessException("Failed to open file");
        }
    }
    
    public void deleteFile(String filename) {
        try {
            Path filePath = Paths.get(localPath, filename);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete file", e);
            throw new BusinessException("Failed to delete file");
        }
    }
    
    public String writeFile(byte[] content, String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + extension;
        
        try {
            Path uploadDir = Paths.get(localPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            
            Path filePath = uploadDir.resolve(newFilename);
            Files.write(filePath, content);
            
            log.info("File written to: {}", filePath);
            return newFilename;
        } catch (IOException e) {
            log.error("Failed to write file locally", e);
            throw new BusinessException("Failed to write file");
        }
    }

    public String fileUrl(String filename) {
        return baseUrl + "/api/v1/files/" + filename;
    }
}
