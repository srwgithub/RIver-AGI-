<template>
  <div class="audit-center">
    <header class="page-header">
      <div>
        <div class="eyebrow">DATA GOVERNANCE & SECURITY</div>
        <h1>数据管理与安全审计</h1>
        <p>平台安全底座：全量日志、分级权限、安全管控、备份恢复与合规自查。</p>
      </div>
      <div class="header-actions">
        <el-tag type="success">审计在线</el-tag>
        <el-button @click="$router.push('/security-admin')">安全管理后台</el-button>
      </div>
    </header>

    <el-row :gutter="12" class="summary-row">
      <el-col :xs="12" :sm="6" v-for="item in summaryCards" :key="item.label">
        <el-card shadow="never" class="summary-card"><strong>{{ item.value }}</strong><span>{{ item.label }}</span></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="workspace-card">
      <div class="audit-layout">
        <el-menu :default-active="activeTab" class="audit-menu" @select="selectTab">
          <el-menu-item index="logs">全量操作日志</el-menu-item>
          <el-menu-item index="permissions">权限分级管理</el-menu-item>
          <el-menu-item index="security">数据安全管控</el-menu-item>
          <el-menu-item index="backups">备份与恢复</el-menu-item>
          <el-menu-item index="compliance">合规自查台账</el-menu-item>
        </el-menu>
        <div class="tab-content"><el-tabs v-model="activeTab" @tab-change="loadTab">
        <el-tab-pane label="全量操作日志中心" name="logs">
          <div class="tab-intro">记录数据、标注、模型、看板、报表和 AI 操作，支持按模块、用户、时间和操作类型追溯。</div>
          <div class="filters">
            <el-input v-model="logFilters.userId" clearable placeholder="用户 ID" />
            <el-input v-model="logFilters.resourceType" clearable placeholder="模块/资源类型" />
            <el-input v-model="logFilters.actionType" clearable placeholder="操作类型" />
            <el-date-picker v-model="logFilters.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
            <el-button type="primary" @click="loadLogs">查询</el-button>
            <el-button @click="exportLogs">导出审计记录</el-button>
          </div>
          <el-table v-loading="loading" :data="logs" stripe empty-text="暂无审计记录" @row-click="showLogDetail">
            <el-table-column prop="id" label="ID" width="75" />
            <el-table-column prop="actionType" label="操作类型" min-width="150" />
            <el-table-column prop="resourceType" label="模块" width="125" />
            <el-table-column prop="username" label="操作人" width="110" />
            <el-table-column prop="ipAddress" label="IP 地址" width="140" />
            <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result || '-' }}</el-tag></template></el-table-column>
            <el-table-column prop="createdAt" label="操作时间" min-width="170" />
            <el-table-column prop="requestPath" label="请求路径" show-overflow-tooltip />
            <el-table-column label="追溯" width="80"><template #default="{ row }"><el-button link type="primary" @click.stop="showLogDetail(row)">详情</el-button></template></el-table-column>
          </el-table>
          <el-pagination v-model:current-page="logPage" :page-size="20" :total="logTotal" layout="total, prev, pager, next" @current-change="loadLogs" />
        </el-tab-pane>

        <el-tab-pane label="权限分级管理中心" name="permissions">
          <div class="tab-intro">按角色分配菜单、数据集、模型、导出和后台访问权限，遵循最小权限原则。</div>
          <div class="tab-actions"><el-button type="primary" @click="$router.push('/security-admin')">进入权限配置</el-button></div>
          <el-table :data="roles" stripe empty-text="暂无角色配置"><el-table-column prop="name" label="角色" /><el-table-column prop="code" label="角色编码" /><el-table-column prop="description" label="职责说明" show-overflow-tooltip /><el-table-column label="权限数量"><template #default="{ row }">{{ rolePermissionCounts[row.id] ?? '-' }}</template></el-table-column><el-table-column label="权限变更" width="100"><template #default="{ row }"><el-button link type="primary" @click="$router.push('/security-admin')">配置</el-button></template></el-table-column></el-table>
          <div class="role-note">内置角色覆盖：超级管理员、运维管理员、标注员、审核员、业务分析师、普通用户。</div>
        </el-tab-pane>

        <el-tab-pane label="数据安全管控工作台" name="security">
          <div class="tab-intro">识别敏感字段、查看脱敏样例、监控高风险数据访问，并对数据集执行安全扫描。</div>
          <div class="security-toolbar"><el-select v-model="selectedDataset" filterable clearable placeholder="选择数据集"><el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" /></el-select><el-button type="primary" :disabled="!selectedDataset" :loading="scanning" @click="runScan">发起安全扫描</el-button><el-button :disabled="!selectedDataset" @click="loadRisks">查看风险</el-button></div>
          <el-alert v-if="scanResult" :title="`最近扫描：${scanResult.status}，发现 ${scanResult.sensitiveFieldsFound || 0} 个敏感字段`" :type="scanResult.status === 'COMPLETED' ? 'success' : 'warning'" :closable="false" show-icon />
          <el-table :data="risks" stripe empty-text="暂无风险记录，请先选择数据集并扫描"><el-table-column prop="fieldName" label="字段" /><el-table-column prop="sensitiveType" label="敏感类型" /><el-table-column prop="riskLevel" label="风险等级" /><el-table-column prop="maskedSampleData" label="脱敏样例" /><el-table-column prop="suggestion" label="管控建议" show-overflow-tooltip /><el-table-column label="管控" width="120"><template #default="{ row }"><el-tag type="warning">需关注</el-tag></template></el-table-column></el-table>
        </el-tab-pane>

        <el-tab-pane label="数据备份与恢复中心" name="backups">
          <div class="tab-intro">支持手动备份、定时备份和历史版本恢复，覆盖数据集、安全记录与系统配置。</div>
          <div class="tab-actions"><el-button type="primary" :loading="backupLoading" @click="createBackup('FULL')">立即全量备份</el-button><el-button @click="createBackup('INCREMENTAL')">立即增量备份</el-button><el-button type="warning" @click="$router.push('/security-admin?tab=backup')">配置备份策略</el-button></div>
          <el-table :data="backups" stripe empty-text="暂无备份记录"><el-table-column prop="backupId" label="备份编号" /><el-table-column prop="type" label="类型" /><el-table-column prop="status" label="状态" /><el-table-column prop="sizeBytes" label="大小" /><el-table-column prop="createdAt" label="创建时间" /><el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="warning" :disabled="row.status !== 'COMPLETED'" @click="restoreBackup(row)">恢复版本</el-button></template></el-table-column></el-table>
        </el-tab-pane>

        <el-tab-pane label="合规自查与台账" name="compliance">
          <div class="tab-intro">自动汇总数据处理、用户行为、隐私保护记录，形成可导出的合规审计台账。</div>
          <el-row :gutter="12" class="compliance-grid"><el-col :xs="24" :sm="8" v-for="item in complianceItems" :key="item.title"><div class="compliance-item"><el-tag :type="item.status === '通过' ? 'success' : 'warning'">{{ item.status }}</el-tag><strong>{{ item.title }}</strong><span>{{ item.detail }}</span></div></el-col></el-row>
          <div class="tab-actions"><el-button type="primary" @click="exportCompliance">导出合规审计报告</el-button><el-button @click="$router.push('/security-admin?tab=compliance')">配置合规规则</el-button></div>
        </el-tab-pane>
        </el-tabs></div>
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="审计详情" size="460px"><el-descriptions v-if="selectedLog" :column="1" border><el-descriptions-item label="操作类型">{{ selectedLog.actionType }}</el-descriptions-item><el-descriptions-item label="模块">{{ selectedLog.resourceType }}</el-descriptions-item><el-descriptions-item label="操作人">{{ selectedLog.username }} (#{{ selectedLog.userId || '-' }})</el-descriptions-item><el-descriptions-item label="IP/设备">{{ selectedLog.ipAddress }} / {{ selectedLog.userAgent || '-' }}</el-descriptions-item><el-descriptions-item label="请求">{{ selectedLog.requestMethod }} {{ selectedLog.requestPath }}</el-descriptions-item><el-descriptions-item label="操作内容"><pre>{{ selectedLog.operationDetails || '-' }}</pre></el-descriptions-item><el-descriptions-item label="时间">{{ selectedLog.createdAt }}</el-descriptions-item></el-descriptions></el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const activeTab = ref('logs'), loading = ref(false), scanning = ref(false), backupLoading = ref(false)
const logs = ref([]), logTotal = ref(0), logPage = ref(1), roles = ref([]), rolePermissionCounts = ref({})
const datasets = ref([]), selectedDataset = ref(null), risks = ref([]), scanResult = ref(null), backups = ref([])
const selectedLog = ref(null), detailVisible = ref(false), dashboard = ref({}), compliance = ref({})
const logFilters = ref({ userId: '', resourceType: '', actionType: '', dateRange: [] })
const summaryCards = computed(() => [{ label: '审计日志', value: dashboard.value.totalAuditLogs || logTotal.value || 0 }, { label: '敏感风险', value: dashboard.value.totalRisks || 0 }, { label: '高风险项', value: dashboard.value.highRiskCount || 0 }, { label: '备份版本', value: backups.value.length }])
const complianceItems = computed(() => [{ title: '数据处理记录', status: compliance.value.auditLogs ? '通过' : '待检查', detail: `${compliance.value.auditLogs || 0} 条操作留痕` }, { title: '隐私保护记录', status: compliance.value.highRiskCount === 0 ? '通过' : '需整改', detail: `${compliance.value.highRiskCount || 0} 个高风险项` }, { title: '备份恢复机制', status: backups.value.length ? '通过' : '待配置', detail: `${backups.value.length} 个历史备份版本` }])

const loadLogs = async () => { const p = new URLSearchParams({ page: logPage.value, size: 20 }); Object.entries(logFilters.value).forEach(([key, value]) => { if (key !== 'dateRange' && value) p.append(key, value); }); if (logFilters.value.dateRange?.length === 2) { p.append('startDate', logFilters.value.dateRange[0]); p.append('endDate', logFilters.value.dateRange[1]); } const data = await request.get(`/v1/audit/logs?${p}`); logs.value = data.records || []; logTotal.value = data.total || 0 }
const selectTab = key => { activeTab.value = key; loadTab() }
const loadTab = async () => { if (activeTab.value === 'logs') await loadLogs(); if (activeTab.value === 'permissions') { roles.value = await request.get('/v1/security-admin/roles'); } if (activeTab.value === 'security') await loadRisks(); if (activeTab.value === 'backups') backups.value = await request.get('/v1/backups'); if (activeTab.value === 'compliance') compliance.value = await request.get('/v1/audit/compliance-summary') }
const loadRisks = async () => { if (!selectedDataset.value) return; risks.value = await request.get(`/v1/security/datasets/${selectedDataset.value}/risks`) || [] }
const runScan = async () => { scanning.value = true; try { scanResult.value = await request.post(`/v1/security/datasets/${selectedDataset.value}/scan`); await loadRisks(); await loadDashboard(); ElMessage.success('安全扫描完成') } catch (e) { ElMessage.error(`扫描失败：${e.message}`) } finally { scanning.value = false } }
const createBackup = async type => { backupLoading.value = true; try { await request.post(`/v1/backups/create?type=${type}`); backups.value = await request.get('/v1/backups'); ElMessage.success('备份任务已创建') } catch (e) { ElMessage.error(`备份失败：${e.message}`) } finally { backupLoading.value = false } }
const restoreBackup = async row => { try { await ElMessageBox.confirm('恢复会覆盖当前数据，确认继续？', '高风险操作', { type: 'warning' }); await request.post(`/v1/backups/${row.backupId}/restore`); ElMessage.success('恢复完成') } catch (e) { if (e !== 'cancel') ElMessage.error(`恢复失败：${e.message}`) } }
const showLogDetail = row => { selectedLog.value = row; detailVisible.value = true }
const exportLogs = async () => { const blob = await request.get('/v1/audit/logs/export', { responseType: 'blob' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'audit_logs.csv'; a.click(); URL.revokeObjectURL(url) }
const exportCompliance = async () => { const blob = await request.get('/v1/audit/compliance-report', { responseType: 'blob' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'compliance_report.json'; a.click(); URL.revokeObjectURL(url) }
const loadDashboard = async () => { dashboard.value = await request.get('/v1/security/dashboard') || {} }
onMounted(async () => { try { const data = await request.get('/v1/datasets?page=1&size=50'); datasets.value = data.records || []; backups.value = await request.get('/v1/backups') || []; await loadDashboard(); await loadLogs() } catch (e) { ElMessage.error(`审计中心加载失败：${e.message}`) } })
</script>

<style scoped>
.audit-center { padding-bottom: 24px; }
.page-header, .workspace-card { border: 1px solid var(--river-line, #dbe5ec); border-radius: 12px; background: #fff; }
.page-header { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; padding: 20px; margin-bottom: 16px; }
.eyebrow { color: var(--river-brand, #0f766e); font-size: 12px; font-weight: 700; letter-spacing: .1em; }
h1 { margin: 6px 0; color: var(--river-text, #1f2937); font-size: 24px; }
.page-header p, .tab-intro, .role-note { color: var(--river-muted, #8c98a4); font-size: 13px; }
.header-actions, .filters, .security-toolbar, .tab-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.summary-row { margin-bottom: 16px; }
.summary-card strong, .summary-card span { display: block; }
.summary-card strong { color: var(--river-brand, #0f766e); font-size: 24px; }
.summary-card span { margin-top: 6px; color: var(--river-muted, #8c98a4); font-size: 13px; }
.workspace-card { padding: 4px 10px 16px; }
.audit-layout { display: flex; gap: 18px; }
.audit-menu { width: 190px; flex: 0 0 190px; border-right: 1px solid var(--river-line, #e5e7eb); }
.tab-content { min-width: 0; flex: 1; }
.tab-intro { margin-bottom: 14px; }
.filters { margin-bottom: 14px; }
.filters .el-input { width: 150px; }.filters .el-date-editor { width: 230px; }
.el-pagination { justify-content: flex-end; margin-top: 14px; }
.security-toolbar { margin-bottom: 14px; }.security-toolbar .el-select { width: 280px; }
.tab-actions { margin: 14px 0; }.role-note { margin-top: 14px; }
.compliance-grid { margin: 18px 0; }.compliance-item { display: flex; flex-direction: column; gap: 8px; min-height: 110px; padding: 16px; border: 1px solid var(--river-line, #e5e7eb); border-radius: 8px; }.compliance-item span { color: var(--river-muted, #8c98a4); font-size: 13px; }
pre { white-space: pre-wrap; word-break: break-all; }
@media (max-width: 700px) { .page-header { flex-direction: column; }.audit-layout { display: block; }.audit-menu { width: 100%; border-right: 0; border-bottom: 1px solid var(--river-line, #e5e7eb); margin-bottom: 12px; }.filters .el-input, .filters .el-date-editor, .security-toolbar .el-select { width: 100%; } }
</style>
