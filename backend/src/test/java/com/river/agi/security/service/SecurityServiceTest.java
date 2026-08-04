package com.river.agi.security.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.dataset.service.LocalStorageService;
import com.river.agi.security.entity.AuditLog;
import com.river.agi.security.entity.SensitiveDataDetection;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.mapper.AuditLogMapper;
import com.river.agi.security.mapper.SensitiveDataDetectionMapper;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("安全扫描服务测试")
class SecurityServiceTest {

    @Mock
    private SensitiveDataDetectionMapper sensitiveDataDetectionMapper;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private SecurityScanTaskMapper securityScanTaskMapper;

    @Mock
    private DatasetMapper datasetMapper;

    @Mock
    private ResourceAccessValidator accessValidator;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private LocalStorageService localStorageService;

    @Mock
    private DatasetDataReaderService dataReader;

    private SecurityService securityService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        securityService = new SecurityService(
                sensitiveDataDetectionMapper,
                auditLogMapper,
                securityScanTaskMapper,
                datasetMapper,
                objectMapper,
                dataReader,
                localStorageService,
                accessValidator,
                securityUtils
        );
    }

    private Dataset dataset(Long id, String status, String schemaJson, Integer rowCount) {
        Dataset ds = new Dataset();
        ds.setId(id);
        ds.setName("ds-" + id);
        ds.setFileType("csv");
        ds.setFilePath("/tmp/file.csv");
        ds.setFileUrl("/uploads/file.csv");
        ds.setStatus(status);
        ds.setSchemaJson(schemaJson);
        ds.setRowCount(rowCount);
        ds.setTenantId(1L);
        ds.setCreatedBy(1L);
        return ds;
    }

    private Authentication auth(String name) {
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn(name);
        return authentication;
    }

    private HttpServletRequest request(String forwardedFor, String remoteAddr, String userAgent) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        lenient().when(req.getHeader("Proxy-Client-IP")).thenReturn(null);
        lenient().when(req.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        lenient().when(req.getRemoteAddr()).thenReturn(remoteAddr);
        lenient().when(req.getHeader("User-Agent")).thenReturn(userAgent);
        return req;
    }

    // ============ getSecurityDashboard ============

    @Test
    @DisplayName("获取安全扫描仪表板 - 统计正确")
    void getSecurityDashboard_countsCorrect() {
        when(securityScanTaskMapper.selectCount(any())).thenReturn(10L, 5L);
        when(sensitiveDataDetectionMapper.selectCount(any()))
                .thenReturn(20L, 3L, 5L, 12L);

        Map<String, Object> dashboard = securityService.getSecurityDashboard();

        assertNotNull(dashboard);
        assertEquals(10L, dashboard.get("totalScans"));
        assertEquals(5L, dashboard.get("completedScans"));
        assertEquals(20L, dashboard.get("totalRisks"));
        assertEquals(3L, dashboard.get("highRiskCount"));
    }

    @Test
    @DisplayName("获取安全扫描仪表板 - 零统计")
    void getSecurityDashboard_zeroCounts() {
        when(securityScanTaskMapper.selectCount(any())).thenReturn(0L);
        when(sensitiveDataDetectionMapper.selectCount(any()))
                .thenReturn(0L, 0L, 0L, 0L);

        Map<String, Object> dashboard = securityService.getSecurityDashboard();

        assertNotNull(dashboard);
        assertEquals(0L, dashboard.get("totalScans"));
        assertEquals(0L, dashboard.get("totalRisks"));
    }

    // ============ getScanTaskCount ============

    @Test
    @DisplayName("扫描任务计数 - 正确返回数量")
    void getScanTaskCount_returnsCount() {
        when(securityScanTaskMapper.selectCount(any())).thenReturn(15L);

        long count = securityService.getScanTaskCount();

        assertEquals(15L, count);
    }

    @Test
    @DisplayName("扫描任务计数 - 无记录时返回零")
    void getScanTaskCount_emptyReturnsZero() {
        when(securityScanTaskMapper.selectCount(any())).thenReturn(0L);

        long count = securityService.getScanTaskCount();

        assertEquals(0L, count);
    }

    // ============ getScanResults ============

    @Test
    @DisplayName("获取扫描结果 - 无扫描记录返回NO_SCAN")
    void getScanResults_noRecordReturnsNoScan() {
        when(securityScanTaskMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = securityService.getScanResults(1L);

        assertNotNull(result);
        assertEquals("NO_SCAN", result.get("status"));
        assertEquals("No security scan has been completed for this dataset", result.get("message"));
    }

    @Test
    @DisplayName("获取扫描结果 - 有记录返回完整信息")
    void getScanResults_withRecordReturnsDetails() {
        SecurityScanTask task = new SecurityScanTask();
        task.setId(1L);
        task.setStatus("COMPLETED");
        task.setTotalFields(10);
        task.setSensitiveFieldsFound(3);
        task.setHighRiskCount(1);
        task.setMediumRiskCount(1);
        task.setLowRiskCount(1);
        task.setCreatedAt(LocalDateTime.now());

        SensitiveDataDetection detection = new SensitiveDataDetection();
        detection.setId(1L);
        detection.setColumnName("phone");
        detection.setSensitiveType("手机号");
        detection.setRiskLevel("MEDIUM");
        detection.setConfidence(new java.math.BigDecimal("0.90"));
        detection.setDetectedCount(5);
        detection.setSampleData("13812345678");
        detection.setMaskedSampleData("138****5678");
        detection.setRuleVersion("v2.1.0");
        detection.setSuggestion("脱敏显示");

        when(securityScanTaskMapper.selectOne(any())).thenReturn(task);
        when(sensitiveDataDetectionMapper.selectByScanTaskId(1L)).thenReturn(List.of(detection));

        Map<String, Object> result = securityService.getScanResults(1L);

        assertNotNull(result);
        assertEquals("COMPLETED", result.get("status"));
        assertEquals(1L, result.get("scanTaskId"));
        assertEquals(10, result.get("totalFieldsScanned"));
        assertEquals(3, result.get("sensitiveFieldsFound"));
        List<Map<String, Object>> scanResults = (List<Map<String, Object>>) result.get("scanResults");
        assertEquals(1, scanResults.size());
        assertEquals("手机号", scanResults.get(0).get("sensitiveType"));
    }

    // ============ getScanTask ============

    @Test
    @DisplayName("获取扫描任务 - 找到返回任务")
    void getScanTask_found() {
        SecurityScanTask task = new SecurityScanTask();
        task.setId(5L);
        when(securityScanTaskMapper.selectById(5L)).thenReturn(task);

        SecurityScanTask result = securityService.getScanTask(5L);

        assertNotNull(result);
        assertEquals(5L, result.getId());
    }

    @Test
    @DisplayName("获取扫描任务 - 未找到抛出 BusinessException")
    void getScanTask_notFound_throws() {
        when(securityScanTaskMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> securityService.getScanTask(99L));
        assertEquals("Security scan task not found", ex.getMessage());
    }

    // ============ getSensitiveDetections ============

    @Test
    @DisplayName("按扫描任务 ID 查询敏感数据检测列表")
    void getSensitiveDetections_returnsList() {
        SensitiveDataDetection d = new SensitiveDataDetection();
        d.setId(1L);
        when(sensitiveDataDetectionMapper.selectByScanTaskId(7L)).thenReturn(List.of(d));

        List<SensitiveDataDetection> result = securityService.getSensitiveDetections(7L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    @DisplayName("按扫描任务 ID 查询敏感数据检测列表 - 空结果")
    void getSensitiveDetections_empty() {
        when(sensitiveDataDetectionMapper.selectByScanTaskId(7L)).thenReturn(new ArrayList<>());

        List<SensitiveDataDetection> result = securityService.getSensitiveDetections(7L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ============ getSensitiveDetectionsByDataset ============

    @Test
    @DisplayName("按数据集查询敏感检测 - 无最近完成扫描返回空")
    void getSensitiveDetectionsByDataset_noLatestScan() {
        Authentication authentication = auth("admin");
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);
        when(securityScanTaskMapper.selectOne(any())).thenReturn(null);

        List<Map<String, Object>> result = securityService.getSensitiveDetectionsByDataset(1L, authentication);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(accessValidator).validateDatasetAccess(1L, 1L);
    }

    @Test
    @DisplayName("按数据集查询敏感检测 - 返回检测列表")
    void getSensitiveDetectionsByDataset_withDetections() {
        Authentication authentication = auth("admin");
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);

        SecurityScanTask task = new SecurityScanTask();
        task.setId(11L);
        when(securityScanTaskMapper.selectOne(any())).thenReturn(task);

        SensitiveDataDetection d = new SensitiveDataDetection();
        d.setId(1L);
        d.setColumnName("phone");
        d.setSensitiveType("手机号");
        d.setRiskLevel("MEDIUM");
        d.setConfidence(new java.math.BigDecimal("0.90"));
        d.setSuggestion("建议脱敏");
        d.setDetectedCount(3);
        d.setSampleData("13812345678");
        d.setMaskedSampleData("138****5678");
        d.setRuleVersion("v2.1.0");
        d.setMatchType("FIELD_NAME_AND_CONTENT");

        when(sensitiveDataDetectionMapper.selectByScanTaskId(11L)).thenReturn(List.of(d));

        List<Map<String, Object>> result = securityService.getSensitiveDetectionsByDataset(1L, authentication);

        assertEquals(1, result.size());
        assertEquals("phone", result.get(0).get("fieldName"));
        assertEquals("手机号", result.get(0).get("sensitiveType"));
        assertEquals("FIELD_NAME_AND_CONTENT", result.get(0).get("matchType"));
    }

    // ============ scanSensitiveData ============

    @Test
    @DisplayName("扫描敏感数据 - 数据集不存在抛出异常")
    void scanSensitiveData_datasetNotFound() {
        when(datasetMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityService.scanSensitiveData(1L, null));
        assertEquals("Dataset not found", ex.getMessage());
    }

    @Test
    @DisplayName("扫描敏感数据 - 数据集未解析完成抛出异常")
    void scanSensitiveData_notParsed() {
        Dataset ds = dataset(1L, "UPLOADED", "{\"phone\":\"string\"}", 10);
        when(datasetMapper.selectById(1L)).thenReturn(ds);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityService.scanSensitiveData(1L, null));
        assertTrue(ex.getMessage().contains("数据集尚未解析完成"));
    }

    @Test
    @DisplayName("扫描敏感数据 - schema 为空抛出异常")
    void scanSensitiveData_emptySchema() {
        Dataset ds = dataset(1L, "PARSED", "", 10);
        when(datasetMapper.selectById(1L)).thenReturn(ds);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityService.scanSensitiveData(1L, null));
        assertEquals("数据集 Schema 为空，无法执行安全扫描", ex.getMessage());
    }

    @Test
    @DisplayName("扫描敏感数据 - 行数为 0 抛出异常")
    void scanSensitiveData_zeroRows() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 0);
        when(datasetMapper.selectById(1L)).thenReturn(ds);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityService.scanSensitiveData(1L, null));
        assertEquals("数据集行数为 0，无法执行安全扫描", ex.getMessage());
    }

    @Test
    @DisplayName("扫描敏感数据 - 成功扫描手机号和身份证号字段")
    void scanSensitiveData_successMultipleFields() {
        Dataset ds = dataset(1L, "PARSED",
                "{\"phone\":\"string\",\"idcard\":\"string\",\"name\":\"string\"}", 2);
        when(datasetMapper.selectById(1L)).thenReturn(ds);

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("phone", "13812345678", "idcard", "110101199003078888", "name", "张三"));
        rows.add(Map.of("phone", "13912345678", "idcard", "110101199003079999", "name", "李四"));
        when(dataReader.readRows(ds)).thenReturn(rows);
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(2L, 1L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        assertNotNull(result);
        assertEquals("COMPLETED", result.get("status"));
        assertEquals(3, result.get("totalFieldsScanned"));
        assertEquals(3, result.get("sensitiveFieldsFound"));
        assertEquals(2, result.get("highRiskCount"));
        assertEquals(1, result.get("mediumRiskCount"));
        assertEquals(0, result.get("lowRiskCount"));
        verify(sensitiveDataDetectionMapper, atLeast(3)).insert(any(SensitiveDataDetection.class));
    }

    @Test
    @DisplayName("扫描敏感数据 - 带 authentication 时校验数据集归属")
    void scanSensitiveData_withAuth_validatesOwnership() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 1);
        Authentication authentication = auth("admin");
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(7L);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("phone", "13812345678")));
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(0L, 1L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, authentication);

        verify(accessValidator).validateDatasetOwnership(1L, 7L);
        assertEquals("COMPLETED", result.get("status"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 仅字段名匹配类型（密码、薪资）")
    void scanSensitiveData_fieldNameOnlyMatches() {
        Dataset ds = dataset(1L, "PARSED",
                "{\"password\":\"string\",\"salary\":\"number\"}", 2);
        when(datasetMapper.selectById(1L)).thenReturn(ds);

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("password", "abc123", "salary", "5000"));
        rows.add(Map.of("password", "def456", "salary", "6000"));
        when(dataReader.readRows(ds)).thenReturn(rows);
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(2L, 0L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(2, result.get("sensitiveFieldsFound"));
        // password + salary both HIGH risk
        assertEquals(2, result.get("highRiskCount"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 读取数据行失败时仅扫描字段名")
    void scanSensitiveData_readRowsFails_continuesWithFieldScan() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenThrow(new RuntimeException("io error"));
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        // empty rows means no detection counts - selectCount returns 0 for all
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(0L, 0L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(0, result.get("sensitiveFieldsFound"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 部分检测插入失败时继续处理")
    void scanSensitiveData_insertFailsForSome_continues() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\",\"idcard\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(
                Map.of("phone", "13812345678", "idcard", "110101199003078888")));
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        // First insert succeeds, second throws
        when(sensitiveDataDetectionMapper.insert(any(SensitiveDataDetection.class)))
                .thenReturn(1)
                .thenThrow(new RuntimeException("db error"));
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(1L, 1L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        // Even if some inserts fail, scan task still completes with detected count
        assertEquals("COMPLETED", result.get("status"));
        assertEquals(2, result.get("sensitiveFieldsFound"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 解析 schema JSON 失败时按空 schema 处理")
    void scanSensitiveData_invalidSchemaJson_treatedAsEmpty() {
        Dataset ds = dataset(1L, "PARSED", "not-json", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(0L, 0L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(0, result.get("totalFieldsScanned"));
    }

    @Test
    @DisplayName("扫描敏感数据 - schema 字段名为空跳过")
    void scanSensitiveData_blankFieldNameSkipped() {
        // schema with a blank key and a valid key
        Dataset ds = dataset(1L, "PARSED", "{\"\":\"string\",\"phone\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("phone", "13812345678", "", "x")));
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(0L, 1L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        assertEquals("COMPLETED", result.get("status"));
        // totalFieldsScanned = schema.size() = 2, but only 1 sensitive
        assertEquals(2, result.get("totalFieldsScanned"));
        assertEquals(1, result.get("sensitiveFieldsFound"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 邮箱地址匹配")
    void scanSensitiveData_emailMatch() {
        Dataset ds = dataset(1L, "PARSED", "{\"email\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("email", "user@example.com")));
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(0L, 1L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(1, result.get("sensitiveFieldsFound"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 银行卡号匹配")
    void scanSensitiveData_bankCardMatch() {
        Dataset ds = dataset(1L, "PARSED", "{\"bank_card\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("bank_card", "6222020200011111111")));
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(1L, 0L, 0L);

        Map<String, Object> result = securityService.scanSensitiveData(1L, null);

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(1, result.get("sensitiveFieldsFound"));
        assertEquals(1, result.get("highRiskCount"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 抛出异常时写入审计日志并设置 FAILED 状态")
    void scanSensitiveData_failurePath_writesAuditLog() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        // Make dataReader throw a non-recoverable error to trigger the catch block
        when(dataReader.readRows(ds)).thenThrow(new RuntimeException("schema parse fail"));
        when(securityScanTaskMapper.insert(any(SecurityScanTask.class))).thenAnswer(inv -> {
            ((SecurityScanTask) inv.getArgument(0)).setId(100L);
            return 1;
        });

        // Now we need to make the inner try block throw - the readRows is wrapped in try/catch
        // so to make the outer try fail, we need a different error.
        // Use a schema that will fail to serialize in scanSummary writing? Better: make
        // sensitiveDataDetectionMapper.selectCount throw (called outside inner try).
        when(sensitiveDataDetectionMapper.selectCount(any())).thenThrow(new RuntimeException("count query failed"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityService.scanSensitiveData(1L, null));
        assertTrue(ex.getMessage().contains("安全扫描失败"));
        verify(auditLogMapper).insert(any(AuditLog.class));
        verify(securityScanTaskMapper).updateById(any(SecurityScanTask.class));
    }

    // ============ logAction ============

    @Test
    @DisplayName("记录审计日志 - 用户已认证时记录用户名")
    void logAction_authenticatedUser() {
        Authentication authentication = auth("admin");
        HttpServletRequest req = request("10.0.0.1", "127.0.0.1", "Mozilla/5.0");

        securityService.logAction("LOGIN", "USER", 1L, "admin",
                "{}", "SUCCESS", authentication, req);

        verify(auditLogMapper).insert(argThat(log ->
                "admin".equals(log.getUsername()) &&
                        "10.0.0.1".equals(log.getIpAddress()) &&
                        "Mozilla/5.0".equals(log.getUserAgent()) &&
                        "LOGIN".equals(log.getActionType()) &&
                        "SUCCESS".equals(log.getResult())));
    }

    @Test
    @DisplayName("记录审计日志 - 未认证时不写入用户名")
    void logAction_unauthenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        HttpServletRequest req = request(null, "127.0.0.1", "curl/8.0");

        securityService.logAction("LOGOUT", "USER", 1L, "user1",
                "{}", "SUCCESS", authentication, req);

        verify(auditLogMapper).insert(argThat(log ->
                log.getUsername() == null &&
                        "127.0.0.1".equals(log.getIpAddress())));
    }

    @Test
    @DisplayName("记录审计日志 - authentication 为 null 时跳过用户名")
    void logAction_nullAuthentication() {
        HttpServletRequest req = request("unknown", "192.168.1.1", "Test/1.0");

        securityService.logAction("VIEW", "DATASET", 2L, "ds", "{}", "SUCCESS", null, req);

        verify(auditLogMapper).insert(argThat(log ->
                log.getUsername() == null &&
                        "192.168.1.1".equals(log.getIpAddress())));
    }

    @Test
    @DisplayName("记录审计日志 - X-Forwarded-For 为 unknown 时回退到 RemoteAddr")
    void logAction_forwardedForUnknown_fallsBackToRemoteAddr() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(req.getHeader("Proxy-Client-IP")).thenReturn("unknown");
        when(req.getHeader("WL-Proxy-Client-IP")).thenReturn("");
        when(req.getRemoteAddr()).thenReturn("10.1.2.3");
        when(req.getHeader("User-Agent")).thenReturn("UA");

        securityService.logAction("A", "B", 1L, "n", "{}", "OK", null, req);

        verify(auditLogMapper).insert(argThat(log -> "10.1.2.3".equals(log.getIpAddress())));
    }

    // ============ getAuditLogs ============

    @Test
    @DisplayName("获取审计日志 - 简单重载")
    void getAuditLogs_simpleOverload() {
        Page<AuditLog> page = new Page<>(1, 10);
        page.setRecords(List.of(new AuditLog()));
        page.setTotal(1L);
        when(auditLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = securityService.getAuditLogs(1, 10, 5L, "DATASET");

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
    }

    @Test
    @DisplayName("获取审计日志 - 完整重载带过滤器")
    void getAuditLogs_fullOverloadWithFilters() {
        Page<AuditLog> page = new Page<>(1, 20);
        page.setRecords(new ArrayList<>());
        page.setTotal(0L);
        when(auditLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = securityService.getAuditLogs(1, 20, 5L, "DATASET",
                "VIEW", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertNotNull(result);
        assertEquals(0, result.getRecords().size());
    }

    @Test
    @DisplayName("获取审计日志 - null 过滤器全部跳过")
    void getAuditLogs_nullFilters() {
        Page<AuditLog> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0L);
        when(auditLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = securityService.getAuditLogs(1, 10, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("获取审计日志 - 空白 resourceType / actionType 不参与过滤")
    void getAuditLogs_blankFilters() {
        Page<AuditLog> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0L);
        when(auditLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = securityService.getAuditLogs(1, 10, null, "  ", "  ", null, null);

        assertNotNull(result);
    }

    // ============ getComplianceSummary ============

    @Test
    @DisplayName("合规摘要 - 高风险为 0 时隐私保护标记为 true")
    void getComplianceSummary_noHighRisk() {
        when(auditLogMapper.selectCount(any())).thenReturn(5L);
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(0L);
        when(securityScanTaskMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> summary = securityService.getComplianceSummary();

        assertEquals(5L, summary.get("auditLogs"));
        assertEquals(0L, summary.get("highRiskCount"));
        assertEquals(3L, summary.get("securityScans"));
        assertEquals(true, summary.get("auditTrailComplete"));
        assertEquals(true, summary.get("privacyProtectionConfigured"));
        assertNotNull(summary.get("standards"));
    }

    @Test
    @DisplayName("合规摘要 - 有高风险但有扫描记录时隐私保护仍为 true")
    void getComplianceSummary_highRiskButScansExist() {
        when(auditLogMapper.selectCount(any())).thenReturn(5L);
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(2L);
        when(securityScanTaskMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> summary = securityService.getComplianceSummary();

        assertEquals(true, summary.get("privacyProtectionConfigured"));
    }

    @Test
    @DisplayName("合规摘要 - 无审计日志且无扫描时审计未完成")
    void getComplianceSummary_emptySystem() {
        when(auditLogMapper.selectCount(any())).thenReturn(0L);
        when(sensitiveDataDetectionMapper.selectCount(any())).thenReturn(0L);
        when(securityScanTaskMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> summary = securityService.getComplianceSummary();

        assertEquals(false, summary.get("auditTrailComplete"));
        assertEquals(true, summary.get("privacyProtectionConfigured"));
    }

    // ============ maskSensitiveData ============

    @Test
    @DisplayName("脱敏数据 - 数据集不存在抛出异常")
    void maskSensitiveData_datasetNotFound() {
        when(datasetMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityService.maskSensitiveData(1L, Map.of("fields", List.of("phone")), null));
        assertEquals("Dataset not found", ex.getMessage());
    }

    @Test
    @DisplayName("脱敏数据 - 带 authentication 时校验归属")
    void maskSensitiveData_withAuth() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 1);
        Authentication authentication = auth("admin");
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(9L);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(new ArrayList<>());

        Map<String, Object> result = securityService.maskSensitiveData(1L,
                Map.of("fields", List.of("phone")), authentication);

        verify(accessValidator).validateDatasetOwnership(1L, 9L);
        assertEquals("COMPLETED", result.get("status"));
    }

    @Test
    @DisplayName("脱敏数据 - 行数据为空时只返回元信息")
    void maskSensitiveData_emptyRows() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 0);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(new ArrayList<>());

        Map<String, Object> result = securityService.maskSensitiveData(1L,
                Map.of("fields", List.of("phone"), "maskType", "partial"), null);

        assertEquals("COMPLETED", result.get("status"));
        assertEquals("partial", result.get("maskType"));
        assertNull(result.get("maskedFileUrl"));
    }

    @Test
    @DisplayName("脱敏数据 - 成功生成脱敏文件和预览")
    void maskSensitiveData_success() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\",\"name\":\"string\"}", 2);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> r1 = new LinkedHashMap<>();
        r1.put("phone", "13812345678");
        r1.put("name", "张三");
        Map<String, String> r2 = new LinkedHashMap<>();
        r2.put("phone", "13987654321");
        r2.put("name", "李四");
        rows.add(r1);
        rows.add(r2);
        when(dataReader.readRows(ds)).thenReturn(rows);
        when(localStorageService.writeFile(any(byte[].class), anyString())).thenReturn("masked_file.csv");

        Map<String, Object> result = securityService.maskSensitiveData(1L,
                Map.of("fields", List.of("phone", "name"), "maskType", "partial"), null);

        assertEquals("COMPLETED", result.get("status"));
        assertEquals("/api/v1/files/masked_file.csv", result.get("maskedFileUrl"));
        assertEquals(2, result.get("totalRowsMasked"));
        assertEquals(2, result.get("totalFieldsMasked"));
        List<Map<String, Object>> preview = (List<Map<String, Object>>) result.get("maskedPreview");
        assertEquals(2, preview.size());
    }

    @Test
    @DisplayName("脱敏数据 - 自定义 fieldRules 应用规则")
    void maskSensitiveData_customFieldRules() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("phone", "13812345678")));
        when(localStorageService.writeFile(any(byte[].class), anyString())).thenReturn("f.csv");

        Map<String, Object> rule = Map.of("fieldName", "phone", "maskType", "complete");
        Map<String, Object> request = Map.of("fields", List.of("phone"), "maskType", "partial", "fieldRules", List.of(rule));

        Map<String, Object> result = securityService.maskSensitiveData(1L, request, null);

        List<Map<String, Object>> preview = (List<Map<String, Object>>) result.get("maskedPreview");
        // complete mask should yield **** for phone
        assertEquals("****", preview.get(0).get("phone"));
    }

    @Test
    @DisplayName("脱敏数据 - email maskType 应用规则")
    void maskSensitiveData_emailMaskType() {
        Dataset ds = dataset(1L, "PARSED", "{\"email\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("email", "user@example.com")));
        when(localStorageService.writeFile(any(byte[].class), anyString())).thenReturn("f.csv");

        Map<String, Object> rule = Map.of("fieldName", "email", "maskType", "email");
        Map<String, Object> request = Map.of("fields", List.of("email"), "fieldRules", List.of(rule));

        Map<String, Object> result = securityService.maskSensitiveData(1L, request, null);

        List<Map<String, Object>> preview = (List<Map<String, Object>>) result.get("maskedPreview");
        assertEquals("us***@example.com", preview.get(0).get("email"));
    }

    @Test
    @DisplayName("脱敏数据 - hash maskType")
    void maskSensitiveData_hashMaskType() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("phone", "13812345678")));
        when(localStorageService.writeFile(any(byte[].class), anyString())).thenReturn("f.csv");

        Map<String, Object> rule = Map.of("fieldName", "phone", "maskType", "hash");
        Map<String, Object> request = Map.of("fields", List.of("phone"), "fieldRules", List.of(rule));

        Map<String, Object> result = securityService.maskSensitiveData(1L, request, null);

        List<Map<String, Object>> preview = (List<Map<String, Object>>) result.get("maskedPreview");
        assertEquals("***HASH***", preview.get(0).get("phone"));
    }

    @Test
    @DisplayName("脱敏数据 - 敏感 header (password) 默认应用 mask")
    void maskSensitiveData_sensitiveHeaderDefault() {
        Dataset ds = dataset(1L, "PARSED", "{\"password\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("password", "secret123")));
        when(localStorageService.writeFile(any(byte[].class), anyString())).thenReturn("f.csv");

        Map<String, Object> request = Map.of("fields", List.of("password"), "maskType", "partial");

        Map<String, Object> result = securityService.maskSensitiveData(1L, request, null);

        List<Map<String, Object>> preview = (List<Map<String, Object>>) result.get("maskedPreview");
        // password matches FIELD_NAME_ONLY rule "密码" + the isSensitiveHeader check
        assertNotNull(preview.get(0).get("password"));
        assertNotEquals("secret123", preview.get(0).get("password"));
    }

    @Test
    @DisplayName("脱敏数据 - 读取行数据抛异常时返回错误信息")
    void maskSensitiveData_readThrows_returnsError() {
        Dataset ds = dataset(1L, "PARSED", "{\"phone\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenThrow(new RuntimeException("io failure"));

        Map<String, Object> result = securityService.maskSensitiveData(1L,
                Map.of("fields", List.of("phone")), null);

        assertEquals("COMPLETED", result.get("status"));
        assertNotNull(result.get("error"));
        assertTrue(((String) result.get("error")).contains("Masking failed"));
    }

    @Test
    @DisplayName("脱敏数据 - 默认 maskType 为 partial")
    void maskSensitiveData_defaultMaskType() {
        Dataset ds = dataset(1L, "PARSED", "{\"foo\":\"string\"}", 1);
        when(datasetMapper.selectById(1L)).thenReturn(ds);
        when(dataReader.readRows(ds)).thenReturn(List.of(Map.of("foo", "abc123def")));
        when(localStorageService.writeFile(any(byte[].class), anyString())).thenReturn("f.csv");

        Map<String, Object> request = Map.of("fields", List.of("foo"));

        Map<String, Object> result = securityService.maskSensitiveData(1L, request, null);

        assertEquals("partial", result.get("maskType"));
        List<Map<String, Object>> preview = (List<Map<String, Object>>) result.get("maskedPreview");
        // foo is not a sensitive header, no rule matches - value returned as-is
        assertEquals("abc123def", preview.get(0).get("foo"));
    }
}
