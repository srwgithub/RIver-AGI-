package com.river.agi.security.service;

import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.dataset.service.LocalStorageService;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.mapper.AuditLogMapper;
import com.river.agi.security.mapper.SensitiveDataDetectionMapper;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    
    private SecurityService securityService;
    private ObjectMapper objectMapper;
    private LocalStorageService localStorageService;
    private DatasetDataReaderService dataReader;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        localStorageService = new LocalStorageService();
        dataReader = new DatasetDataReaderService(localStorageService, objectMapper);
        
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
    
    @Test
    @DisplayName("获取安全扫描仪表板 - 统计正确")
    void getSecurityDashboard_countsCorrect() {
        when(securityScanTaskMapper.selectCount(any())).thenReturn(10L);
        
        List<Long> counts = List.of(20L, 3L, 5L, 12L);
        when(sensitiveDataDetectionMapper.selectCount(any()))
            .thenReturn(20L, 3L, 5L, 12L);
        
        Map<String, Object> dashboard = securityService.getSecurityDashboard();
        
        assertNotNull(dashboard);
        assertEquals(10L, dashboard.get("totalScans"));
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
        assertEquals(0L, dashboard.get("highRiskCount"));
    }
    
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
    
    @Test
    @DisplayName("获取扫描结果 - 无扫描记录返回NO_SCAN")
    void getScanResults_noRecordReturnsNoScan() {
        when(securityScanTaskMapper.selectOne(any())).thenReturn(null);
        
        Map<String, Object> result = securityService.getScanResults(1L);
        
        assertNotNull(result);
        assertEquals("NO_SCAN", result.get("status"));
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
        
        when(securityScanTaskMapper.selectOne(any())).thenReturn(task);
        
        Map<String, Object> result = securityService.getScanResults(1L);
        
        assertNotNull(result);
        assertEquals("COMPLETED", result.get("status"));
    }
}
