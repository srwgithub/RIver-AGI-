package com.river.agi.prediction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.common.BusinessException;
import com.river.agi.prediction.entity.PerformanceSample;
import com.river.agi.prediction.entity.RuntimeAlert;
import com.river.agi.prediction.mapper.PerformanceSampleMapper;
import com.river.agi.prediction.mapper.RuntimeAlertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.sun.management.OperatingSystemMXBean;

@Service
@RequiredArgsConstructor
public class RuntimeMonitoringService {
    private final PerformanceSampleMapper sampleMapper;
    private final RuntimeAlertMapper alertMapper;

    @Transactional
    public PerformanceSample recordSample(PerformanceSample sample) {
        if (sample == null) throw new BusinessException("性能采样不能为空");
        if (sample.getSampledAt() == null) sample.setSampledAt(LocalDateTime.now());
        if (sample.getTenantId() == null) sample.setTenantId(1L);
        if (sample.getStatus() == null) sample.setStatus("SUCCESS");
        sampleMapper.insert(sample);
        createThresholdAlerts(sample);
        return sample;
    }

    /** Captures host/JVM resources without requiring a prediction task or Python engine. */
    public PerformanceSample recordSystemSample() {
        return recordSystemSample(null);
    }

    /** Records real host/JVM metrics and associates them with the selected task when supplied. */
    public PerformanceSample recordSystemSample(Long taskId) {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        PerformanceSample sample = new PerformanceSample();
        sample.setPredictionTaskId(taskId);
        sample.setSampleType("SYSTEM");
        sample.setStatus("SUCCESS");
        sample.setCpuPercent(percent(os.getCpuLoad()));
        sample.setMemoryPercent(percent(heap.getUsed(), heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()));
        sample.setStorageIoPercent(storageUsage());
        sample.setDetailsJson("{\"jvmHeapUsedBytes\":" + Math.max(0, heap.getUsed())
                + ",\"jvmHeapMaxBytes\":" + Math.max(0, heap.getMax())
                + ",\"availableProcessors\":" + os.getAvailableProcessors() + "}");
        return recordSample(sample);
    }

    /** Records one real prediction/retraining execution with latency and throughput metrics. */
    public void recordExecutionSample(Long taskId, Long modelVersionId, String sampleType,
                                      long startedAtNanos, int itemCount, String status, String errorCode) {
        try {
            long durationNanos = Math.max(1L, System.nanoTime() - startedAtNanos);
            long durationMs = Math.max(1L, Math.round(durationNanos / 1_000_000d));
            double durationSeconds = durationNanos / 1_000_000_000d;
            PerformanceSample sample = currentSystemSample(taskId);
            sample.setModelVersionId(modelVersionId);
            sample.setSampleType(sampleType);
            sample.setDurationMs(durationMs);
            sample.setLatencyMs((double) durationMs);
            sample.setThroughputQps(itemCount > 0 ? round(itemCount / durationSeconds) : 0d);
            boolean successful = isSuccessfulExecution(status);
            sample.setStatus(successful ? "SUCCESS" : "FAILED");
            sample.setErrorCode(successful ? null : errorCode);
            sample.setDetailsJson("{\"itemCount\":" + Math.max(0, itemCount)
                    + ",\"durationNanos\":" + durationNanos
                    + ",\"taskStatus\":\"" + escapeJson(status) + "\"}");
            recordSample(sample);
        } catch (Exception ignored) {
            // Observability must never change the result of the business task.
        }
    }

    private boolean isSuccessfulExecution(String status) {
        return status == null || "SUCCESS".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status)
                || "OPTIMIZED".equalsIgnoreCase(status);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private PerformanceSample currentSystemSample(Long taskId) {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        PerformanceSample sample = new PerformanceSample();
        sample.setPredictionTaskId(taskId);
        sample.setCpuPercent(percent(os.getCpuLoad()));
        sample.setMemoryPercent(percent(heap.getUsed(), heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()));
        sample.setStorageIoPercent(storageUsage());
        return sample;
    }

    @Scheduled(fixedDelayString = "${monitoring.sample-interval-ms:300000}", initialDelayString = "${monitoring.sample-initial-delay-ms:30000}")
    public void scheduledSystemSample() {
        try {
            recordSystemSample();
        } catch (Exception ignored) {
            // Monitoring failures must not interrupt business requests or stop future samples.
        }
    }

