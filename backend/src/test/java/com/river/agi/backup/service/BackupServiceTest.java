package com.river.agi.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.backup.entity.BackupRecord;
import com.river.agi.backup.mapper.BackupRecordMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.entity.SensitiveDataDetection;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import com.river.agi.security.mapper.SensitiveDataDetectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("备份服务测试")
class BackupServiceTest {

    @Mock
    private BackupRecordMapper backupRecordMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private SecurityScanTaskMapper securityScanTaskMapper;
    @Mock
    private SensitiveDataDetectionMapper sensitiveDataDetectionMapper;

    private BackupService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new BackupService(
                backupRecordMapper, datasetMapper, securityScanTaskMapper,
                sensitiveDataDetectionMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("createFullBackup - 成功创建备份")
    void createFullBackup_success() {
        when(datasetMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sensitiveDataDetectionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(backupRecordMapper.insert(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });
        when(backupRecordMapper.updateById(any())).thenReturn(1);

        String backupId = service.createFullBackup("MANUAL");
        assertNotNull(backupId);
        assertTrue(backupId.startsWith("backup_"));
    }

    @Test
    @DisplayName("createFullBackup - 含数据集")
    void createFullBackup_withDatasets() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test");
        dataset.setFileType("CSV");
        dataset.setRowCount(10);
        dataset.setColumnCount(3);
        dataset.setStatus("PARSED");

        when(datasetMapper.selectList(any())).thenReturn(List.of(dataset));
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sensitiveDataDetectionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(backupRecordMapper.insert(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });
        when(backupRecordMapper.updateById(any())).thenReturn(1);

        String backupId = service.createFullBackup("MANUAL");
        assertNotNull(backupId);
    }

    @Test
    @DisplayName("createFullBackup - 备份失败时记录 FAILED 状态")
    void createFullBackup_failure() {
        // 使用 null datasetMapper.selectList 抛异常模拟失败
        when(backupRecordMapper.insert(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });
        when(datasetMapper.selectList(any())).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> service.createFullBackup("MANUAL"));
        // 应该插入了 FAILED 记录
        verify(backupRecordMapper, atLeast(2)).insert(any());
    }

    @Test
    @DisplayName("listBackups - 返回备份列表")
    void listBackups_success() {
        BackupRecord r = new BackupRecord();
        r.setId(1L);
        r.setBackupId("backup_1");
        when(backupRecordMapper.selectList(any())).thenReturn(List.of(r));

        List<BackupRecord> result = service.listBackups();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getBackup - 找到备份记录")
    void getBackup_found() {
        BackupRecord r = new BackupRecord();
        r.setBackupId("backup_1");
        when(backupRecordMapper.selectOne(any())).thenReturn(r);

        BackupRecord result = service.getBackup("backup_1");
        assertNotNull(result);
    }

    @Test
    @DisplayName("getBackup - 不存在返回 null")
    void getBackup_notFound() {
        when(backupRecordMapper.selectOne(any())).thenReturn(null);
        BackupRecord result = service.getBackup("missing");
        assertNull(result);
    }

    @Test
    @DisplayName("restoreFromBackup - 备份不存在抛异常")
    void restoreFromBackup_notFound() {
        when(backupRecordMapper.selectOne(any())).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.restoreFromBackup("missing"));
    }

    @Test
    @DisplayName("restoreFromBackup - 状态非 COMPLETED 抛异常")
    void restoreFromBackup_notCompleted() {
        BackupRecord r = new BackupRecord();
        r.setBackupId("backup_1");
        r.setStatus("FAILED");
        when(backupRecordMapper.selectOne(any())).thenReturn(r);
        assertThrows(RuntimeException.class, () -> service.restoreFromBackup("backup_1"));
    }

    @Test
    @DisplayName("restoreFromBackup - 文件不存在返回 false")
    void restoreFromBackup_fileMissing() {
        BackupRecord r = new BackupRecord();
        r.setBackupId("backup_1");
        r.setStatus("COMPLETED");
        r.setFilePath("/nonexistent/path/backup.zip");
        when(backupRecordMapper.selectOne(any())).thenReturn(r);
        when(backupRecordMapper.updateById(any())).thenReturn(1);

        boolean result = service.restoreFromBackup("backup_1");
        assertFalse(result);
    }

