<template>
  <div class="security-page">
    <header class="page-header">
      <div class="header-copy">
        <div class="eyebrow">SECURITY AUDIT CENTER</div>
        <h1>数据管理与安全审计</h1>
        <p>全量扫描、风险识别、脱敏建议、审计追溯和安全看板统一在此处理。</p>
      </div>
      <div class="header-actions">
        <el-tag effect="dark" type="success">审计在线</el-tag>
        <el-tag effect="dark" type="info">{{ selectedDataset ? '已选择数据集' : '待选择数据集' }}</el-tag>
        <el-button type="primary" plain @click="runScan">开始扫描</el-button>
      </div>
    </header>

    <section class="control-strip">
      <el-select v-model="selectedDataset" placeholder="选择数据集" class="dataset-select" filterable>
        <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
      </el-select>
      <el-button type="primary" @click="runScan">安全扫描</el-button>
      <el-button plain :disabled="!selectedDataset" :loading="riskLoading" @click="viewRisks">查看风险</el-button>
      <div class="dataset-hint">
        <strong>{{ currentDatasetName || '未选择数据集' }}</strong>
        <span>扫描后自动汇总风险字段、等级和建议。</span>
      </div>
    </section>

    <section class="stats-grid">
      <div class="stat-box">
        <div class="stat-label">总扫描数</div>
        <div class="stat-num">{{ dashboard.totalScans }}</div>
      </div>
      <div class="stat-box">
        <div class="stat-label">已完成扫描</div>
        <div class="stat-num">{{ dashboard.completedScans }}</div>
      </div>
      <div class="stat-box danger">
        <div class="stat-label">高风险</div>
        <div class="stat-num">{{ dashboard.highRiskCount }}</div>
      </div>
      <div class="stat-box">
        <div class="stat-label">总风险数</div>
        <div class="stat-num">{{ dashboard.totalRisks }}</div>
      </div>
    </section>

    <section class="content-grid">
      <el-card v-if="scanResult" class="panel">
        <template #header>
          <div class="panel-head">
            <span>扫描结果</span>
            <el-tag :type="scanResult.status === 'COMPLETED' ? 'success' : scanResult.status === 'FAILED' ? 'danger' : 'warning'">
              {{ scanResult.status === 'COMPLETED' ? '已完成' : scanResult.status === 'FAILED' ? '失败' : '扫描中' }}
            </el-tag>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="扫描状态">{{ scanResult.status }}</el-descriptions-item>
          <el-descriptions-item label="扫描字段数">{{ scanResult.totalFieldsScanned }}</el-descriptions-item>
          <el-descriptions-item label="敏感字段数">
            <span class="danger-text">{{ scanResult.sensitiveFieldsFound }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="scanResult.scanSummaryJson && scanResult.scanSummaryJson.sensitiveFieldsFound > 0" class="risk-summary">
          <h4>风险汇总</h4>
          <el-row :gutter="12">
            <el-col :span="8">
              <div class="summary-card danger">
                <span>高风险</span><strong>{{ scanResult.scanSummaryJson.highRiskCount }}</strong>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="summary-card warn">
                <span>中风险</span><strong>{{ scanResult.scanSummaryJson.mediumRiskCount }}</strong>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="summary-card">
                <span>低风险</span><strong>{{ scanResult.scanSummaryJson.lowRiskCount }}</strong>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-card>

      <el-card v-if="risks.length > 0" class="panel">
        <template #header>
          <div class="panel-head">
            <span>敏感数据风险 (共 {{ risks.length }} 项)</span>
            <el-tag type="danger">重点审计</el-tag>
          </div>
        </template>
        <el-table :data="risks" stripe>
          <el-table-column prop="fieldName" label="字段名" width="150" />
          <el-table-column prop="sensitiveType" label="敏感类型" width="120">
            <template #default="scope">
              <el-tag size="small">{{ scope.row.sensitiveType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="riskLevel" label="风险等级" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.riskLevel === 'HIGH' ? 'danger' : scope.row.riskLevel === 'MEDIUM' ? 'warning' : 'info'">
                {{ scope.row.riskLevel === 'HIGH' ? '高' : scope.row.riskLevel === 'MEDIUM' ? '中' : '低' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="confidence" label="置信度" width="100">
            <template #default="scope">
              {{ (scope.row.confidence * 100).toFixed(0) }}%
            </template>
          </el-table-column>
          <el-table-column prop="suggestion" label="建议" show-overflow-tooltip />
        </el-table>
      </el-card>

      <el-card v-if="scanResult && risks.length === 0" class="panel empty-panel">
        <el-empty description="未检测到敏感数据风险" />
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { getActiveDatasetId, onDatasetSync } from '../utils/workspaceSync'

const datasets = ref([])
const selectedDataset = ref('')
const scanResult = ref(null)
const risks = ref([])
const riskLoading = ref(false)
const dashboard = ref({
  totalScans: 0,
  completedScans: 0,
  highRiskCount: 0,
  totalRisks: 0
})
const currentDatasetName = computed(() => datasets.value.find(ds => ds.id === selectedDataset.value)?.name || '')

onMounted(async () => {
  try {
    const data = await request.get('/v1/datasets?page=1&size=20')
    datasets.value = data.records || []
    const activeId = getActiveDatasetId()
    const preferred = datasets.value.find(ds => String(ds.id) === String(activeId)) || datasets.value.find(ds => ds.status === 'PARSED') || datasets.value[0]
    if (preferred) {
      selectedDataset.value = Number(preferred.id)
      if (!activeId) localStorage.setItem('river_active_dataset_id', String(preferred.id))
      try {
        const existing = await request.get(`/v1/security/datasets/${preferred.id}/risks`)
        risks.value = Array.isArray(existing) ? existing : []
        if (risks.value.length) scanResult.value = { status: 'COMPLETED' }
      } catch (_) {
        // No prior scan is a real empty state; the scan action remains available.
      }
    }
  } catch (e) {
    console.error('加载数据集失败:', e)
    ElMessage.error('加载数据集失败')
  }
  
  try {
    dashboard.value = await request.get('/v1/security/dashboard')
  } catch (e) {
    console.error('加载安全看板失败:', e)
    dashboard.value = { totalScans: 0, completedScans: 0, highRiskCount: 0, totalRisks: 0 }
  }
})

const runScan = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  try {
    scanResult.value = await request.post(`/v1/security/datasets/${selectedDataset.value}/scan`)
    // The scan response already contains the latest findings; render them immediately
    // instead of requiring a second click on "查看风险".
    risks.value = (scanResult.value.scanResults || []).map((item, index) => ({
      id: item.id || `${selectedDataset.value}-${index}`,
      ...item,
      fieldName: item.fieldName || item.columnName
    }))
    try {
      dashboard.value = await request.get('/v1/security/dashboard')
    } catch (_) {
      // Keep the completed scan visible even if the aggregate dashboard refresh fails.
    }
    ElMessage.success('扫描完成')
  } catch (e) {
    ElMessage.error('扫描失败: ' + (e.message || '未知错误'))
  }
}

onDatasetSync(async datasetId => {
  if (!datasetId) return
  selectedDataset.value = Number(datasetId)
})

const viewRisks = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  riskLoading.value = true
  try {
    const data = await request.get(`/v1/security/datasets/${selectedDataset.value}/risks`)
    risks.value = data || []
    // Keep the result panel visible after a successful zero-result lookup.
    // A completed query with no findings is different from an unqueried page.
    scanResult.value = scanResult.value || { status: 'COMPLETED' }
    if (!data || data.length === 0) {
      ElMessage.info(scanResult.value?.status === 'COMPLETED'
        ? '本次扫描未发现敏感数据风险'
        : '该数据集暂无扫描风险记录，请先执行安全扫描')
    }
  } catch (e) {
    ElMessage.error('获取风险失败: ' + (e.message || '未知错误'))
  } finally { riskLoading.value = false }
}
</script>

<style scoped>
.security-page {
  min-height: calc(100vh - 60px);
  color: #1f2937;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid #dbe5ec;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.eyebrow {
  margin-bottom: 8px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  color: #18212d;
}

.page-header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.control-strip {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto auto 1.2fr;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 16px;
  border: 1px solid #dbe5ec;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.dataset-select {
  width: 100%;
}

.dataset-hint {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fbfd;
  border: 1px solid #e6edf3;
}

.dataset-hint strong {
  color: #18212d;
  font-size: 13px;
}

.dataset-hint span {
  color: #64748b;
  font-size: 11px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-box {
  text-align: center;
  padding: 18px 16px;
  border-radius: 14px;
  border: 1px solid #dbe5ec;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.stat-box.danger {
  border-color: #f4c7d0;
}

.stat-label {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 8px;
}

.stat-num {
  font-size: 28px;
  font-weight: 800;
  color: #18212d;
}

.stat-box.danger .stat-num {
  color: #fb7185;
}

.content-grid {
  display: grid;
  gap: 16px;
}

.panel {
  border-radius: 16px;
  border: 1px solid #dbe5ec;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  color: #18212d;
  font-weight: 700;
}

.danger-text {
  color: #fb7185;
  font-weight: 800;
}

.risk-summary {
  margin-top: 16px;
}

.risk-summary h4 {
  margin: 0 0 12px;
  color: #18212d;
}

.summary-card {
  padding: 12px;
  border-radius: 12px;
  background: #f8fbfd;
  border: 1px solid #e6edf3;
}

.summary-card span {
  display: block;
  color: #64748b;
  font-size: 11px;
  margin-bottom: 4px;
}

.summary-card strong {
  color: #18212d;
  font-size: 18px;
}

.summary-card.danger strong {
  color: #fb7185;
}

.summary-card.warn strong {
  color: #f59e0b;
}

.empty-panel {
  display: flex;
  justify-content: center;
}

:deep(.security-page .el-input__wrapper),
:deep(.security-page .el-select__wrapper) {
  background: #fff;
  box-shadow: none;
}

:deep(.security-page .el-input__inner),
:deep(.security-page .el-select__selected-item),
:deep(.security-page .el-select__placeholder) {
  color: #18212d;
}

:deep(.security-page .el-card__header) {
  padding: 14px 16px;
  border-bottom: 1px solid #e6edf3;
  background: #fbfdfe;
}

:deep(.security-page .el-card__body) {
  color: #334155;
}

:deep(.security-page .el-table) {
  --el-table-border-color: #e6edf3;
  --el-table-header-bg-color: #f8fbfd;
  --el-table-tr-bg-color: #fff;
  --el-table-row-hover-bg-color: #f3fbfa;
  color: #334155;
  background: transparent;
}

:deep(.security-page .el-table th.el-table__cell) {
  color: #64748b;
  font-weight: 700;
}

:deep(.security-page .el-table td.el-table__cell) {
  background: transparent;
}

:deep(.security-page .el-table .el-table__body tr:nth-child(even) td.el-table__cell) {
  background: #fbfdfe;
}

:deep(.security-page .el-table .el-table__body tr:hover > td.el-table__cell) {
  background: #eef9f7 !important;
}

@media (max-width: 1180px) {
  .control-strip {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 760px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
