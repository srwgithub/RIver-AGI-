package com.river.agi.common.aspect;

import com.river.agi.common.SecurityUtils;
import com.river.agi.common.annotation.AuditOperation;
import com.river.agi.security.entity.AuditLog;
import com.river.agi.security.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final SecurityUtils securityUtils;
    private final Executor auditExecutor;

    public AuditLogAspect(AuditLogMapper auditLogMapper,
                          ObjectMapper objectMapper,
                          SecurityUtils securityUtils,
                          @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") Executor auditExecutor) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
        this.securityUtils = securityUtils;
        this.auditExecutor = auditExecutor;
    }
    
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(password|passwd|secret|token|key|authorization|credit_card|ssn|social_security)",
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("\\d{16,19}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+");
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(\\\"(?:password|passwd|secret|token|key|authorization|credit_card|ssn|social_security)\\\"\\s*:\\s*)(\\\".*?\\\"|\\d+|true|false|null)",
            Pattern.CASE_INSENSITIVE);
    
    @Around("@annotation(com.river.agi.common.annotation.AuditOperation)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        AuditOperation auditAnnotation = method.getAnnotation(AuditOperation.class);
        
        String action = auditAnnotation.action();
        String resourceType = auditAnnotation.resourceType();
        String description = auditAnnotation.description();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        Long resourceId = null;
        String resourceName = null;
        
        Object[] args = point.getArgs();
        if (args.length > 0) {
            Object firstArg = args[0];
            if (firstArg instanceof Long) {
                resourceId = (Long) firstArg;
            } else if (firstArg instanceof String) {
                resourceName = maskSensitiveData((String) firstArg);
            }
        }
        
        long startTime = System.currentTimeMillis();
        String result = "SUCCESS";
        String operationDetails = auditDetails(description, null, traceId);
        
        try {
            Object returnValue = point.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            if (returnValue != null) {
                try {
                    String returnJson = objectMapper.writeValueAsString(returnValue);
                    String maskedJson = maskSensitiveData(returnJson);
                    if (maskedJson.length() > 500) {
                        operationDetails = auditDetails(description, "[Response truncated]", traceId);
                    } else {
                        operationDetails = auditDetails(description, maskedJson, traceId);
                    }
                } catch (Exception e) {
                    operationDetails = auditDetails(description, "[Response serialization failed]", traceId);
                }
            }
            
            asyncSaveAuditLog(action, resourceType, resourceId, resourceName,
                    operationDetails, result, duration, traceId);

            return returnValue;
        } catch (Exception e) {
            result = "FAILED";
            operationDetails = auditDetails(description,
                    "Error: " + maskSensitiveData(e.getMessage()), traceId);
            long duration = System.currentTimeMillis() - startTime;

            asyncSaveAuditLog(action, resourceType, resourceId, resourceName,
                    operationDetails, result, duration, traceId);

            throw e;
        }
    }

    /** The audit_log.operation_details column is JSON; keep every audit write valid. */
    private String auditDetails(String description, String response, String traceId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("description", description);
        details.put("traceId", traceId);
        if (response != null) {
            details.put("response", response);
        }
        try {
            return truncate(objectMapper.writeValueAsString(details), 2000);
        } catch (Exception e) {
            return "{\"description\":\"audit serialization failed\"}";
        }
    }
    
    private void asyncSaveAuditLog(String action, String resourceType, Long resourceId,
                                   String resourceName, String operationDetails, String result,
                                   long duration, String traceId) {
        // SecurityContext and RequestContext are thread-local; capture them on the request thread
        // before dispatching the DB insert to the async executor.
        AuditLog auditLog = new AuditLog();
        auditLog.setActionType(action);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId);
        auditLog.setResourceName(resourceName);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            auditLog.setUsername(auth.getName());
            try {
                Long userId = securityUtils.getCurrentUserId(auth);
                auditLog.setUserId(userId);
            } catch (Exception e) {
                log.warn("Failed to get user ID for audit log", e);
            }
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            auditLog.setIpAddress(getClientIp(request));
            auditLog.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            auditLog.setRequestMethod(request.getMethod());
            auditLog.setRequestPath(request.getRequestURI());
            request.setAttribute("traceId", traceId);
        }

        auditLog.setOperationDetails(truncate(operationDetails, 2000));
        auditLog.setResult(result);
        auditLog.setDurationMs(duration);
        auditLog.setCreatedAt(LocalDateTime.now());

        try {
            auditExecutor.execute(() -> {
                try {
                    auditLogMapper.insert(auditLog);
                } catch (Exception e) {
                    log.error("Failed to insert audit log asynchronously", e);
                }
            });
        } catch (Exception e) {
            // Executor queue saturated — fall back to synchronous insert so the audit trail is not lost.
            log.warn("Audit executor rejected task, falling back to synchronous insert", e);
            try {
                auditLogMapper.insert(auditLog);
            } catch (Exception ex) {
                log.error("Failed to save audit log (fallback)", ex);
            }
        }
    }
    
    private String maskSensitiveData(String input) {
        if (input == null) return null;
        
        String masked = input;
        
        Matcher idCardMatcher = ID_CARD_PATTERN.matcher(masked);
        masked = idCardMatcher.replaceAll(m -> {
            String id = m.group();
            return id.substring(0, 4) + "*********" + id.substring(13);
        });
        
        Matcher phoneMatcher = PHONE_PATTERN.matcher(masked);
        masked = phoneMatcher.replaceAll(m -> {
            String phone = m.group();
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });
        
        Matcher bankCardMatcher = BANK_CARD_PATTERN.matcher(masked);
        masked = bankCardMatcher.replaceAll(m -> {
            String card = m.group();
            return card.substring(0, 4) + "****" + card.substring(card.length() - 4);
        });
        
        Matcher emailMatcher = EMAIL_PATTERN.matcher(masked);
        masked = emailMatcher.replaceAll(m -> {
            String email = m.group();
            int atIndex = email.indexOf('@');
            if (atIndex > 2) {
                return email.substring(0, 2) + "***" + email.substring(atIndex);
            }
            return email;
        });
        
        return SENSITIVE_FIELD_PATTERN.matcher(masked).replaceAll("$1\"***\"");
    }
    
    private String truncate(String input, int maxLength) {
        if (input == null) return null;
        if (input.length() <= maxLength) return input;
        return input.substring(0, maxLength) + "...[truncated]";
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
