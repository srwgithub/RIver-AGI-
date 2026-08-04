package com.river.agi.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dashboard.entity.Dashboard;
import com.river.agi.dashboard.entity.DashboardWidget;
import com.river.agi.dashboard.mapper.DashboardMapper;
import com.river.agi.dashboard.mapper.DashboardWidgetMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("仪表盘服务测试")
class DashboardServiceTest {

    @Mock
    private DashboardMapper dashboardMapper;
    @Mock
    private DashboardWidgetMapper widgetMapper;
    @Mock
    private SecurityUtils securityUtils;

    private DashboardService service;
    private MockedStatic<SecurityContextHolder> securityContextMockedStatic;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        mockAuth = mock(Authentication.class);
        SecurityContext mockSecurityContext = mock(SecurityContext.class);
        lenient().when(mockSecurityContext.getAuthentication()).thenReturn(mockAuth);
        securityContextMockedStatic = mockStatic(SecurityContextHolder.class);
        securityContextMockedStatic.when(SecurityContextHolder::getContext).thenReturn(mockSecurityContext);
        lenient().when(securityUtils.getCurrentUserId(any(Authentication.class))).thenReturn(1L);

        service = new DashboardService(dashboardMapper, widgetMapper, securityUtils, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (securityContextMockedStatic != null) {
            securityContextMockedStatic.close();
        }
    }

    private GrantedAuthority adminAuthority() {
        return () -> "ROLE_ADMIN";
    }

    private GrantedAuthority userAuthority() {
        return () -> "ROLE_USER";
    }

    @Test
    @DisplayName("listDashboards - 返回仪表盘列表")
    void listDashboards_success() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        when(dashboardMapper.selectList(any())).thenReturn(List.of(d));

        List<Dashboard> result = service.listDashboards(null, null);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listDashboards - 带 datasetId 和 category")
    void listDashboards_withFilters() {
        when(dashboardMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<Dashboard> result = service.listDashboards(10L, "trend");
        assertNotNull(result);
    }

    @Test
    @DisplayName("getDashboard - 找到仪表盘")
    void getDashboard_found() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        when(dashboardMapper.selectById(1L)).thenReturn(d);
        Dashboard result = service.getDashboard(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getDashboard - 不存在抛异常")
    void getDashboard_notFound() {
        when(dashboardMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getDashboard(1L));
    }

    @Test
    @DisplayName("getDashboard - 已删除抛异常")
    void getDashboard_deleted() {
        Dashboard d = new Dashboard();
        d.setDeleted(1);
        when(dashboardMapper.selectById(1L)).thenReturn(d);
        assertThrows(BusinessException.class, () -> service.getDashboard(1L));
    }

    @Test
    @DisplayName("getDefaultDashboard - 找到默认仪表盘")
    void getDefaultDashboard_found() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setIsDefault(true);
        when(dashboardMapper.selectList(any())).thenReturn(List.of(d));

        Dashboard result = service.getDefaultDashboard();
        assertNotNull(result);
    }

    @Test
    @DisplayName("getDefaultDashboard - 没有默认仪表盘")
    void getDefaultDashboard_empty() {
        when(dashboardMapper.selectList(any())).thenReturn(new ArrayList<>());
        Dashboard result = service.getDefaultDashboard();
        assertNull(result);
    }

    @Test
    @DisplayName("createDashboard - 成功创建")
    void createDashboard_success() {
        Dashboard d = new Dashboard();
        d.setName("test");
        when(dashboardMapper.insert(any())).thenAnswer(inv -> {
            Dashboard inserted = inv.getArgument(0);
            inserted.setId(1L);
            return 1;
        });

        Dashboard result = service.createDashboard(d, mockAuth);
        assertNotNull(result);
        assertEquals(1L, result.getTenantId());
        assertEquals(1L, result.getCreatedBy());
        assertFalse(result.getIsDefault());
        assertEquals(0, result.getDeleted());
    }

    @Test
    @DisplayName("createDashboard - 默认 isPublic 为 false")
    void createDashboard_defaultIsPublic() {
        Dashboard d = new Dashboard();
        d.setName("test");
        d.setIsPublic(null);
        when(dashboardMapper.insert(any())).thenAnswer(inv -> {
            Dashboard inserted = inv.getArgument(0);
            inserted.setId(1L);
            return 1;
        });

        Dashboard result = service.createDashboard(d, mockAuth);
        assertFalse(result.getIsPublic());
    }

    @Test
    @DisplayName("updateDashboard - 非所有者无权限")
    void updateDashboard_notOwner() {
        Dashboard existing = new Dashboard();
        existing.setId(1L);
        existing.setDeleted(0);
        existing.setCreatedBy(999L); // 别人创建
        when(dashboardMapper.selectById(1L)).thenReturn(existing);
        java.util.Collection<GrantedAuthority> userAuths = new java.util.ArrayList<>();
        userAuths.add(userAuthority());
        doReturn(userAuths).when(mockAuth).getAuthorities();

        assertThrows(BusinessException.class, () ->
                service.updateDashboard(1L, new Dashboard(), mockAuth));
    }

    @Test
    @DisplayName("updateDashboard - 管理员可更新他人仪表盘")
    void updateDashboard_adminCanUpdate() {
        Dashboard existing = new Dashboard();
        existing.setId(1L);
        existing.setDeleted(0);
        existing.setCreatedBy(999L);
        when(dashboardMapper.selectById(1L)).thenReturn(existing);
        java.util.Collection<GrantedAuthority> adminAuths = new java.util.ArrayList<>();
        adminAuths.add(adminAuthority());
        doReturn(adminAuths).when(mockAuth).getAuthorities();

        Dashboard updates = new Dashboard();
        updates.setName("updated name");
        when(dashboardMapper.updateById(any())).thenReturn(1);

        Dashboard result = service.updateDashboard(1L, updates, mockAuth);
        assertEquals("updated name", result.getName());
    }

    @Test
    @DisplayName("updateDashboard - 所有者更新各字段")
    void updateDashboard_ownerUpdates() {
        Dashboard existing = new Dashboard();
        existing.setId(1L);
        existing.setDeleted(0);
        existing.setCreatedBy(1L);
        when(dashboardMapper.selectById(1L)).thenReturn(existing);

        Dashboard updates = new Dashboard();
        updates.setName("new name");
        updates.setDescription("desc");
        updates.setLayoutJson("{}");
        updates.setFilterConfigJson("{}");
        updates.setIsPublic(true);
        when(dashboardMapper.updateById(any())).thenReturn(1);

        Dashboard result = service.updateDashboard(1L, updates, mockAuth);
        assertEquals("new name", result.getName());
        assertEquals("desc", result.getDescription());
        assertTrue(result.getIsPublic());
    }

    @Test
    @DisplayName("deleteDashboard - 删除同时清理 widget")
    void deleteDashboard_success() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        d.setCreatedBy(1L);
        when(dashboardMapper.selectById(1L)).thenReturn(d);

        DashboardWidget w = new DashboardWidget();
        w.setId(10L);
        w.setDashboardId(1L);
        when(widgetMapper.selectList(any())).thenReturn(List.of(w));
        when(widgetMapper.updateById(any())).thenReturn(1);
        when(dashboardMapper.updateById(any())).thenReturn(1);

        assertDoesNotThrow(() -> service.deleteDashboard(1L, mockAuth));
        verify(dashboardMapper).updateById(any());
        verify(widgetMapper).updateById(any());
    }