    public List<PerformanceSample> samples(Long taskId, int minutes, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        if (taskId == null) return sampleMapper.selectList(new LambdaQueryWrapper<PerformanceSample>()
                .orderByDesc(PerformanceSample::getSampledAt).last("LIMIT " + safeLimit));
        return sampleMapper.selectSince(taskId, LocalDateTime.now().minusMinutes(Math.max(1, Math.min(minutes, 10080))));
    }

    public Map<String, Object> summary(Long taskId, int minutes) {
        List<PerformanceSample> rows = samples(taskId, minutes, 1000);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleCount", rows.size());
        result.put("successCount", rows.stream().filter(x -> "SUCCESS".equalsIgnoreCase(x.getStatus())).count());
        result.put("failureCount", rows.stream().filter(x -> !"SUCCESS".equalsIgnoreCase(x.getStatus())).count());
        result.put("averageLatencyMs", average(rows, PerformanceSample::getLatencyMs));
        result.put("peakLatencyMs", peak(rows, PerformanceSample::getLatencyMs));
        result.put("peakQps", peak(rows, PerformanceSample::getThroughputQps));
        result.put("averageCpuPercent", average(rows, PerformanceSample::getCpuPercent));
        result.put("averageMemoryPercent", average(rows, PerformanceSample::getMemoryPercent));
        result.put("averageGpuPercent", average(rows, PerformanceSample::getGpuPercent));
        result.put("samples", rows);
        return result;
    }

    public List<RuntimeAlert> alerts(Long taskId, String status, int limit) {
        return alertMapper.selectAlerts(taskId, status, Math.max(1, Math.min(limit, 500)));
    }

    public RuntimeAlert resolve(Long id, String resolution, Long operatorId) {
        RuntimeAlert alert = alertMapper.selectById(id);
        if (alert == null) throw new BusinessException("运行告警不存在");
        alert.setStatus("RESOLVED");
        alert.setResolution(resolution);
        alert.setResolvedBy(operatorId);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        alertMapper.updateById(alert);
        return alert;
    }

    private void createThresholdAlerts(PerformanceSample sample) {
        List<AlertSpec> specs = new ArrayList<>();
        if (sample.getLatencyMs() != null && sample.getLatencyMs() > 200) specs.add(new AlertSpec("LATENCY", "WARNING", "推理延迟超过阈值", "延迟 " + sample.getLatencyMs() + " ms"));
        if (sample.getCpuPercent() != null && sample.getCpuPercent() > 85) specs.add(new AlertSpec("CPU", "WARNING", "CPU 占用超过阈值", "CPU " + sample.getCpuPercent() + "%"));
        if (sample.getMemoryPercent() != null && sample.getMemoryPercent() > 85) specs.add(new AlertSpec("MEMORY", "WARNING", "内存占用超过阈值", "内存 " + sample.getMemoryPercent() + "%"));
        if (sample.getStatus() != null && !"SUCCESS".equalsIgnoreCase(sample.getStatus())) specs.add(new AlertSpec("RUN_FAILURE", "ERROR", "运行任务失败", sample.getErrorCode() == null ? "任务状态为 " + sample.getStatus() : sample.getErrorCode()));
        for (AlertSpec spec : specs) {
            RuntimeAlert alert = new RuntimeAlert();
            alert.setTenantId(sample.getTenantId()); alert.setPredictionTaskId(sample.getPredictionTaskId()); alert.setSampleId(sample.getId());
            alert.setAlertType(spec.type); alert.setSeverity(spec.severity); alert.setTitle(spec.title); alert.setDescription(spec.description);
            alert.setStatus("OPEN"); alert.setDetectedAt(sample.getSampledAt()); alert.setCreatedAt(LocalDateTime.now()); alert.setUpdatedAt(LocalDateTime.now());
            alert.setThresholdJson("{\"latencyMs\":200,\"cpuPercent\":85,\"memoryPercent\":85}");
            alertMapper.insert(alert);
        }
    }

    private double average(List<PerformanceSample> rows, java.util.function.Function<PerformanceSample, Double> getter) {
        return round(rows.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0));
    }
    private double peak(List<PerformanceSample> rows, java.util.function.Function<PerformanceSample, Double> getter) {
        return round(rows.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(0));
    }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private Double percent(double ratio) { return ratio < 0 ? null : round(ratio * 100); }
    private Double percent(long used, long total) { return total <= 0 ? null : round((double) used / total * 100); }
    private Double storageUsage() {
        try {
            FileStore store = Files.getFileStore(Paths.get("."));
            return percent(store.getTotalSpace() - store.getUsableSpace(), store.getTotalSpace());
        } catch (Exception ignored) {
            return null;
        }
    }
    private record AlertSpec(String type, String severity, String title, String description) {}
}
