package com.river.agi.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dashboard.entity.Dashboard;
import com.river.agi.dashboard.entity.DashboardWidget;
import com.river.agi.dashboard.mapper.DashboardMapper;
import com.river.agi.dashboard.mapper.DashboardWidgetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final DashboardWidgetMapper widgetMapper;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    public List<Dashboard> listDashboards(Long datasetId, String category) {
        LambdaQueryWrapper<Dashboard> wrapper = new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getDeleted, 0)
                .orderByDesc(Dashboard::getIsDefault)
                .orderByDesc(Dashboard::getUpdatedAt);
        if (datasetId != null) wrapper.eq(Dashboard::getDatasetId, datasetId);
        if (category != null) wrapper.eq(Dashboard::getCategory, category);
        wrapper.and(w -> w.eq(Dashboard::getIsPublic, 1).or().eq(Dashboard::getCreatedBy, getCurrentUserId()));
        return dashboardMapper.selectList(wrapper);
    }

    public Dashboard getDashboard(Long id) {
        Dashboard d = dashboardMapper.selectById(id);
        if (d == null || d.getDeleted() == 1) throw new BusinessException("仪表盘不存在");
        return d;
    }

    public Dashboard getDefaultDashboard() {
        return dashboardMapper.selectList(
                new LambdaQueryWrapper<Dashboard>()
                        .eq(Dashboard::getDeleted, 0)
                        .eq(Dashboard::getIsDefault, 1)
                        .last("LIMIT 1")
        ).stream().findFirst().orElse(null);
    }

    @Transactional
    public Dashboard createDashboard(Dashboard dashboard, Authentication auth) {
        dashboard.setTenantId(1L);
        dashboard.setCreatedBy(securityUtils.getCurrentUserId(auth));
        dashboard.setIsDefault(false);
        dashboard.setIsPublic(dashboard.getIsPublic() != null ? dashboard.getIsPublic() : false);
        dashboard.setDeleted(0);
        dashboard.setCreatedAt(LocalDateTime.now());
        dashboard.setUpdatedAt(LocalDateTime.now());
        if (dashboard.getLayoutJson() == null) dashboard.setLayoutJson("{}");
        if (dashboard.getFilterConfigJson() == null) dashboard.setFilterConfigJson("{}");
        dashboardMapper.insert(dashboard);
        return dashboard;
    }

    @Transactional
    public Dashboard updateDashboard(Long id, Dashboard updates, Authentication auth) {
        Dashboard existing = getDashboard(id);
        checkOwnership(existing, auth);
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getLayoutJson() != null) existing.setLayoutJson(updates.getLayoutJson());
        if (updates.getFilterConfigJson() != null) existing.setFilterConfigJson(updates.getFilterConfigJson());
        if (updates.getIsPublic() != null) existing.setIsPublic(updates.getIsPublic());
        existing.setUpdatedAt(LocalDateTime.now());
        dashboardMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void deleteDashboard(Long id, Authentication auth) {
        Dashboard d = getDashboard(id);
        checkOwnership(d, auth);
        d.setDeleted(1);
        d.setUpdatedAt(LocalDateTime.now());
        dashboardMapper.updateById(d);
        List<DashboardWidget> widgets = widgetMapper.selectList(
                new LambdaQueryWrapper<DashboardWidget>().eq(DashboardWidget::getDashboardId, id));
        for (DashboardWidget w : widgets) {
            w.setDeleted(1);
            widgetMapper.updateById(w);
        }
    }

    public List<DashboardWidget> listWidgets(Long dashboardId) {
        return widgetMapper.selectList(
                new LambdaQueryWrapper<DashboardWidget>()
                        .eq(DashboardWidget::getDashboardId, dashboardId)
                        .eq(DashboardWidget::getDeleted, 0)
                        .orderByAsc(DashboardWidget::getSortOrder)
        );
    }

    @Transactional
    public DashboardWidget addWidget(Long dashboardId, DashboardWidget widget, Authentication auth) {
        Dashboard d = getDashboard(dashboardId);
        checkOwnership(d, auth);
        widget.setDashboardId(dashboardId);
        widget.setTenantId(1L);
        widget.setDeleted(0);
        widget.setCreatedAt(LocalDateTime.now());
        widget.setUpdatedAt(LocalDateTime.now());
        if (widget.getWidth() == null) widget.setWidth(6);
        if (widget.getHeight() == null) widget.setHeight(4);
        if (widget.getSortOrder() == null) widget.setSortOrder(0);
        if (widget.getConfigJson() == null) widget.setConfigJson("{}");
        if (widget.getDataSourceJson() == null) widget.setDataSourceJson("{}");
        widgetMapper.insert(widget);
        return widget;
    }

    @Transactional
    public DashboardWidget updateWidget(Long widgetId, DashboardWidget updates, Authentication auth) {
        DashboardWidget w = widgetMapper.selectById(widgetId);
        if (w == null || w.getDeleted() == 1) throw new BusinessException("组件不存在");
        Dashboard d = getDashboard(w.getDashboardId());
        checkOwnership(d, auth);
        if (updates.getTitle() != null) w.setTitle(updates.getTitle());
        if (updates.getChartType() != null) w.setChartType(updates.getChartType());
        if (updates.getWidgetType() != null) w.setWidgetType(updates.getWidgetType());
        if (updates.getPositionX() != null) w.setPositionX(updates.getPositionX());
        if (updates.getPositionY() != null) w.setPositionY(updates.getPositionY());
        if (updates.getWidth() != null) w.setWidth(updates.getWidth());
        if (updates.getHeight() != null) w.setHeight(updates.getHeight());
        if (updates.getConfigJson() != null) w.setConfigJson(updates.getConfigJson());
        if (updates.getDataSourceJson() != null) w.setDataSourceJson(updates.getDataSourceJson());
        if (updates.getSortOrder() != null) w.setSortOrder(updates.getSortOrder());
        w.setUpdatedAt(LocalDateTime.now());
        widgetMapper.updateById(w);
        return w;
    }

    @Transactional
    public void deleteWidget(Long widgetId, Authentication auth) {
        DashboardWidget w = widgetMapper.selectById(widgetId);
        if (w == null) return;
        Dashboard d = getDashboard(w.getDashboardId());
        checkOwnership(d, auth);
        w.setDeleted(1);
        w.setUpdatedAt(LocalDateTime.now());
        widgetMapper.updateById(w);
    }

    @Transactional
    public List<DashboardWidget> bulkUpdateWidgets(Long dashboardId, List<DashboardWidget> widgets, Authentication auth) {
        Dashboard d = getDashboard(dashboardId);
        checkOwnership(d, auth);
        List<DashboardWidget> existing = listWidgets(dashboardId);
        Map<Long, DashboardWidget> existingMap = new HashMap<>();
        for (DashboardWidget w : existing) existingMap.put(w.getId(), w);

        List<DashboardWidget> result = new ArrayList<>();
        for (DashboardWidget widget : widgets) {
            if (widget.getId() != null && existingMap.containsKey(widget.getId())) {
                DashboardWidget toUpdate = existingMap.get(widget.getId());
                if (widget.getPositionX() != null) toUpdate.setPositionX(widget.getPositionX());
                if (widget.getPositionY() != null) toUpdate.setPositionY(widget.getPositionY());
                if (widget.getWidth() != null) toUpdate.setWidth(widget.getWidth());
                if (widget.getHeight() != null) toUpdate.setHeight(widget.getHeight());
                if (widget.getSortOrder() != null) toUpdate.setSortOrder(widget.getSortOrder());
                toUpdate.setUpdatedAt(LocalDateTime.now());
                widgetMapper.updateById(toUpdate);
                result.add(toUpdate);
            } else {
                widget.setId(null);
                result.add(addWidget(dashboardId, widget, auth));
            }
        }
        return result;
    }

    public Map<String, Object> getDashboardWithWidgets(Long id) {
        Dashboard d = getDashboard(id);
        List<DashboardWidget> widgets = listWidgets(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboard", d);
        result.put("widgets", widgets);
        return result;
    }

    public Map<String, Object> getAvailableWidgetTypes() {
        Map<String, Object> types = new LinkedHashMap<>();
        types.put("KPI_CARD", Map.of(
                "name", "KPI指标卡",
                "icon", "Number",
                "defaultSize", Map.of("width", 3, "height", 2),
                "dataSource", "kpi"
        ));
        types.put("TREND_CHART", Map.of(
                "name", "趋势折线图",
                "icon", "TrendCharts",
                "defaultSize", Map.of("width", 12, "height", 5),
                "dataSource", "trend"
        ));
        types.put("COMPARISON_CHART", Map.of(
                "name", "对比柱状图",
                "icon", "BarChart",
                "defaultSize", Map.of("width", 6, "height", 4),
                "dataSource", "comparison"
        ));
        types.put("PIE_CHART", Map.of(
                "name", "占比饼图",
                "icon", "PieChart",
                "defaultSize", Map.of("width", 6, "height", 4),
                "dataSource", "distribution"
        ));
        types.put("ANOMALY_TABLE", Map.of(
                "name", "异常告警表",
                "icon", "Warning",
                "defaultSize", Map.of("width", 12, "height", 4),
                "dataSource", "alerts"
        ));
        types.put("RCA_PANEL", Map.of(
                "name", "根因分析面板",
                "icon", "Search",
                "defaultSize", Map.of("width", 6, "height", 5),
                "dataSource", "rca"
        ));
        types.put("FORECAST_CHART", Map.of(
                "name", "预测分析图",
                "icon", "DataLine",
                "defaultSize", Map.of("width", 12, "height", 5),
                "dataSource", "forecast"
        ));
        types.put("DECISION_PANEL", Map.of(
                "name", "决策建议面板",
                "icon", "Lightbulb",
                "defaultSize", Map.of("width", 6, "height", 5),
                "dataSource", "decision"
        ));
        return types;
    }

    private Long getCurrentUserId() {
        try {
            Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            return securityUtils.getCurrentUserId(auth);
        } catch (Exception e) {
            return 1L;
        }
    }

    private Long getCurrentUserId(Authentication auth) {
        return securityUtils.getCurrentUserId(auth);
    }

    private void checkOwnership(Dashboard d, Authentication auth) {
        Long userId = securityUtils.getCurrentUserId(auth);
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("TENANT_ADMIN"));
        if (!isAdmin && d.getCreatedBy() != null && !d.getCreatedBy().equals(userId)) {
            throw new BusinessException("无权操作此仪表盘");
        }
    }
}