    @Test
    @DisplayName("listWidgets - 返回 widget 列表")
    void listWidgets_success() {
        DashboardWidget w = new DashboardWidget();
        w.setId(1L);
        when(widgetMapper.selectList(any())).thenReturn(List.of(w));
        List<DashboardWidget> result = service.listWidgets(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("addWidget - 默认值填充")
    void addWidget_defaults() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        d.setCreatedBy(1L);
        when(dashboardMapper.selectById(1L)).thenReturn(d);
        when(widgetMapper.insert(any())).thenAnswer(inv -> {
            DashboardWidget w = inv.getArgument(0);
            w.setId(1L);
            return 1;
        });

        DashboardWidget widget = new DashboardWidget();
        widget.setTitle("new widget");
        DashboardWidget result = service.addWidget(1L, widget, mockAuth);
        assertNotNull(result);
        assertEquals(1L, result.getDashboardId());
        assertEquals(6, result.getWidth());
        assertEquals(4, result.getHeight());
        assertEquals(0, result.getSortOrder());
        assertEquals("{}", result.getConfigJson());
        assertEquals("{}", result.getDataSourceJson());
    }

    @Test
    @DisplayName("addWidget - 自定义值保留")
    void addWidget_customValues() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        d.setCreatedBy(1L);
        when(dashboardMapper.selectById(1L)).thenReturn(d);
        when(widgetMapper.insert(any())).thenAnswer(inv -> {
            DashboardWidget w = inv.getArgument(0);
            w.setId(1L);
            return 1;
        });

        DashboardWidget widget = new DashboardWidget();
        widget.setTitle("custom");
        widget.setWidth(12);
        widget.setHeight(8);
        widget.setSortOrder(5);
        widget.setConfigJson("{\"key\":\"val\"}");
        widget.setDataSourceJson("{\"source\":\"x\"}");
        DashboardWidget result = service.addWidget(1L, widget, mockAuth);
        assertEquals(12, result.getWidth());
        assertEquals(8, result.getHeight());
        assertEquals(5, result.getSortOrder());
    }

    @Test
    @DisplayName("updateWidget - widget 不存在抛异常")
    void updateWidget_notFound() {
        when(widgetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.updateWidget(1L, new DashboardWidget(), mockAuth));
    }

    @Test
    @DisplayName("updateWidget - widget 已删除抛异常")
    void updateWidget_deleted() {
        DashboardWidget w = new DashboardWidget();
        w.setDeleted(1);
        when(widgetMapper.selectById(1L)).thenReturn(w);
        assertThrows(BusinessException.class, () ->
                service.updateWidget(1L, new DashboardWidget(), mockAuth));
    }

