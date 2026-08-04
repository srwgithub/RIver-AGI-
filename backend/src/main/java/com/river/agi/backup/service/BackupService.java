package com.river.agi.backup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.backup.entity.BackupRecord;
import com.river.agi.backup.mapper.BackupRecordMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.security.entity.SensitiveDataDetection;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.mapper.SensitiveDataDetectionMapper;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import com.river.agi.common.annotation.AuditOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final BackupRecordMapper backupRecordMapper;
    private final DatasetMapper datasetMapper;
    private final SecurityScanTaskMapper securityScanTaskMapper;
    private final SensitiveDataDetectionMapper sensitiveDataDetectionMapper;
    private final ObjectMapper objectMapper;

    private static final String BACKUP_DIR = "./backups";
    private static final int MAX_BACKUPS = 10;

    /** 异地备份目录（合同 14.1.3 异地备份策略） */
    @Value("${backup.offsite-dir:./backups-offsite}")
    private String offsiteDir;

    /** 是否启用异地备份 */
    @Value("${backup.offsite-enabled:false}")
    private boolean offsiteEnabled;
    
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledBackup() {
        log.info("Starting scheduled backup...");
        try {
            String backupId = createFullBackup("SCHEDULED");
            log.info("Scheduled backup completed: {}", backupId);
        } catch (Exception e) {
            log.error("Scheduled backup failed", e);
        }
    }
    
    @AuditOperation(action = "CREATE_BACKUP", resourceType = "BACKUP", description = "Create full data backup")
    public String createFullBackup(String type) {
        String backupId = "backup_" + System.currentTimeMillis();
        Path backupDir = Paths.get(BACKUP_DIR);
        
        try {
            Files.createDirectories(backupDir);
            
            BackupRecord record = new BackupRecord();
            record.setBackupId(backupId);
            record.setType(type);
            record.setStatus("IN_PROGRESS");
            record.setCreatedAt(LocalDateTime.now());
            record.setSizeBytes(0L);
            backupRecordMapper.insert(record);
            
            String backupFileName = backupId + ".zip";
            Path backupPath = backupDir.resolve(backupFileName);
            
            long totalSize = 0;
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupPath.toFile()))) {
                totalSize += backupDatasets(zos);
                totalSize += backupSecurityData(zos);
                totalSize += backupSystemConfig(zos);
            }

            // 计算备份文件 SHA-256 校验值（合同 14.1.3 备份完整性校验）
            String checksum = sha256(backupPath);

            // 异地备份副本（合同 14.1.3 异地备份策略）
            String offsitePath = null;
            if (offsiteEnabled) {
                offsitePath = replicateOffsite(backupPath, backupId);
            }

            record.setStatus("COMPLETED");
            record.setSizeBytes(totalSize);
            record.setFilePath(backupPath.toString());
            record.setChecksum(checksum);
            record.setOffsitePath(offsitePath);
            record.setCompletedAt(LocalDateTime.now());
            backupRecordMapper.updateById(record);

            log.info("Full backup created: {} ({} bytes, checksum={})", backupId, totalSize, checksum);
            return backupId;
            
        } catch (Exception e) {
            log.error("Backup failed", e);
            BackupRecord record = new BackupRecord();
            record.setBackupId(backupId);
            record.setStatus("FAILED");
            record.setError(e.getMessage());
            record.setCreatedAt(LocalDateTime.now());
            backupRecordMapper.insert(record);
            throw new RuntimeException("Backup failed: " + e.getMessage());
        }
    }
    
    private long backupDatasets(ZipOutputStream zos) throws Exception {
        List<Dataset> datasets = datasetMapper.selectList(null);
        long size = 0;
        
        for (Dataset dataset : datasets) {
            String entryName = "datasets/" + dataset.getId() + "_" + dataset.getName() + ".json";
            zos.putNextEntry(new ZipEntry(entryName));
            
            Map<String, Object> datasetData = new LinkedHashMap<>();
            datasetData.put("id", dataset.getId());
            datasetData.put("name", dataset.getName());
            datasetData.put("description", dataset.getDescription());
            datasetData.put("fileType", dataset.getFileType());
            datasetData.put("rowCount", dataset.getRowCount());
            datasetData.put("columnCount", dataset.getColumnCount());
            datasetData.put("status", dataset.getStatus());
            datasetData.put("schemaJson", dataset.getSchemaJson());
            datasetData.put("previewJson", dataset.getPreviewJson());
            datasetData.put("filePath", dataset.getFilePath());
            datasetData.put("createdBy", dataset.getCreatedBy());
            datasetData.put("createdAt", dataset.getCreatedAt());
            datasetData.put("updatedAt", dataset.getUpdatedAt());
            
            byte[] data = objectMapper.writeValueAsBytes(datasetData);
            zos.write(data);
            size += data.length;
            zos.closeEntry();
        }
        
        return size;
    }
    
    private long backupSecurityData(ZipOutputStream zos) throws Exception {
        List<SecurityScanTask> scanTasks = securityScanTaskMapper.selectList(null);
        long size = 0;
        
        zos.putNextEntry(new ZipEntry("security/scan_tasks.json"));
        byte[] scanData = objectMapper.writeValueAsBytes(scanTasks);
        zos.write(scanData);
        size += scanData.length;
        zos.closeEntry();
        
        List<SensitiveDataDetection> detections = sensitiveDataDetectionMapper.selectList(null);
        zos.putNextEntry(new ZipEntry("security/detections.json"));
        byte[] detectionData = objectMapper.writeValueAsBytes(detections);
        zos.write(detectionData);
        size += detectionData.length;
        zos.closeEntry();
        
        return size;
    }
    
    private long backupSystemConfig(ZipOutputStream zos) throws Exception {
        long size = 0;
        
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", "1.0");
        config.put("backupTime", LocalDateTime.now().toString());
        config.put("database", "H2/MySQL");
        config.put("application", "RIver AGI System");
        
        zos.putNextEntry(new ZipEntry("config/system.json"));
        byte[] configData = objectMapper.writeValueAsBytes(config);
        zos.write(configData);
        size += configData.length;
        zos.closeEntry();
        
        return size;
    }
    
    public List<BackupRecord> listBackups() {
        return backupRecordMapper.selectList(
            new LambdaQueryWrapper<BackupRecord>()
                .orderByDesc(BackupRecord::getCreatedAt)
        );
    }
    
    public BackupRecord getBackup(String backupId) {
        return backupRecordMapper.selectOne(
            new LambdaQueryWrapper<BackupRecord>()
                .eq(BackupRecord::getBackupId, backupId)
        );
    }
    
    @Transactional
    @AuditOperation(action = "RESTORE_BACKUP", resourceType = "BACKUP", description = "Restore data from backup")
    public boolean restoreFromBackup(String backupId) {
        BackupRecord record = getBackup(backupId);
        if (record == null || !"COMPLETED".equals(record.getStatus())) {
            throw new RuntimeException("Backup not found or not completed");
        }
        
        try {
            Path backupPath = Paths.get(record.getFilePath());
            if (!Files.exists(backupPath)) {
                throw new RuntimeException("Backup file not found: " + backupPath);
            }
            
            log.info("Restoring from backup: {}", backupId);
            
            record.setStatus("RESTORING");
            backupRecordMapper.updateById(record);
            
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    new FileInputStream(backupPath.toFile()))) {
                
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    // Jackson may close the supplied stream after parsing one JSON entry.
                    // Buffer each ZIP entry so the restore loop can safely continue.
                    ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                    zis.transferTo(entryBuffer);
                    byte[] entryBytes = entryBuffer.toByteArray();
                    if (entry.getName().startsWith("datasets/") && entry.getName().endsWith(".json")) {
                        Dataset dataset = objectMapper.readValue(entryBytes, Dataset.class);
                        restoreDataset(dataset);
                    } else if ("security/scan_tasks.json".equals(entry.getName())) {
                        restoreSecurityScanTasks(objectMapper.readValue(entryBytes,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, SecurityScanTask.class)));
                    } else if ("security/detections.json".equals(entry.getName())) {
                        restoreSensitiveDetections(objectMapper.readValue(entryBytes,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, SensitiveDataDetection.class)));
                    }
                    zis.closeEntry();
                }
            }
            
            record.setStatus("COMPLETED");
            backupRecordMapper.updateById(record);
            
            log.info("Restore completed successfully");
            return true;
            
        } catch (Exception e) {
            log.error("Restore failed", e);
            record.setStatus("FAILED");
            record.setError(e.getMessage());
            backupRecordMapper.updateById(record);
            return false;
        }
    }

    private void restoreDataset(Dataset source) {
        if (source.getId() == null) {
            throw new IllegalArgumentException("备份数据集缺少 id");
        }
        Dataset existing = datasetMapper.selectById(source.getId());
        if (existing == null) {
            datasetMapper.insert(source);
        } else {
            datasetMapper.updateById(source);
        }
        log.info("Restored dataset: {} (id={})", source.getName(), source.getId());
    }

    private void restoreSecurityScanTasks(List<SecurityScanTask> sources) {
        for (SecurityScanTask source : sources) {
            if (source.getId() == null) {
                continue;
            }
            if (securityScanTaskMapper.selectById(source.getId()) == null) {
                securityScanTaskMapper.insert(source);
            } else {
                securityScanTaskMapper.updateById(source);
            }
        }
    }

    private void restoreSensitiveDetections(List<SensitiveDataDetection> sources) {
        for (SensitiveDataDetection source : sources) {
            if (source.getId() == null) {
                continue;
            }
            if (sensitiveDataDetectionMapper.selectById(source.getId()) == null) {
                sensitiveDataDetectionMapper.insert(source);
            } else {
                sensitiveDataDetectionMapper.updateById(source);
            }
        }
    }
    
    @AuditOperation(action = "DELETE_BACKUP", resourceType = "BACKUP", description = "Delete backup record and file")
    public boolean deleteBackup(String backupId) {
        BackupRecord record = getBackup(backupId);
        if (record == null) {
            return false;
        }
        
        try {
            Path backupPath = Paths.get(record.getFilePath());
            if (Files.exists(backupPath)) {
                Files.delete(backupPath);
            }
        } catch (IOException e) {
            log.warn("Could not delete backup file: {}", e.getMessage());
        }
        
        backupRecordMapper.delete(
            new LambdaQueryWrapper<BackupRecord>()
                .eq(BackupRecord::getBackupId, backupId)
        );
        
        return true;
    }
    
    public void cleanupOldBackups() {
        List<BackupRecord> backups = listBackups();
        int count = backups.size();
        
        if (count > MAX_BACKUPS) {
            List<BackupRecord> toDelete = backups.subList(MAX_BACKUPS, count);
            for (BackupRecord backup : toDelete) {
                deleteBackup(backup.getBackupId());
            }
            log.info("Cleaned up {} old backups", toDelete.size());
        }
    }
    
    public Map<String, Object> getBackupStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        List<BackupRecord> allBackups = listBackups();
        long completedCount = allBackups.stream()
            .filter(b -> "COMPLETED".equals(b.getStatus()))
            .count();

        status.put("totalBackups", allBackups.size());
        status.put("completedBackups", completedCount);
        status.put("failedBackups", allBackups.stream()
            .filter(b -> "FAILED".equals(b.getStatus()))
            .count());
        status.put("lastBackupTime", allBackups.isEmpty() ? null : allBackups.get(0).getCreatedAt());
        status.put("backupDirectory", BACKUP_DIR);
        status.put("maxBackups", MAX_BACKUPS);
        status.put("offsiteEnabled", offsiteEnabled);
        status.put("offsiteDirectory", offsiteDir);

        return status;
    }

    /**
     * 计算文件 SHA-256 校验值（合同 14.1.3 备份完整性校验）。
     */
    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 复制备份文件到异地目录（合同 14.1.3 异地备份策略）。
     */
    private String replicateOffsite(Path sourceBackupPath, String backupId) {
        try {
            Path offsite = Paths.get(offsiteDir);
            Files.createDirectories(offsite);
            Path target = offsite.resolve(sourceBackupPath.getFileName().toString());
            Files.copy(sourceBackupPath, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Replicated backup {} to offsite location: {}", backupId, target);
            return target.toString();
        } catch (Exception e) {
            log.warn("Failed to replicate backup {} to offsite location: {}", backupId, e.getMessage());
            return null;
        }
    }

    /**
     * 验证备份完整性（合同 14.1.3 恢复验证机制）。
     * 重新计算备份文件 SHA-256 并与记录的 checksum 比对，同时验证 ZIP 可读取。
     */
    @AuditOperation(action = "VERIFY_BACKUP", resourceType = "BACKUP", description = "Verify backup integrity")
    public Map<String, Object> verifyBackupIntegrity(String backupId) {
        BackupRecord record = getBackup(backupId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("backupId", backupId);

        if (record == null) {
            result.put("status", "NOT_FOUND");
            result.put("integrityValid", false);
            result.put("message", "备份记录不存在");
            return result;
        }

        result.put("recordedChecksum", record.getChecksum());

        Path backupPath = Paths.get(record.getFilePath());
        if (!Files.exists(backupPath)) {
            result.put("status", "FILE_MISSING");
            result.put("integrityValid", false);
            result.put("message", "备份文件不存在: " + record.getFilePath());
            return result;
        }

        try {
            String actualChecksum = sha256(backupPath);
            result.put("actualChecksum", actualChecksum);
            boolean checksumMatch = record.getChecksum() != null
                    && record.getChecksum().equalsIgnoreCase(actualChecksum);
            result.put("checksumMatch", checksumMatch);

            // 验证 ZIP 可正常读取
            boolean zipReadable = verifyZipReadable(backupPath);
            result.put("zipReadable", zipReadable);

            // 异地副本校验
            boolean offsiteValid = false;
            if (record.getOffsitePath() != null) {
                Path offsitePath = Paths.get(record.getOffsitePath());
                offsiteValid = Files.exists(offsitePath)
                        && sha256(offsitePath).equalsIgnoreCase(actualChecksum);
            }
            result.put("offsiteValid", offsiteValid);

            boolean overallValid = checksumMatch && zipReadable;
            result.put("integrityValid", overallValid);
            result.put("status", overallValid ? "VERIFIED" : "CORRUPTED");
            result.put("verifiedAt", LocalDateTime.now());
            result.put("message", overallValid ? "备份完整性校验通过" : "备份完整性校验失败");
            return result;
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("integrityValid", false);
            result.put("message", "校验异常: " + e.getMessage());
            log.error("Backup integrity verification failed for {}", backupId, e);
            return result;
        }
    }

    private boolean verifyZipReadable(Path backupPath) {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new FileInputStream(backupPath.toFile()))) {
            java.util.zip.ZipEntry entry;
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                zis.closeEntry();
            }
            return entryCount > 0;
        } catch (Exception e) {
            log.warn("ZIP read verification failed for {}: {}", backupPath, e.getMessage());
            return false;
        }
    }
}