    @Test
    @DisplayName("restoreFromBackup - 成功恢复")
    void restoreFromBackup_success() throws Exception {
        // 先创建一个有效备份
        when(datasetMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sensitiveDataDetectionMapper.selectList(any())).thenReturn(new ArrayList<>());

        BackupRecord created = new BackupRecord();
        created.setBackupId("backup_test");
        created.setStatus("IN_PROGRESS");
        created.setCreatedAt(java.time.LocalDateTime.now());

        // 先记录创建时的 insert，并保存 record 引用
        when(backupRecordMapper.insert(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            // 保存对同一 record 的引用，便于后续 selectOne 返回
            synchronized (created) {
                created.setBackupId(r.getBackupId());
                created.setStatus(r.getStatus());
                created.setFilePath(r.getFilePath());
                created.setCreatedAt(r.getCreatedAt());
            }
            return 1;
        });
        when(backupRecordMapper.updateById(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            // 同步状态到 created
            created.setStatus(r.getStatus());
            created.setFilePath(r.getFilePath());
            created.setCompletedAt(r.getCompletedAt());
            return 1;
        });
        // getBackup 返回 created
        when(backupRecordMapper.selectOne(any())).thenReturn(created);

        String backupId = service.createFullBackup("MANUAL");
        // createFullBackup 完成后，created 状态已同步为 COMPLETED
        created.setStatus("COMPLETED");

        boolean result = service.restoreFromBackup(backupId);
        assertTrue(result);
    }

    @Test
    @DisplayName("deleteBackup - 备份不存在返回 false")
    void deleteBackup_notFound() {
        when(backupRecordMapper.selectOne(any())).thenReturn(null);
        boolean result = service.deleteBackup("missing");
        assertFalse(result);
    }

    @Test
    @DisplayName("deleteBackup - 成功删除")
    void deleteBackup_success() {
        // 直接构造一个有效的 backup record，让 getBackup 返回它
        BackupRecord record = new BackupRecord();
        record.setBackupId("backup_test");
        record.setStatus("COMPLETED");
        record.setFilePath("/tmp/nonexistent_for_delete.zip");

        when(backupRecordMapper.selectOne(any())).thenReturn(record);
        when(backupRecordMapper.delete(any())).thenReturn(1);

        boolean result = service.deleteBackup("backup_test");
        assertTrue(result);
    }

    @Test
    @DisplayName("cleanupOldBackups - 备份数量未超限不删除")
    void cleanupOldBackups_underLimit() {
        BackupRecord r = new BackupRecord();
        r.setBackupId("backup_1");
        when(backupRecordMapper.selectList(any())).thenReturn(List.of(r));

        // 只有1个备份，不会触发清理
        assertDoesNotThrow(() -> service.cleanupOldBackups());
        verify(backupRecordMapper, never()).delete(any());
    }

    @Test
    @DisplayName("cleanupOldBackups - 超过 MAX_BACKUPS 触发清理")
    void cleanupOldBackups_overLimit() {
        // 创建 12 个备份记录，超过 MAX_BACKUPS(10)
        List<BackupRecord> backups = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            BackupRecord r = new BackupRecord();
            r.setBackupId("backup_" + i);
            r.setFilePath("/tmp/nonexistent_" + i + ".zip");
            backups.add(r);
        }
        when(backupRecordMapper.selectList(any())).thenReturn(backups);
        // 对每次 selectOne 都返回对应的 record
        when(backupRecordMapper.selectOne(any())).thenAnswer(inv -> backups.get(0));
        when(backupRecordMapper.delete(any())).thenReturn(1);