    @Test
    @DisplayName("updateWidget - 成功更新各字段")
    void updateWidget_success() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        d.setCreatedBy(1L);

        DashboardWidget w = new DashboardWidget();
        w.setId(10L);
        w.setDashboardId(1L);
        w.setDeleted(0);

        when(widgetMapper.selectById(10L)).thenReturn(w);
        when(dashboardMapper.selectById(1L)).thenReturn(d);
        when(widgetMapper.updateById(any())).thenReturn(1);

        DashboardWidget updates = new DashboardWidget();
        updates.setTitle("new title");
        updates.setChartType("BAR");
        updates.setWidgetType("KPI");
        updates.setPositionX(1);
        updates.setPositionY(2);
        updates.setWidth(8);
        updates.setHeight(6);
        updates.setConfigJson("{}");
        updates.setDataSourceJson("{}");
        updates.setSortOrder(3);

        DashboardWidget result = service.updateWidget(10L, updates, mockAuth);
        assertEquals("new title", result.getTitle());
        assertEquals("BAR", result.getChartType());
        assertEquals(8, result.getWidth());
    }

    @Test
    @DisplayName("deleteWidget - widget 不存在直接返回")
    void deleteWidget_notFound() {
        when(widgetMapper.selectById(anyLong())).thenReturn(null);
        assertDoesNotThrow(() -> service.deleteWidget(1L, mockAuth));
        verify(widgetMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("deleteWidget - 成功删除")
    void deleteWidget_success() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        d.setCreatedBy(1L);

        DashboardWidget w = new DashboardWidget();
        w.setId(10L);
        w.setDashboardId(1L);

        when(widgetMapper.selectById(10L)).thenReturn(w);
        when(dashboardMapper.selectById(1L)).thenReturn(d);
        when(widgetMapper.updateById(any())).thenReturn(1);

        assertDoesNotThrow(() -> service.deleteWidget(10L, mockAuth));
        verify(widgetMapper).updateById(any());
    }

    @Test
    @DisplayName("bulkUpdateWidgets - 更新现有 widget")
    void bulkUpdateWidgets_updateExisting() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        d.setCreatedBy(1L);
        when(dashboardMapper.selectById(1L)).thenReturn(d);

        DashboardWidget existing = new DashboardWidget();
        existing.setId(5L);
        existing.setDashboardId(1L);
        existing.setDeleted(0);
        when(widgetMapper.selectList(any())).thenReturn(List.of(existing));
        when(widgetMapper.updateById(any())).thenReturn(1);

        DashboardWidget update = new DashboardWidget();
        update.setId(5L);
        update.setWidth(10);
        update.setHeight(7);

        List<DashboardWidget> result = service.bulkUpdateWidgets(1L, List.of(update), mockAuth);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(10, result.get(0).getWidth());
    }

    @Test
    @DisplayName("bulkUpdateWidgets - 新增 widget")
    void bulkUpdateWidgets_addNew() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        d.setCreatedBy(1L);
        when(dashboardMapper.selectById(1L)).thenReturn(d);

        when(widgetMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(widgetMapper.insert(any())).thenAnswer(inv -> {
            DashboardWidget w = inv.getArgument(0);
            w.setId(100L);
            return 1;
        });

        DashboardWidget newWidget = new DashboardWidget();
        newWidget.setTitle("new");
        List<DashboardWidget> result = service.bulkUpdateWidgets(1L, List.of(newWidget), mockAuth);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getDashboardWithWidgets - 返回仪表盘和 widget")
    void getDashboardWithWidgets_success() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDeleted(0);
        when(dashboardMapper.selectById(1L)).thenReturn(d);
        when(widgetMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> result = service.getDashboardWithWidgets(1L);
        assertNotNull(result);
        assertNotNull(result.get("dashboard"));
        assertNotNull(result.get("widgets"));
    }

    @Test
    @DisplayName("getAvailableWidgetTypes - 返回所有类型")
    void getAvailableWidgetTypes_success() {
        Map<String, Object> types = service.getAvailableWidgetTypes();
        assertNotNull(types);
        assertTrue(types.containsKey("KPI_CARD"));
        assertTrue(types.containsKey("TREND_CHART"));
        assertTrue(types.containsKey("COMPARISON_CHART"));
        assertTrue(types.containsKey("PIE_CHART"));
        assertTrue(types.containsKey("ANOMALY_TABLE"));
        assertTrue(types.containsKey("RCA_PANEL"));
        assertTrue(types.containsKey("FORECAST_CHART"));
        assertTrue(types.containsKey("DECISION_PANEL"));
    }

    @Test
    @DisplayName("getCurrentUserId 异常时回退到 1L")
    void listDashboards_getCurrentUserIdFallback() {
        // 当 SecurityContextHolder 抛异常时应回退到 1L
        SecurityContext errCtx = mock(SecurityContext.class);
        when(errCtx.getAuthentication()).thenThrow(new RuntimeException("no auth"));
        securityContextMockedStatic.when(SecurityContextHolder::getContext).thenReturn(errCtx);

        when(dashboardMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertDoesNotThrow(() -> service.listDashboards(null, null));
    }
}
