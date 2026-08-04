package com.river.agi.config.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.common.ApiResponse;
import com.river.agi.config.entity.SystemConfig;
import com.river.agi.config.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system-config")
@RequiredArgsConstructor
public class SystemConfigController {
    private final SystemConfigMapper mapper;

    @GetMapping("/{namespace}")
    public ApiResponse<SystemConfig> get(@PathVariable String namespace) {
        return ApiResponse.ok(mapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getTenantId, 1L)
                .eq(SystemConfig::getNamespace, namespace)
                .eq(SystemConfig::getSnapshot, false)
                .orderByDesc(SystemConfig::getVersion)
                .last("LIMIT 1")));
    }

    @GetMapping("/{namespace}/snapshots")
    public ApiResponse<List<SystemConfig>> snapshots(@PathVariable String namespace) {
        return ApiResponse.ok(mapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getTenantId, 1L)
                .eq(SystemConfig::getNamespace, namespace)
                .eq(SystemConfig::getSnapshot, true)
                .orderByDesc(SystemConfig::getVersion)));
    }

    @PutMapping("/{namespace}")
    public ApiResponse<SystemConfig> save(@PathVariable String namespace,
                                          @RequestBody String configJson,
                                          Authentication authentication) {
        SystemConfig current = mapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getTenantId, 1L)
                .eq(SystemConfig::getNamespace, namespace)
                .eq(SystemConfig::getSnapshot, false)
                .orderByDesc(SystemConfig::getVersion).last("LIMIT 1"));
        if (current == null) {
            current = new SystemConfig();
            current.setTenantId(1L);
            current.setNamespace(namespace);
            current.setVersion(1);
            current.setSnapshot(false);
            current.setCreatedAt(LocalDateTime.now());
        } else {
            current.setVersion((current.getVersion() == null ? 0 : current.getVersion()) + 1);
        }
        current.setConfigJson(configJson);
        current.setUpdatedBy(null);
        current.setUpdatedAt(LocalDateTime.now());
        if (current.getId() == null) mapper.insert(current); else mapper.updateById(current);
        return ApiResponse.ok(current);
    }

    @PostMapping("/{namespace}/snapshots")
    public ApiResponse<SystemConfig> snapshot(@PathVariable String namespace,
                                               @RequestBody String configJson) {
        SystemConfig snapshot = new SystemConfig();
        snapshot.setTenantId(1L);
        snapshot.setNamespace(namespace);
        snapshot.setConfigJson(configJson);
        snapshot.setSnapshot(true);
        snapshot.setVersion(nextVersion(namespace));
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setUpdatedAt(LocalDateTime.now());
        mapper.insert(snapshot);
        return ApiResponse.ok(snapshot);
    }

    private int nextVersion(String namespace) {
        SystemConfig latest = mapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getTenantId, 1L).eq(SystemConfig::getNamespace, namespace)
                .orderByDesc(SystemConfig::getVersion).last("LIMIT 1"));
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }
}
