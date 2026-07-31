package com.river.agi.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.auth.entity.Permission;
import com.river.agi.auth.entity.Role;
import com.river.agi.auth.mapper.PermissionMapper;
import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.common.ApiResponse;
import com.river.agi.security.entity.SecurityPolicy;
import com.river.agi.security.mapper.SecurityPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/security-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SecurityAdminController {
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final SecurityPolicyMapper policyMapper;

    @GetMapping("/roles") public ApiResponse<List<Role>> roles() { return ApiResponse.ok(roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getDeleted, 0))); }
    @PostMapping("/roles") public ApiResponse<Role> saveRole(@RequestBody Role role) { role.setTenantId(1L); role.setDeleted(0); role.setUpdatedAt(LocalDateTime.now()); if (role.getId() == null) { role.setCreatedAt(LocalDateTime.now()); roleMapper.insert(role); } else roleMapper.updateById(role); return ApiResponse.ok(role); }
    @GetMapping("/permissions") public ApiResponse<List<Permission>> permissions() { return ApiResponse.ok(permissionMapper.selectList(new LambdaQueryWrapper<Permission>().eq(Permission::getDeleted, 0))); }
    @GetMapping("/policies") public ApiResponse<List<SecurityPolicy>> policies() { return ApiResponse.ok(policyMapper.selectList(null)); }
    @PostMapping("/policies") public ApiResponse<SecurityPolicy> savePolicy(@RequestBody SecurityPolicy policy) { policy.setTenantId(1L); policy.setUpdatedAt(LocalDateTime.now()); if (policy.getEnabled() == null) policy.setEnabled(true); if (policy.getId() == null) { policy.setCreatedAt(LocalDateTime.now()); policyMapper.insert(policy); } else policyMapper.updateById(policy); return ApiResponse.ok(policy); }
    @DeleteMapping("/policies/{id}") public ApiResponse<Void> deletePolicy(@PathVariable Long id) { policyMapper.deleteById(id); return ApiResponse.ok(null); }
}
