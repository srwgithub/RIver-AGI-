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
            @Parameter(description = "Resource type filter") @RequestParam(required = false) String resourceType) {
        return ApiResponse.ok(securityService.getAuditLogs(page, size, userId, resourceType));
    }

    @GetMapping(value = "/logs/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportAuditLogs(@RequestParam(required = false) Long userId,
                                                   @RequestParam(required = false) String resourceType) {
        var page = securityService.getAuditLogs(1, 10000, userId, resourceType);
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
