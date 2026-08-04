<template>
  <div class="model-management-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">MARKET DEMAND FORECASTING / MODEL GOVERNANCE</span>
        <h1>需求预测引擎 · 模型管理后台</h1>
        <p>统一管理模型版本、生产发布、深度学习模型和 A/B 测试，预测创建页只负责提交预测任务。</p>
      </div>
      <div class="heading-actions">
        <el-button @click="$router.push('/prediction-engine')">返回预测创建</el-button>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新模型</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="status-row">
      <el-col :xs="24" :sm="8"><el-card shadow="never"><div class="status-value">{{ models.length }}</div><div class="status-label">模型版本</div></el-card></el-col>
      <el-col :xs="24" :sm="8"><el-card shadow="never"><div class="status-value">{{ productionCount }}</div><div class="status-label">生产版本</div></el-card></el-col>
      <el-col :xs="24" :sm="8"><el-card shadow="never"><el-tag :type="dlStatus.reachable ? 'success' : 'info'">{{ dlStatus.reachable ? '深度学习引擎在线' : '本地算法可用' }}</el-tag><div class="status-label">运行状态</div></el-card></el-col>
    </el-row>

    <el-card shadow="never" class="panel">
      <template #header><div class="card-heading"><span>模型版本与生产发布</span><span class="muted">发布操作会写入审计日志</span></div></template>
      <el-table v-loading="loading" :data="models" stripe empty-text="暂无模型版本，请先在预测创建页提交任务">
        <el-table-column prop="modelName" label="模型名称" min-width="170" />
        <el-table-column prop="algorithmType" label="算法" min-width="150" />
        <el-table-column prop="versionNumber" label="版本" width="90" />
        <el-table-column prop="mape" label="MAPE" width="110" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.isProduction ? 'success' : 'info'">{{ row.isProduction ? '生产版本' : (row.status || '未发布') }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="row.isProduction" @click="setProduction(row)">{{ row.isProduction ? '当前生产' : '设为生产' }}</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header><span>模型 A/B 测试</span></template>
      <div class="compare-form">
        <el-select v-model="championId" clearable filterable placeholder="选择 Champion 模型">
          <el-option v-for="model in models" :key="`c-${model.id}`" :label="modelLabel(model)" :value="model.id" />
        </el-select>
        <el-select v-model="challengerId" clearable filterable placeholder="选择 Challenger 模型">
          <el-option v-for="model in models" :key="`d-${model.id}`" :label="modelLabel(model)" :value="model.id" />
        </el-select>
        <el-button type="primary" :disabled="!championId || !challengerId || championId === challengerId" :loading="comparing" @click="compareModels">开始 A/B 测试</el-button>
      </div>
      <el-alert v-if="comparison" :title="comparison.message || 'A/B 测试完成'" :type="comparison.recommendedModelId ? 'success' : 'info'" :closable="false" show-icon />
      <el-descriptions v-if="comparison" :column="3" border class="comparison-result">
        <el-descriptions-item label="推荐模型">{{ comparison.recommendedModelId || '以后端结果为准' }}</el-descriptions-item>
        <el-descriptions-item label="Champion 指标">{{ comparison.championMetrics?.mape ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Challenger 指标">{{ comparison.challengerMetrics?.mape ?? '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header><span>深度学习模型</span></template>
        <el-table :data="normalizedDeepLearningModels" stripe empty-text="暂无深度学习模型">
          <el-table-column prop="modelName" label="模型" min-width="180" />
          <el-table-column prop="algorithm" label="算法" min-width="150" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="metricsText" label="指标" min-width="280" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const models = ref([])
const deepLearningModels = ref([])
const dlStatus = ref({ reachable: false })
const loading = ref(false)
const comparing = ref(false)
const championId = ref(null)
const challengerId = ref(null)
const comparison = ref(null)
const productionCount = computed(() => models.value.filter(model => model.isProduction).length)
const normalizedDeepLearningModels = computed(() => deepLearningModels.value.map(model => {
  const metrics = model.metrics && typeof model.metrics === 'object' ? model.metrics : {}
  const metricText = [
    ['MAE', metrics.mae],
    ['MAPE', metrics.mape],
    ['RMSE', metrics.rmse],
    ['准确率', metrics.accuracy ?? metrics.metrics?.accuracy],
    ['R²', metrics.r2]
  ]
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([label, value]) => `${label} ${formatMetric(value)}`)
    .join(' · ')

  return {
    ...model,
    modelName: model.modelName || model.modelId || '未命名模型',
    algorithm: model.algorithm || model.modelType || '未知算法',
    status: model.status || 'UNKNOWN',
    metricsText: metricText || '暂无评估指标'
  }
}))
const modelLabel = model => `${model.modelName || '未命名模型'} v${model.versionNumber || 1} (#${model.id})`
const formatMetric = value => typeof value === 'number' ? Number(value.toFixed(4)) : String(value)

const loadAll = async () => {
  loading.value = true
  try {
    const [modelData, statusData, deepData] = await Promise.all([
      request.get('/v1/predictions/models'),
      request.get('/v1/predictions/deep-learning/status'),
      request.get('/v1/predictions/deep-learning/models')
    ])
    models.value = Array.isArray(modelData) ? modelData : []
    dlStatus.value = statusData || { reachable: false }
    deepLearningModels.value = Array.isArray(deepData) ? deepData : []
  } catch (error) {
    ElMessage.error(`模型管理数据加载失败：${error.message || '后端接口不可用'}`)
  } finally {
    loading.value = false
  }
}

const compareModels = async () => {
  comparing.value = true
  try {
    comparison.value = await request.post('/v1/predictions/models/compare', { modelId1: championId.value, modelId2: challengerId.value })
    ElMessage.success('A/B 测试完成')
  } catch (error) {
    ElMessage.error(`A/B 测试失败：${error.message || '后端接口不可用'}`)
  } finally {
    comparing.value = false
  }
}

const setProduction = async model => {
  try {
    await request.post(`/v1/predictions/models/${model.id}/set-production`)
    await loadAll()
    ElMessage.success('模型已发布为生产版本')
  } catch (error) {
    ElMessage.error(`发布失败：${error.message || '后端接口不可用'}`)
  }
}

onMounted(loadAll)
</script>

<style scoped>
.model-management-page { padding-bottom: 24px; }
.page-heading, .card-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.page-heading { margin-bottom: 20px; }
.eyebrow { color: var(--river-brand, #0f766e); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
h1 { margin: 6px 0; color: var(--river-text, #1f2937); font-size: 24px; }
.page-heading p, .muted, .status-label { color: var(--river-muted, #8c98a4); font-size: 13px; }
.status-row { margin-bottom: 16px; }
.status-value { color: var(--river-brand, #0f766e); font-size: 24px; font-weight: 700; }
.status-label { margin-top: 5px; }
.panel { margin-bottom: 16px; }
.compare-form { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.compare-form .el-select { width: 260px; }
.comparison-result { margin-top: 16px; }
@media (max-width: 700px) { .page-heading { align-items: flex-start; flex-direction: column; } .compare-form .el-select { width: 100%; } }
</style>
