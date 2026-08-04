package com.river.agi.prediction.controller;

import com.river.agi.common.ApiResponse;
import com.river.agi.prediction.entity.PerformanceSample;
import com.river.agi.prediction.service.RuntimeMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/predictions/monitoring", "/v1/predictions/monitoring"})
public class RuntimeMonitoringController {
    private final RuntimeMonitoringService monitoringService;

    @PostMapping("/samples")
    public ApiResponse<PerformanceSample> recordSample(@RequestBody PerformanceSample sample) {
        return ApiResponse.ok(monitoringService.recordSample(sample));
    }

    @PostMapping("/samples/system")
    public ApiResponse<PerformanceSample> recordSystemSample(@RequestParam(required = false) Long taskId) {
        return ApiResponse.ok(monitoringService.recordSystemSample(taskId));
    }

    @GetMapping("/samples")
    public ApiResponse<?> samples(@RequestParam(required = false) Long taskId,
                                  @RequestParam(defaultValue = "1440") int minutes,
                                  @RequestParam(defaultValue = "500") int limit) {
        return ApiResponse.ok(monitoringService.samples(taskId, minutes, limit));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) Long taskId,
                                                     @RequestParam(defaultValue = "1440") int minutes) {
        return ApiResponse.ok(monitoringService.summary(taskId, minutes));
    }

    @GetMapping("/alerts")
    public ApiResponse<?> alerts(@RequestParam(required = false) Long taskId,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(monitoringService.alerts(taskId, status, limit));
    }

    @PostMapping("/alerts/{id}/resolve")
    public ApiResponse<?> resolve(@PathVariable Long id,
                                  @RequestBody(required = false) Map<String, String> body,
                                  Authentication authentication) {
        Long operatorId = null;
        if (authentication != null && authentication.getPrincipal() instanceof com.river.agi.auth.entity.User user) operatorId = user.getId();
        return ApiResponse.ok(monitoringService.resolve(id, body == null ? "已处理" : body.getOrDefault("resolution", "已处理"), operatorId));
    }
}
