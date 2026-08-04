package com.river.agi.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.auth.entity.Permission;
import com.river.agi.auth.entity.Role;
import com.river.agi.auth.mapper.PermissionMapper;
import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.auth.mapper.RolePermissionMapper;
import com.river.agi.auth.entity.RolePermission;
import com.river.agi.common.ApiResponse;
import com.river.agi.security.entity.SecurityPolicy;
import com.river.agi.security.mapper.SecurityPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/security-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SecurityAdminController {
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final SecurityPolicyMapper policyMapper;

    @GetMapping("/roles") public ApiResponse<List<Role>> roles() {
        ensureDefaultPermissions();
        return ApiResponse.ok(roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getDeleted, 0)));
    }
    @PostMapping("/roles") public ApiResponse<Role> saveRole(@RequestBody Role role) { role.setTenantId(1L); role.setDeleted(0); role.setUpdatedAt(LocalDateTime.now()); if (role.getId() == null) { role.setCreatedAt(LocalDateTime.now()); roleMapper.insert(role); } else roleMapper.updateById(role); return ApiResponse.ok(role); }
    @DeleteMapping("/roles/{id}") public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        Role role = roleMapper.selectById(id);
        if (role != null) {
            role.setDeleted(1);
            role.setUpdatedAt(LocalDateTime.now());
            roleMapper.updateById(role);
            rolePermissionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RolePermission>().eq("role_id", id));
        }
        return ApiResponse.ok(null);
    }
    @GetMapping("/roles/{id}/permissions") public ApiResponse<List<Long>> rolePermissions(@PathVariable Long id) {
        return ApiResponse.ok(rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id))
                .stream().map(RolePermission::getPermissionId).collect(Collectors.toList()));
    }
    @PutMapping("/roles/{id}/permissions") @Transactional
    public ApiResponse<List<Long>> saveRolePermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        rolePermissionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RolePermission>().eq("role_id", id));
        Object raw = body == null ? null : body.get("permissionIds");
        List<Long> ids = raw instanceof List<?> list ? list.stream().map(value -> Long.valueOf(value.toString())).toList() : List.of();
        for (Long permissionId : ids) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(id);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        return ApiResponse.ok(ids);
    }
    @GetMapping("/permissions") public ApiResponse<List<Permission>> permissions() {
        ensureDefaultPermissions();
        return ApiResponse.ok(permissionMapper.selectList(new LambdaQueryWrapper<Permission>().eq(Permission::getDeleted, 0)));
    }
    @GetMapping("/policies") public ApiResponse<List<SecurityPolicy>> policies() {
        ensureDefaultPolicies();
        return ApiResponse.ok(policyMapper.selectList(new LambdaQueryWrapper<SecurityPolicy>().orderByAsc(SecurityPolicy::getPolicyType).orderByAsc(SecurityPolicy::getId)));
    }
    @PostMapping("/policies") public ApiResponse<SecurityPolicy> savePolicy(@RequestBody SecurityPolicy policy) { preparePolicy(policy); if (policy.getId() == null) { policy.setCreatedAt(LocalDateTime.now()); policyMapper.insert(policy); } else policyMapper.updateById(policy); return ApiResponse.ok(policy); }
    @DeleteMapping("/policies/{id}") public ApiResponse<Void> deletePolicy(@PathVariable Long id) { policyMapper.deleteById(id); return ApiResponse.ok(null); }

    private void ensureDefaultPolicies() {
        List<SecurityPolicy> defaults = List.of(
                defaultPolicy("日志审计默认规则", "AUDIT", "{\"retentionDays\":730,\"forceSensitiveOperationLog\":true,\"maskLogFields\":true,\"exportRoles\":[\"SUPER_ADMIN\"]}"),
                defaultPolicy("权限最小化默认规则", "ACCESS_CONTROL", "{\"leastPrivilege\":true,\"approvalRequired\":true,\"denyCrossTenantAccess\":true}"),
                defaultPolicy("敏感数据管控默认规则", "DATA_SECURITY", "{\"scanOnUpload\":true,\"maskSensitivePreview\":true,\"downloadApproval\":true,\"riskThreshold\":0.8}"),
                defaultPolicy("每日备份默认规则", "BACKUP", "{\"schedule\":\"03:00\",\"mode\":\"FULL\",\"retentionCount\":10,\"cleanupExpired\":true}"),
                defaultPolicy("双法规合规默认规则", "COMPLIANCE", "{\"laws\":[\"DATA_SECURITY_LAW\",\"PIPL\"],\"auditRequired\":true,\"reportTemplate\":\"STANDARD\"}")
        );
        for (SecurityPolicy policy : defaults) {
            Long count = policyMapper.selectCount(new LambdaQueryWrapper<SecurityPolicy>()
                    .eq(SecurityPolicy::getName, policy.getName()).eq(SecurityPolicy::getPolicyType, policy.getPolicyType()));
            if (count == 0) {
                policy.setCreatedAt(LocalDateTime.now());
                policy.setUpdatedAt(LocalDateTime.now());
                preparePolicy(policy);
                policyMapper.insert(policy);
            }
        }
    }

    /** Seed the permission catalog once so the admin UI has real assignable resources. */
    private void ensureDefaultPermissions() {
        List<PermissionSeed> seeds = List.of(
                new PermissionSeed("数据集列表", "dataset:list", "DATASET", "/api/v1/datasets", "GET"),
                new PermissionSeed("数据集上传", "dataset:upload", "DATASET", "/api/v1/datasets/upload", "POST"),
                new PermissionSeed("数据分析", "analysis:run", "ANALYSIS", "/api/v1/analysis/**", "POST"),
                new PermissionSeed("标注任务管理", "annotation:manage", "ANNOTATION", "/api/v1/annotation-tasks/**", "*"),
                new PermissionSeed("标注质量审核", "annotation:quality", "ANNOTATION", "/api/v1/annotations/**", "POST"),
                new PermissionSeed("预测任务管理", "prediction:manage", "PREDICTION", "/api/v1/predictions/**", "*"),
                new PermissionSeed("趋势分析", "trend:analyze", "TREND", "/api/v1/trend/**", "*"),
                new PermissionSeed("安全扫描", "security:scan", "SECURITY", "/api/v1/security/**", "*"),
                new PermissionSeed("审计日志", "audit:read", "AUDIT", "/api/v1/audit/**", "GET"),
                new PermissionSeed("备份恢复", "backup:manage", "BACKUP", "/api/v1/backups/**", "*"),
                new PermissionSeed("权限配置", "security-admin:manage", "SECURITY_ADMIN", "/api/v1/security-admin/**", "*")
        );
        List<Permission> persisted = new java.util.ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            PermissionSeed seed = seeds.get(i);
            Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                    .eq(Permission::getCode, seed.code));
            if (permission == null) {
                permission = new Permission();
                permission.setTenantId(1L);
                permission.setName(seed.name);
                permission.setCode(seed.code);
                permission.setResourceType(seed.resourceType);
                permission.setResourcePath(seed.path);
                permission.setSortOrder(i);
                permission.setDeleted(0);
                permission.setCreatedAt(LocalDateTime.now());
                permission.setUpdatedAt(LocalDateTime.now());
                permissionMapper.insert(permission);
            }
            persisted.add(permission);
        }

        Role admin = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (admin != null) {
            for (Permission permission : persisted) {
                ensureRolePermission(admin.getId(), permission.getId());
            }
        }
        Role user = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "USER"));
        if (user != null) {
            for (Permission permission : persisted) {
                if (permission.getCode().equals("dataset:list") || permission.getCode().equals("analysis:run")
                        || permission.getCode().equals("trend:analyze") || permission.getCode().equals("audit:read")) {
                    ensureRolePermission(user.getId(), permission.getId());
                }
            }
        }
    }

    private void ensureRolePermission(Long roleId, Long permissionId) {
        if (roleId == null || permissionId == null) return;
        Long count = rolePermissionMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RolePermission>()
                .eq("role_id", roleId).eq("permission_id", permissionId));
        if (count == 0) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
    }

    private record PermissionSeed(String name, String code, String resourceType, String path, String method) {}

    private void preparePolicy(SecurityPolicy policy) {
        policy.setTenantId(policy.getTenantId() == null ? 1L : policy.getTenantId());
        policy.setUpdatedAt(LocalDateTime.now());
        if (policy.getEnabled() == null) policy.setEnabled(true);
        policy.setLegacyEnabled(policy.getEnabled());
        if (policy.getRulesJson() == null || policy.getRulesJson().isBlank()) policy.setRulesJson("{}");
        policy.setRules(policy.getRulesJson());
        if (policy.getDescription() == null) policy.setDescription(policy.getName());
        if (policy.getPriority() == null) policy.setPriority(0);
    }

    private SecurityPolicy defaultPolicy(String name, String type, String rules) {
        SecurityPolicy policy = new SecurityPolicy();
        policy.setTenantId(1L);
        policy.setName(name);
        policy.setPolicyType(type);
        policy.setClassification("INTERNAL");
        policy.setRulesJson(rules);
        policy.setEnabled(true);
        return policy;
    }
}