        assertDoesNotThrow(() -> service.cleanupOldBackups());
    }

    @Test
    @DisplayName("getBackupStatus - 返回备份状态")
    void getBackupStatus_success() {
        BackupRecord r1 = new BackupRecord();
        r1.setStatus("COMPLETED");
        r1.setCreatedAt(java.time.LocalDateTime.now());

        BackupRecord r2 = new BackupRecord();
        r2.setStatus("FAILED");

        when(backupRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        Map<String, Object> status = service.getBackupStatus();
        assertNotNull(status);
        assertEquals(2, status.get("totalBackups"));
        assertEquals(1L, status.get("completedBackups"));
        assertEquals(1L, status.get("failedBackups"));
        assertEquals(10, status.get("maxBackups"));
        assertNotNull(status.get("backupDirectory"));
    }

    @Test
    @DisplayName("getBackupStatus - 无备份")
    void getBackupStatus_empty() {
        when(backupRecordMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> status = service.getBackupStatus();
        assertNotNull(status);
        assertEquals(0, status.get("totalBackups"));
        assertNull(status.get("lastBackupTime"));
    }

    @Test
    @DisplayName("scheduledBackup - 调用成功")
    void scheduledBackup_success() {
        when(datasetMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sensitiveDataDetectionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(backupRecordMapper.insert(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });
        when(backupRecordMapper.updateById(any())).thenReturn(1);

        assertDoesNotThrow(() -> service.scheduledBackup());
    }

    @Test
    @DisplayName("scheduledBackup - 异常被吞掉")
    void scheduledBackup_failure() {
        when(backupRecordMapper.insert(any())).thenThrow(new RuntimeException("DB error"));
        // scheduledBackup 内部捕获异常，不会抛出
        assertDoesNotThrow(() -> service.scheduledBackup());
    }

    // ===== verifyBackupIntegrity（合同 14.1.3 恢复验证） =====

    @Test
    @DisplayName("verifyBackupIntegrity - 备份不存在返回 NOT_FOUND")
    void verifyBackupIntegrity_notFound() {
        when(backupRecordMapper.selectOne(any())).thenReturn(null);
        Map<String, Object> result = service.verifyBackupIntegrity("missing");
        assertEquals("NOT_FOUND", result.get("status"));
        assertEquals(false, result.get("integrityValid"));
    }

    @Test
    @DisplayName("verifyBackupIntegrity - 文件不存在返回 FILE_MISSING")
    void verifyBackupIntegrity_fileMissing() {
        BackupRecord record = new BackupRecord();
        record.setBackupId("b1");
        record.setStatus("COMPLETED");
        record.setFilePath("/nonexistent/path/b1.zip");
        record.setChecksum("abc");
        when(backupRecordMapper.selectOne(any())).thenReturn(record);

        Map<String, Object> result = service.verifyBackupIntegrity("b1");
        assertEquals("FILE_MISSING", result.get("status"));
        assertEquals(false, result.get("integrityValid"));
    }

    @Test
    @DisplayName("verifyBackupIntegrity - 校验通过")
    void verifyBackupIntegrity_valid() throws Exception {
        // 创建一个真实备份用于校验
        when(datasetMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sensitiveDataDetectionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(backupRecordMapper.insert(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });
        when(backupRecordMapper.updateById(any())).thenReturn(1);

        String backupId = service.createFullBackup("MANUAL");

        // getBackup 返回刚创建的记录
        when(backupRecordMapper.selectOne(any())).thenAnswer(inv -> {
            BackupRecord r = new BackupRecord();
            r.setBackupId(backupId);
            r.setStatus("COMPLETED");
            r.setFilePath("./backups/" + backupId + ".zip");
            // 通过反射读取真实 checksum
            return r;
        });

        // 直接获取真实记录的 checksum 需要读取文件。这里改为先创建记录，再用真实路径校验。
        // createFullBackup 已经计算 checksum，但 mock 的 selectOne 返回新对象，checksum 为 null。
        // 改为：让 selectOne 返回带真实 checksum 的记录。
        // 重新创建以获取真实 checksum
        java.nio.file.Path backupPath = java.nio.file.Paths.get("./backups/" + backupId + ".zip");
        String checksum = sha256OfFile(backupPath);

        when(backupRecordMapper.selectOne(any())).thenAnswer(inv -> {
            BackupRecord r = new BackupRecord();
            r.setBackupId(backupId);
            r.setStatus("COMPLETED");
            r.setFilePath("./backups/" + backupId + ".zip");
            r.setChecksum(checksum);
            return r;
        });

        Map<String, Object> result = service.verifyBackupIntegrity(backupId);
        assertEquals("VERIFIED", result.get("status"));
        assertEquals(true, result.get("integrityValid"));
        assertEquals(true, result.get("checksumMatch"));
        assertEquals(true, result.get("zipReadable"));
    }

    @Test
    @DisplayName("verifyBackupIntegrity - checksum 不匹配返回 CORRUPTED")
    void verifyBackupIntegrity_checksumMismatch() throws Exception {
        when(datasetMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sensitiveDataDetectionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(backupRecordMapper.insert(any())).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });
        when(backupRecordMapper.updateById(any())).thenReturn(1);

        String backupId = service.createFullBackup("MANUAL");

        when(backupRecordMapper.selectOne(any())).thenAnswer(inv -> {
            BackupRecord r = new BackupRecord();
            r.setBackupId(backupId);
            r.setStatus("COMPLETED");
            r.setFilePath("./backups/" + backupId + ".zip");
            r.setChecksum("wrongchecksum");
            return r;
        });

        Map<String, Object> result = service.verifyBackupIntegrity(backupId);
        assertEquals("CORRUPTED", result.get("status"));
        assertEquals(false, result.get("integrityValid"));
        assertEquals(false, result.get("checksumMatch"));
    }

    private String sha256OfFile(java.nio.file.Path path) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(path)) {
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
}
