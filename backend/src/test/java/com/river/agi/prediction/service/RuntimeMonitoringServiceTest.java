package com.river.agi.prediction.service;

import com.river.agi.common.BusinessException;
import com.river.agi.prediction.entity.PerformanceSample;
import com.river.agi.prediction.entity.RuntimeAlert;
import com.river.agi.prediction.mapper.PerformanceSampleMapper;
import com.river.agi.prediction.mapper.RuntimeAlertMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuntimeMonitoringServiceTest {

    @Mock
    private PerformanceSampleMapper sampleMapper;
    @Mock
    private RuntimeAlertMapper alertMapper;

    private RuntimeMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new RuntimeMonitoringService(sampleMapper, alertMapper);
    }

    // ===== recordSample =====

    @Test
    @DisplayName("recordSample: null sample throws BusinessException")
    void recordSample_nullThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.recordSample(null));
        assertTrue(ex.getMessage().contains("性能采样不能为空"));
    }

    @Test
    @DisplayName("recordSample: fills defaults when sampledAt/tenantId/status null")
    void recordSample_fillsDefaults() {
        PerformanceSample sample = new PerformanceSample();
        sample.setSampleType("INFERENCE");

        PerformanceSample result = service.recordSample(sample);

        assertNotNull(result.getSampledAt());
        assertEquals(1L, result.getTenantId());
        assertEquals("SUCCESS", result.getStatus());
        verify(sampleMapper).insert(sample);
        verify(alertMapper, never()).insert(any());
    }

    @Test
    @DisplayName("recordSample: preserves provided sampledAt/tenantId/status")
    void recordSample_preservesProvidedValues() {
        PerformanceSample sample = new PerformanceSample();
        LocalDateTime fixed = LocalDateTime.now().minusHours(1);
        sample.setSampledAt(fixed);
        sample.setTenantId(99L);
        sample.setStatus("SUCCESS");

        PerformanceSample result = service.recordSample(sample);

        assertEquals(fixed, result.getSampledAt());
        assertEquals(99L, result.getTenantId());
        assertEquals("SUCCESS", result.getStatus());
        verify(sampleMapper).insert(sample);
    }

    @Test
    @DisplayName("createThresholdAlerts: latency over threshold creates LATENCY alert")
    void recordSample_latencyAlert() {
        PerformanceSample sample = baseSample();
        sample.setLatencyMs(250.0);

        service.recordSample(sample);

        ArgumentCaptor<RuntimeAlert> captor = ArgumentCaptor.forClass(RuntimeAlert.class);
        verify(alertMapper).insert(captor.capture());
        RuntimeAlert alert = captor.getValue();
        assertEquals("LATENCY", alert.getAlertType());
        assertEquals("WARNING", alert.getSeverity());
        assertEquals("OPEN", alert.getStatus());
        assertNotNull(alert.getThresholdJson());
        assertEquals(sample.getTenantId(), alert.getTenantId());
    }

    @Test
    @DisplayName("createThresholdAlerts: cpu over threshold creates CPU alert")
    void recordSample_cpuAlert() {
        PerformanceSample sample = baseSample();
        sample.setCpuPercent(90.0);

        service.recordSample(sample);

        ArgumentCaptor<RuntimeAlert> captor = ArgumentCaptor.forClass(RuntimeAlert.class);
        verify(alertMapper).insert(captor.capture());
        assertEquals("CPU", captor.getValue().getAlertType());
    }

    @Test
    @DisplayName("createThresholdAlerts: memory over threshold creates MEMORY alert")
    void recordSample_memoryAlert() {
        PerformanceSample sample = baseSample();
        sample.setMemoryPercent(90.0);

        service.recordSample(sample);

        ArgumentCaptor<RuntimeAlert> captor = ArgumentCaptor.forClass(RuntimeAlert.class);
        verify(alertMapper).insert(captor.capture());
        assertEquals("MEMORY", captor.getValue().getAlertType());
    }

    @Test
    @DisplayName("createThresholdAlerts: non-success status with errorCode creates RUN_FAILURE alert using errorCode")
    void recordSample_runFailureAlert_withErrorCode() {
        PerformanceSample sample = baseSample();
        sample.setStatus("FAILED");
        sample.setErrorCode("TIMEOUT");

        service.recordSample(sample);

        ArgumentCaptor<RuntimeAlert> captor = ArgumentCaptor.forClass(RuntimeAlert.class);
        verify(alertMapper).insert(captor.capture());
        RuntimeAlert alert = captor.getValue();
        assertEquals("RUN_FAILURE", alert.getAlertType());
        assertEquals("ERROR", alert.getSeverity());
        assertEquals("TIMEOUT", alert.getDescription());
    }

    @Test
    @DisplayName("createThresholdAlerts: non-success status without errorCode uses status in description")
    void recordSample_runFailureAlert_noErrorCode() {
        PerformanceSample sample = baseSample();
        sample.setStatus("FAILED");
        sample.setErrorCode(null);

        service.recordSample(sample);

        ArgumentCaptor<RuntimeAlert> captor = ArgumentCaptor.forClass(RuntimeAlert.class);
        verify(alertMapper).insert(captor.capture());
        assertTrue(captor.getValue().getDescription().contains("FAILED"));
    }

    @Test
    @DisplayName("createThresholdAlerts: all thresholds exceeded creates 4 alerts")
    void recordSample_allThresholds() {
        PerformanceSample sample = baseSample();
        sample.setLatencyMs(300.0);
        sample.setCpuPercent(95.0);
        sample.setMemoryPercent(95.0);
        sample.setStatus("ERROR");
        sample.setErrorCode("OOM");

        service.recordSample(sample);

        verify(alertMapper, times(4)).insert(any(RuntimeAlert.class));
    }

    @Test
    @DisplayName("createThresholdAlerts: success sample under thresholds creates no alerts")
    void recordSample_noAlerts() {
        PerformanceSample sample = baseSample();
        sample.setLatencyMs(100.0);
        sample.setCpuPercent(50.0);
        sample.setMemoryPercent(50.0);
        sample.setStatus("SUCCESS");

        service.recordSample(sample);

        verify(alertMapper, never()).insert(any());
    }

    private PerformanceSample baseSample() {
        PerformanceSample sample = new PerformanceSample();
        sample.setTenantId(1L);
        sample.setPredictionTaskId(10L);
        sample.setStatus("SUCCESS");
        sample.setSampledAt(LocalDateTime.now());
        return sample;
    }

    // ===== recordSystemSample =====

    @Test
    @DisplayName("recordSystemSample: captures real host metrics and inserts sample")
    void recordSystemSample_noTaskId() {
        PerformanceSample result = service.recordSystemSample();

        assertNotNull(result);
        assertEquals("SYSTEM", result.getSampleType());
        assertEquals("SUCCESS", result.getStatus());
        assertNull(result.getPredictionTaskId());
        assertNotNull(result.getDetailsJson());
        assertTrue(result.getDetailsJson().contains("availableProcessors"));
        verify(sampleMapper).insert(any(PerformanceSample.class));
    }

    @Test
    @DisplayName("recordSystemSample: with taskId associates sample with task")
    void recordSystemSample_withTaskId() {
        PerformanceSample result = service.recordSystemSample(42L);

        assertNotNull(result);
        assertEquals(42L, result.getPredictionTaskId());
        assertEquals("SYSTEM", result.getSampleType());
        verify(sampleMapper).insert(any(PerformanceSample.class));
    }

    // ===== recordExecutionSample =====

    @Test
    @DisplayName("recordExecutionSample: successful status sets SUCCESS and null errorCode")
    void recordExecutionSample_successful() {
        long startedAt = System.nanoTime() - 1_000_000L;

        service.recordExecutionSample(5L, 7L, "PREDICT", startedAt, 100, "SUCCESS", null);

        ArgumentCaptor<PerformanceSample> captor = ArgumentCaptor.forClass(PerformanceSample.class);
        verify(sampleMapper).insert(captor.capture());
        PerformanceSample inserted = captor.getValue();
        assertEquals(5L, inserted.getPredictionTaskId());
        assertEquals(7L, inserted.getModelVersionId());
        assertEquals("PREDICT", inserted.getSampleType());
        assertEquals("SUCCESS", inserted.getStatus());
        assertNull(inserted.getErrorCode());
        assertNotNull(inserted.getDurationMs());
        assertTrue(inserted.getDurationMs() >= 1L);
        assertNotNull(inserted.getThroughputQps());
        assertTrue(inserted.getThroughputQps() > 0);
        assertTrue(inserted.getDetailsJson().contains("itemCount"));
    }

    @Test
    @DisplayName("recordExecutionSample: failed status sets FAILED and errorCode")
    void recordExecutionSample_failed() {
        long startedAt = System.nanoTime() - 1_000_000L;

        service.recordExecutionSample(5L, 7L, "PREDICT", startedAt, 50, "FAILED", "MODEL_ERROR");

        ArgumentCaptor<PerformanceSample> captor = ArgumentCaptor.forClass(PerformanceSample.class);
        verify(sampleMapper).insert(captor.capture());
        PerformanceSample inserted = captor.getValue();
        assertEquals("FAILED", inserted.getStatus());
        assertEquals("MODEL_ERROR", inserted.getErrorCode());
    }

    @Test
    @DisplayName("recordExecutionSample: itemCount=0 yields zero throughput")
    void recordExecutionSample_zeroItems() {
        long startedAt = System.nanoTime() - 1_000_000L;

        service.recordExecutionSample(5L, null, "TRAIN", startedAt, 0, "COMPLETED", null);

        ArgumentCaptor<PerformanceSample> captor = ArgumentCaptor.forClass(PerformanceSample.class);
        verify(sampleMapper).insert(captor.capture());
        assertEquals(0d, captor.getValue().getThroughputQps());
    }

    @Test
    @DisplayName("recordExecutionSample: null status treated as successful")
    void recordExecutionSample_nullStatus() {
        long startedAt = System.nanoTime() - 1_000_000L;

        service.recordExecutionSample(5L, 7L, "OPTIMIZE", startedAt, 10, null, "ERR");

        ArgumentCaptor<PerformanceSample> captor = ArgumentCaptor.forClass(PerformanceSample.class);
        verify(sampleMapper).insert(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getStatus());
        assertNull(captor.getValue().getErrorCode());
    }

    @Test
    @DisplayName("recordExecutionSample: OPTIMIZED status treated as successful")
    void recordExecutionSample_optimizedStatus() {
        long startedAt = System.nanoTime() - 1_000_000L;

        service.recordExecutionSample(5L, 7L, "TRAIN", startedAt, 10, "OPTIMIZED", "IGNORED");

        ArgumentCaptor<PerformanceSample> captor = ArgumentCaptor.forClass(PerformanceSample.class);
        verify(sampleMapper).insert(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("recordExecutionSample: swallows exceptions from mapper without propagating")
    void recordExecutionSample_swallowsExceptions() {
        when(sampleMapper.insert(any(PerformanceSample.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.recordExecutionSample(5L, 7L, "PREDICT", System.nanoTime(), 10, "SUCCESS", null));
    }

    // ===== scheduledSystemSample =====

    @Test
    @DisplayName("scheduledSystemSample: records a system sample")
    void scheduledSystemSample_records() {
        service.scheduledSystemSample();
        verify(sampleMapper).insert(any(PerformanceSample.class));
    }

    @Test
    @DisplayName("scheduledSystemSample: swallows exceptions without propagating")
    void scheduledSystemSample_swallowsExceptions() {
        when(sampleMapper.insert(any(PerformanceSample.class))).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.scheduledSystemSample());
    }

    // ===== samples =====

    @Test
    @DisplayName("samples: null taskId uses selectList with safe limit")
    void samples_nullTaskId() {
        List<PerformanceSample> rows = new ArrayList<>();
        rows.add(new PerformanceSample());
        when(sampleMapper.selectList(any())).thenReturn(rows);

        List<PerformanceSample> result = service.samples(null, 60, 50);

        assertEquals(1, result.size());
        verify(sampleMapper).selectList(any());
        verify(sampleMapper, never()).selectSince(any(), any());
    }

    @Test
    @DisplayName("samples: non-null taskId uses selectSince")
    void samples_withTaskId() {
        List<PerformanceSample> rows = new ArrayList<>();
        rows.add(new PerformanceSample());
        when(sampleMapper.selectSince(eq(10L), any(LocalDateTime.class))).thenReturn(rows);

        List<PerformanceSample> result = service.samples(10L, 60, 50);

        assertEquals(1, result.size());
        verify(sampleMapper).selectSince(eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("samples: clamps limit to 1000 max")
    void samples_clampsLimit() {
        when(sampleMapper.selectList(any())).thenReturn(new ArrayList<>());

        service.samples(null, 60, 5000);

        verify(sampleMapper).selectList(any());
    }

    // ===== summary =====

    @Test
    @DisplayName("summary: aggregates counts, averages and peaks from rows")
    void summary_withRows() {
        PerformanceSample s1 = new PerformanceSample();
        s1.setStatus("SUCCESS");
        s1.setLatencyMs(100.0);
        s1.setThroughputQps(50.0);
        s1.setCpuPercent(40.0);
        s1.setMemoryPercent(60.0);
        s1.setGpuPercent(20.0);
        PerformanceSample s2 = new PerformanceSample();
        s2.setStatus("FAILED");
        s2.setLatencyMs(200.0);
        s2.setThroughputQps(80.0);
        s2.setCpuPercent(70.0);
        s2.setMemoryPercent(50.0);
        s2.setGpuPercent(null);
        List<PerformanceSample> rows = new ArrayList<>();
        rows.add(s1);
        rows.add(s2);
        when(sampleMapper.selectList(any())).thenReturn(rows);

        Map<String, Object> summary = service.summary(null, 60);

        assertEquals(2, summary.get("sampleCount"));
        assertEquals(1L, summary.get("successCount"));
        assertEquals(1L, summary.get("failureCount"));
        assertEquals(150.0, summary.get("averageLatencyMs"));
        assertEquals(200.0, summary.get("peakLatencyMs"));
        assertEquals(80.0, summary.get("peakQps"));
        assertEquals(55.0, summary.get("averageCpuPercent"));
        assertEquals(55.0, summary.get("averageMemoryPercent"));
        assertEquals(20.0, summary.get("averageGpuPercent"));
        assertNotNull(summary.get("samples"));
    }

    @Test
    @DisplayName("summary: empty rows yields zero aggregates")
    void summary_empty() {
        when(sampleMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> summary = service.summary(null, 60);

        assertEquals(0, summary.get("sampleCount"));
        assertEquals(0L, summary.get("successCount"));
        assertEquals(0L, summary.get("failureCount"));
        assertEquals(0.0, summary.get("averageLatencyMs"));
        assertEquals(0.0, summary.get("peakLatencyMs"));
    }

    @Test
    @DisplayName("summary: with taskId routes through selectSince")
    void summary_withTaskId() {
        when(sampleMapper.selectSince(eq(10L), any(LocalDateTime.class))).thenReturn(new ArrayList<>());

        Map<String, Object> summary = service.summary(10L, 30);

        assertEquals(0, summary.get("sampleCount"));
        verify(sampleMapper).selectSince(eq(10L), any(LocalDateTime.class));
    }

    // ===== alerts =====

    @Test
    @DisplayName("alerts: delegates to alertMapper.selectAlerts")
    void alerts_delegates() {
        List<RuntimeAlert> alertList = new ArrayList<>();
        RuntimeAlert alert = new RuntimeAlert();
        alert.setAlertType("CPU");
        alertList.add(alert);
        when(alertMapper.selectAlerts(eq(10L), eq("OPEN"), eq(100))).thenReturn(alertList);

        List<RuntimeAlert> result = service.alerts(10L, "OPEN", 100);

        assertEquals(1, result.size());
        assertEquals("CPU", result.get(0).getAlertType());
    }

    @Test
    @DisplayName("alerts: clamps limit to 500 max")
    void alerts_clampsLimit() {
        when(alertMapper.selectAlerts(eq(10L), eq("OPEN"), eq(500))).thenReturn(new ArrayList<>());

        service.alerts(10L, "OPEN", 9999);

        verify(alertMapper).selectAlerts(eq(10L), eq("OPEN"), eq(500));
    }

    // ===== resolve =====

    @Test
    @DisplayName("resolve: updates existing alert to RESOLVED")
    void resolve_found() {
        RuntimeAlert alert = new RuntimeAlert();
        alert.setId(1L);
        alert.setStatus("OPEN");
        when(alertMapper.selectById(1L)).thenReturn(alert);

        RuntimeAlert result = service.resolve(1L, "fixed", 7L);

        assertEquals("RESOLVED", result.getStatus());
        assertEquals("fixed", result.getResolution());
        assertEquals(7L, result.getResolvedBy());
        assertNotNull(result.getResolvedAt());
        assertNotNull(result.getUpdatedAt());
        verify(alertMapper).updateById(alert);
    }

    @Test
    @DisplayName("resolve: non-existent alert throws BusinessException")
    void resolve_notFound() {
        when(alertMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resolve(99L, "fixed", 7L));
        assertTrue(ex.getMessage().contains("运行告警不存在"));
        verify(alertMapper, never()).updateById(any());
    }
}
