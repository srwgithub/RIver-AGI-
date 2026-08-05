package com.river.agi.dataset.service;

import com.river.agi.config.MinioConfig;
import com.river.agi.common.BusinessException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class MinioService {
    
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    
    public String uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + extension;
        
        try (InputStream inputStream = file.getInputStream()) {
            Long fileSize = file.getSize();
            Long partSize = -1L; // Use -1 to let MinIO determine the part size for streaming
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(newFilename)
                            .stream(inputStream, fileSize, partSize)
                            .contentType(file.getContentType())
                            .build()
            );
            return minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" + newFilename;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new BusinessException("Failed to upload file");
        }
    }
    
    public void deleteFile(String fileName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(fileName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO", e);
            throw new BusinessException("Failed to delete file");
        }
    }
}
