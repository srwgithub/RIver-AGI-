package com.river.agi.security.controller;

import com.river.agi.security.entity.AuditLog;
import com.river.agi.security.service.SecurityService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit", description = "Audit log APIs")
public class AuditController {
    
    private final SecurityService securityService;
    
    @GetMapping("/logs")
    @Operation(summary = "Get audit logs", description = "Get audit logs with filters")
    public ApiResponse<PageResult<AuditLog>> getAuditLogs(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "User ID filter") @RequestParam(required = false) Long userId,
            @Parameter(description = "Resource type filter") @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(securityService.getAuditLogs(page, size, userId, resourceType, actionType, startDate, endDate));
    }

    @GetMapping("/compliance-summary")
    public ApiResponse<Map<String, Object>> complianceSummary() {
        return ApiResponse.ok(securityService.getComplianceSummary());
    }

    @GetMapping(value = "/compliance-report", produces = "application/json;charset=UTF-8")
    public ResponseEntity<byte[]> complianceReport() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportName", "RIver AGI 数据安全合规审计报告");
        report.put("standards", new String[]{"《数据安全法》", "《个人信息保护法》"});
        report.put("generatedAt", java.time.LocalDateTime.now().toString());
        report.put("summary", securityService.getComplianceSummary());
        byte[] body = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=compliance_report.json").body(body);
    }

    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> exportAuditLogs(@RequestParam(required = false) Long userId,
                                                   @RequestParam(required = false) String resourceType) {
        var page = securityService.getAuditLogs(1, 10000, userId, resourceType, null, null, null);
        StringBuilder csv = new StringBuilder("ID,操作类型,资源类型,操作人,IP,结果,时间,详情\n");
        for (AuditLog log : page.getRecords()) {
            csv.append(csv(log.getId())).append(',').append(csv(log.getActionType())).append(',')
                    .append(csv(log.getResourceType())).append(',').append(csv(log.getUsername())).append(',')
                    .append(csv(log.getIpAddress())).append(',').append(csv(log.getResult())).append(',')
                    .append(csv(log.getCreatedAt())).append(',').append(csv(log.getOperationDetails())).append('\n');
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=audit_logs.csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csv(Object value) { return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\""; }
}
