<template>
  <div class="trend-workbench" :class="[screenMode ? 'screen-mode' : '', `screen-theme-${trendConfig.screenTheme.toLowerCase()}`, `palette-${trendConfig.palette.toLowerCase()}`]">
    <header class="trend-toolbar">
      <div>
        <span class="eyebrow">MARKET TREND ANALYSIS & VISUALIZATION</span>
        <h1>趋势分析与可视化</h1>
        <p>搭建市场趋势分析模型，提供多维度数据可视化看板，支持预测结果的趋势分析、对比分析、异常检测、根因分析，辅助决策层进行战略规划，支持自定义报表和仪表盘配置。</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="saveDashboard">保存看板</el-button>
        <el-button @click="saveTemplate">另存模板</el-button>
        <el-button :loading="reportLoading" @click="generateReport">导出报表</el-button>
        <el-button @click="toggleScreenMode">{{ screenMode ? '退出大屏' : '大屏模式' }}</el-button>
        <el-button type="primary" @click="router.push('/trend-dashboard/config')">配置后台</el-button>
      </div>
    </header>

    <section class="trend-shell">
      <aside class="builder-panel left-panel">
        <div class="panel-head">
          <strong>数据源与字段</strong>
          <el-tag type="info" effect="plain">拖拽配置</el-tag>
        </div>
        <el-select v-model="selectedDataset" placeholder="选择数据集" filterable @change="onDatasetChange">
          <el-option v-for="dataset in datasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" />
        </el-select>
        <el-select v-model="selectedPredictionTask" clearable placeholder="关联预测任务" @change="refreshTaskAnalyses">
          <el-option v-for="task in predictionTasks" :key="task.id" :label="taskOptionLabel(task)" :value="task.id">
            <div class="task-option">
              <strong>{{ taskOptionLabel(task) }}</strong>
              <small>{{ taskTypeLabel(task) }} · {{ task.modelType || 'AUTO' }} · 目标：{{ task.targetField || '未设置' }}</small>
            </div>
          </el-option>
        </el-select>
        <div v-if="selectedPredictionTaskInfo" class="selected-task-info">
          <div><span>当前任务</span><strong>{{ taskOptionLabel(selectedPredictionTaskInfo) }}</strong></div>
          <div><span>预测用途</span><strong>{{ taskPurpose(selectedPredictionTaskInfo) }}</strong></div>
          <div><span>数据集 / 目标</span><strong>{{ selectedPredictionTaskInfo.datasetId || '—' }} / {{ selectedPredictionTaskInfo.targetField || '—' }}</strong></div>
          <el-tag size="small" :type="taskStatusType(selectedPredictionTaskInfo.status)">{{ taskStatusLabel(selectedPredictionTaskInfo.status) }}</el-tag>
        </div>

        <div class="field-group">
          <div class="field-title">时间维度</div>
          <button v-for="field in dimensionFields" :key="field" class="field-chip" @click="selectXAxis(field)">{{ field }}</button>
          <el-empty v-if="!dimensionFields.length" description="选择数据集后显示字段" :image-size="48" />
        </div>
        <div class="field-group">
          <div class="field-title">产品 / 区域 / 渠道</div>
          <button v-for="field in categoryFields" :key="field" class="field-chip" @click="selectXAxis(field)">{{ field }}</button>
        </div>
        <div class="field-group">
          <div class="field-title">指标</div>
          <button v-for="field in numericFields" :key="field" class="field-chip metric" @click="selectYAxis(field)">{{ field }}</button>
        </div>

        <div class="chart-builder">
          <div class="field-title">画布组件</div>
          <button v-for="widget in widgets" :key="widget.type" class="widget-option" @click="activeWidget = widget.type">
            <el-icon><component :is="widget.icon" /></el-icon><span>{{ widget.name }}</span>
          </button>
        </div>
      </aside>

      <main class="canvas-panel">
        <div class="canvas-actions">
          <div class="canvas-selectors">
            <el-segmented v-model="chartType" :options="chartTypeOptions" @change="refreshChartMode" />
            <el-select v-model="xAxisField" placeholder="X 轴字段" :disabled="!fields.length" @change="loadDashboardData">
              <el-option v-for="field in fields" :key="field" :label="field" :value="field" />
            </el-select>
            <el-select v-model="yAxisField" placeholder="Y 轴指标" :disabled="!numericFields.length" @change="loadDashboardData">
              <el-option v-for="field in numericFields" :key="field" :label="field" :value="field" />
            </el-select>
          </div>
          <div class="canvas-buttons">
            <el-button :loading="chartLoading" @click="recommendCharts">推荐图表</el-button>
            <el-button type="primary" :loading="chartLoading" @click="generateChart">生成图表</el-button>
          </div>
        </div>

        <section class="metric-strip">
          <article v-for="item in overviewMetrics" :key="item.label" :class="['overview-card', item.tone]">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.desc }}</small>
          </article>
        </section>

        <section class="dashboard-canvas">
          <article class="chart-card main-chart">
            <div class="chart-title"><strong>真实销量与 AI 预测需求趋势</strong><el-tag effect="plain">{{ chartType }}</el-tag></div>
            <div v-if="dashboardData?.actualVsPredicted?.xAxis?.length" ref="dashboardChartRef" class="chart-box dashboard-chart-box"></div>
            <div v-else-if="chartData" ref="chartRef" class="chart-box"></div>
            <div v-else class="chart-placeholder">
              <el-icon><TrendCharts /></el-icon>
              <strong>选择数据集和字段后生成趋势图</strong>
              <span>支持趋势折线、对比柱状、异常散点和数据明细表。</span>
            </div>
          </article>

          <article class="chart-card compact-card">
            <div class="chart-title"><strong>同比 / 环比对比</strong><span>多产品 · 多区域</span></div>
            <div v-if="comparisonBars.length" class="comparison-bars">
              <div v-for="bar in comparisonBars" :key="bar.name"><span>{{ bar.name }}</span><i :style="{ width: `${bar.value}%` }"></i><b>{{ bar.value }}%</b></div>
            </div>
            <el-empty v-else description="暂无对比数据" :image-size="48" />
          </article>

          <article class="chart-card compact-card">
            <div class="chart-title"><strong>异常检测</strong><span>偏离 · 跳变 · 极值</span></div>
            <div v-if="anomalyCards.some(item => Number(item.value) > 0)" class="anomaly-list">
              <div v-for="item in anomalyCards" :key="item.label"><em :class="item.level"></em><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
            </div>
            <el-empty v-else description="暂无异常告警" :image-size="48" />
          </article>

          <article class="chart-card table-card">
            <div class="chart-title"><strong>根因分析下钻</strong><span>市场波动 / 样本问题 / 标注质量 / 模型偏差</span></div>
            <el-table v-if="rootCauses.length" :data="rootCauses" size="small" height="196">
              <el-table-column prop="cause" label="归因类型" min-width="130" />
              <el-table-column prop="weight" label="权重" width="80" />
              <el-table-column prop="evidence" label="判定依据" min-width="190" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="88">
                <template #default="scope"><el-tag size="small" :type="scope.row.type" effect="plain">{{ scope.row.status }}</el-tag></template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="完成根因分析后显示结果" :image-size="48" />
          </article>

          <article class="chart-card screen-detail-card">
            <div class="chart-title"><strong>预测情景分析</strong><span>乐观 / 基准 / 悲观</span></div>
            <div v-if="scenarioCards.length" class="scenario-grid">
              <div v-for="item in scenarioCards" :key="item.key" class="scenario-item">
                <span>{{ item.name }}</span><strong>{{ item.value }}</strong><small>{{ item.description }}</small>
              </div>
            </div>
            <el-empty v-else description="完成预测任务后显示情景分析" :image-size="52" />
          </article>

          <article class="chart-card screen-detail-card">
            <div class="chart-title"><strong>维度贡献度</strong><span>根因分析 Top 5</span></div>
            <div v-if="contributionItems.length" class="contribution-list">
              <div v-for="item in contributionItems" :key="item.name">
                <span>{{ item.name }}</span><i :style="{ width: `${Math.min(100, Math.abs(Number(item.percent || item.contributionPercent || 0)))}%` }"></i><b>{{ item.percent ?? item.contributionPercent ?? 0 }}%</b>
              </div>
            </div>
            <el-empty v-else description="暂无贡献度数据" :image-size="52" />
          </article>

          <article class="chart-card screen-detail-card decision-card">
            <div class="chart-title"><strong>决策建议</strong><span>基于当前数据自动生成</span></div>
            <div v-if="decisionItems.length" class="decision-list">
              <div v-for="item in decisionItems" :key="item.title || item.category">
                <el-tag size="small" :type="item.priority === 'HIGH' ? 'danger' : 'warning'" effect="plain">{{ item.priority || '建议' }}</el-tag>
                <div><strong>{{ item.title || item.category }}</strong><p>{{ item.description || item.message }}</p></div>
              </div>
            </div>
            <el-empty v-else description="暂无决策建议" :image-size="52" />
          </article>
        </section>

        <section v-if="recommendations.length" class="recommendation-panel">
          <div class="chart-title"><strong>AI 推荐图表</strong><span>根据当前字段结构生成</span></div>
          <button v-for="item in recommendations" :key="`${item.chartType}-${item.recommendedXField}`" @click="selectRecommendation(item)">
            <b>{{ item.chartType }}</b><span>{{ item.description || '推荐可视化组件' }}</span><small>{{ item.recommendedXField }} / {{ item.recommendedYField }}</small>
          </button>
        </section>
      </main>

      <aside class="builder-panel ai-panel">
        <div class="panel-head">
          <strong>AI 智能分析</strong>
          <el-tag type="success" effect="plain">实时解读</el-tag>
        </div>
        <div class="ai-summary">
          <span>整体趋势</span>
          <strong>{{ aiSummary.trend }}</strong>
          <p>{{ aiSummary.summary }}</p>
        </div>
        <div class="ai-actions">
          <el-button :disabled="!selectedPredictionTask" :loading="trendLoading" @click="runTrendDiagnosis">趋势诊断</el-button>
          <el-button :disabled="!selectedPredictionTask" :loading="trendLoading" @click="detectAnomalies">异常检测</el-button>
          <el-button :disabled="!selectedPredictionTask" :loading="trendLoading" @click="runWhatIf">What-If 分析</el-button>
          <el-button :disabled="!selectedPredictionTask" type="primary" plain @click="pushToEvaluation">推送模型优化</el-button>
        </div>
        <div class="insight-list">
          <div v-for="item in insights" :key="item.title" class="insight-item">
            <span :class="item.type"></span>
            <div><strong>{{ item.title }}</strong><p>{{ item.desc }}</p></div>
          </div>
        </div>
        <div v-if="trendResult" class="result-box">
          <strong>{{ trendResultTitle }}</strong>
          <pre>{{ JSON.stringify(trendResult, null, 2) }}</pre>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataLine, Grid, Histogram, List, PieChart, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../utils/request'
import { getActiveDatasetId, onDatasetSync } from '../utils/workspaceSync'

const safeData = (payload, fallback = null) => {
  if (payload == null) return fallback
  if (Array.isArray(payload)) return payload
  if (typeof payload === 'object' && payload.data !== undefined) return payload.data
  return payload
}

const router = useRouter()
const datasets = ref([])
const predictionTasks = ref([])
const selectedDataset = ref('')
const selectedPredictionTask = ref(null)
const fields = ref([])
const fieldTypes = ref({})
const chartType = ref('LINE')
const xAxisField = ref('')
const yAxisField = ref('')
const activeWidget = ref('trend')
const chartData = ref(null)
let recommendationRequestId = 0
const recommendations = ref([])
const trendResult = ref(null)
const chartLoading = ref(false)
const trendLoading = ref(false)
const reportLoading = ref(false)
const screenMode = ref(false)
const dashboardData = ref(null)
const dashboardLoading = ref(false)
const autoAnalysisLoading = ref(false)
let anomalyLoadedTask = null
const trendConfig = ref({ screenTheme: 'DARK', palette: 'BUSINESS', axisRules: ['SHOW_GRID', 'AUTO_UNIT'] })
const chartRef = ref(null)
const dashboardChartRef = ref(null)
let chartInstance = null
let dashboardChartInstance = null

const toggleScreenMode = () => {
  screenMode.value = !screenMode.value
  document.body.classList.toggle('trend-screen-active', screenMode.value)
  nextTick(() => {
    chartInstance?.resize()
    dashboardChartInstance?.resize()
  })
}

const chartTypeOptions = [
  { label: '趋势折线', value: 'LINE' },
  { label: '对比柱状', value: 'BAR' },
  { label: '异常散点', value: 'SCATTER' },
  { label: '占比饼图', value: 'PIE' }
]
const taskOptionLabel = task => task?.name || `预测任务 #${task?.id ?? '—'}`
const taskTypeLabel = task => ({
  TIME_SERIES: '时间序列预测',
  REGRESSION: '回归预测',
  CLASSIFICATION: '分类预测',
  SEQUENCE: '深度序列预测'
}[String(task?.taskType || '').toUpperCase()] || '需求预测')
const taskPurpose = task => {
  const type = taskTypeLabel(task)
  const target = task?.targetField || '目标指标'
  return `${type} · 预测${target}未来需求`
}
const taskStatusLabel = status => ({ COMPLETED: '已完成', RUNNING: '运行中', PROCESSING: '处理中', PENDING: '待运行', FAILED: '失败' }[String(status || '').toUpperCase()] || status || '未知状态')
const taskStatusType = status => ({ COMPLETED: 'success', RUNNING: 'warning', PROCESSING: 'warning', FAILED: 'danger' }[String(status || '').toUpperCase()] || 'info')
const usableTaskForDataset = datasetId => predictionTasks.value.find(task => String(task.datasetId) === String(datasetId) && ['COMPLETED', 'RUNNING', 'PROCESSING'].includes(String(task.status || '').toUpperCase()))
const widgets = [
  { name: '趋势折线', type: 'trend', icon: TrendCharts },
  { name: '对比柱状', type: 'compare', icon: Histogram },
  { name: '同比环比卡片', type: 'metric', icon: DataLine },
  { name: '异常散点图', type: 'anomaly', icon: PieChart },
  { name: '数据明细表', type: 'table', icon: List },
  { name: '自由仪表盘', type: 'dashboard', icon: Grid }
]
const chartColors = computed(() => ({
  business: ['#165DFF', '#00B42A', '#FF7D00', '#F53F3F'],
  risk: ['#F53F3F', '#FF7D00', '#165DFF', '#722ED1'],
  screen: ['#4F8CFF', '#31C48D', '#F5C451', '#F56C6C']
}[String(trendConfig.value.palette || 'BUSINESS').toLowerCase()] || ['#165DFF', '#00B42A', '#FF7D00', '#F53F3F']))
const selectedPredictionTaskInfo = computed(() => predictionTasks.value.find(task => String(task.id) === String(selectedPredictionTask.value)) || null)
const axisFormatter = value => trendConfig.value.axisRules?.includes('AUTO_UNIT') ? Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : value
const trendResultTitle = computed(() => trendResult.value?.message || '后端趋势分析已完成')
const dimensionFields = computed(() => fields.value.filter(field => /日期|时间|date|time|month|day/i.test(field)))
const categoryFields = computed(() => fields.value.filter(field => !numericFields.value.includes(field) && !dimensionFields.value.includes(field)).slice(0, 8))
const numericFields = computed(() => fields.value.filter(field => {
  const fieldType = String(fieldTypes.value[field] || '').toUpperCase()
  if (fieldType) return ['NUMERIC', 'NUMBER', 'INTEGER', 'DECIMAL', 'DOUBLE', 'FLOAT', 'LONG'].includes(fieldType)
  return /销量|价格|金额|数量|score|rate|count|amount|value|num/i.test(field)
}))
const overviewMetrics = computed(() => [
  { label: '数据记录', value: formatMetric(dashboardData.value?.kpi?.recordCount), desc: '当前数据集有效记录数', tone: 'primary' },
  { label: '指标总量', value: formatMetric(dashboardData.value?.kpi?.total), desc: '所选指标累计值', tone: 'success' },
  { label: '预测覆盖率', value: formatPercent(dashboardData.value?.actualVsPredicted?.summary?.withinBoundsRate), desc: '实际值落在预测区间内', tone: 'warning' },
  { label: '异常告警', value: formatMetric(dashboardData.value?.alertSummary?.totalAlerts), desc: '当前数据集已识别告警', tone: 'info' }
])
const aiSummary = computed(() => ({
  trend: trendResult.value ? '已完成诊断' : '等待分析',
  summary: trendResult.value ? '当前画布已收到后端诊断结果，可结合异常点、根因和建议继续下钻。' : '选择预测任务后，AI 将自动解读趋势、异常、根因和业务建议。'
}))
const insights = computed(() => [
  { title: '趋势判断', desc: selectedPredictionTask.value ? '可基于预测任务识别上涨、下降、平稳和周期性波动。' : '请先关联预测任务。', type: 'primary' },
  { title: '异常根因', desc: '异常来源按市场波动、样本问题、标注质量、模型偏差拆解。', type: 'warning' },
  { title: '决策建议', desc: '输出产能、库存、渠道和模型迭代建议。', type: 'success' }
])
const comparisonBars = computed(() => {
  const summary = dashboardData.value?.actualVsPredicted?.summary
  const bars = dashboardData.value?.actualVsPredicted?.deviations || []
  if (bars.length) return bars.slice(-5).map(item => ({ name: item.date, value: Math.max(0, Math.min(100, Math.round(Math.abs(Number(item.deviationPercent || 0)) * 10) / 10)) }))
  if (summary) return [{ name: '区间内', value: Number(summary.withinBoundsRate || 0) }]
  return trendResult.value?.comparisonBars || trendResult.value?.comparison || []
})
const anomalyCards = computed(() => {
  const summary = dashboardData.value?.alertSummary || {}
  const byType = summary.byType || {}
  const alerts = trendResult.value?.alerts || []
  return ['预测偏离', '数据跳变', '极值异常'].map((label, index) => ({
    label,
    value: Object.entries(byType).filter(([key]) => String(key).includes(['DEVIATION', 'JUMP', 'EXTREME'][index])).reduce((sum, [, value]) => sum + Number(value || 0), 0) || alerts.filter(item => String(item.type || item.category || '').includes(['DEVIATION', 'JUMP', 'EXTREME'][index])).length,
    level: ['danger', 'warning', 'primary'][index]
  }))
})
const rootCauses = computed(() => {
  const contribution = dashboardData.value?.contribution
  if (Array.isArray(contribution)) return contribution.flatMap(group => {
    if (Array.isArray(group.items)) return group.items.slice(0, 5).map(item => ({ cause: `${group.dimension || '维度'} / ${item.name || '—'}`, weight: `${item.percent ?? 0}%`, evidence: '后端维度贡献度分析结果', status: '已分析', type: 'success' }))
    return [{ cause: group.dimension || group.name || group.value, weight: `${group.contributionPercent ?? group.percent ?? 0}%`, evidence: group.evidence || '后端贡献度分析结果', status: '已分析', type: 'success' }]
  }).slice(0, 5)
  return trendResult.value?.rootCauses || trendResult.value?.causes || []
})
const scenarioCards = computed(() => {
  const scenarios = dashboardData.value?.scenarios || {}
  return Object.entries(scenarios).filter(([key, value]) => ['optimistic', 'neutral', 'pessimistic'].includes(key) && value).map(([key, value]) => ({
    key, name: value.name || ({ optimistic: '乐观情景', neutral: '基准情景', pessimistic: '悲观情景' }[key]),
    value: formatMetric(value.total ?? value.forecastTotal ?? value.average), description: value.description || value.assumption || '后端情景预测结果'
  }))
})
const contributionItems = computed(() => {
  const direct = Array.isArray(dashboardData.value?.contribution) ? dashboardData.value.contribution : []
  const fallback = trendResult.value?.rootCauses || trendResult.value?.causes || []
  return (direct.length ? direct.flatMap(group => Array.isArray(group.items) ? group.items.map(item => ({ ...item, dimension: group.dimension })) : [group]) : fallback).slice(0, 5).map(item => ({ name: item.dimension ? `${item.dimension} / ${item.value || item.name || ''}` : item.name || item.cause, percent: item.contributionPercent ?? item.percent ?? (Number(String(item.weight || '').replace('%', '')) || 0) }))
})
const decisionItems = computed(() => {
  const data = dashboardData.value?.recommendations
  if (Array.isArray(data)) return data
  return data?.recommendations || []
})
const formatMetric = value => value === undefined || value === null || value === '' ? '--' : Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : String(value)
const formatPercent = value => value === undefined || value === null ? '--' : `${Number(value).toFixed(1)}%`

const selectXAxis = async field => { xAxisField.value = field; await loadDashboardData() }
const selectYAxis = async field => { yAxisField.value = field; await loadDashboardData() }
const selectTaskForDataset = datasetId => {
  const candidates = predictionTasks.value.filter(task => String(task.datasetId) === String(datasetId))
  const usable = candidates.find(task => ['COMPLETED', 'RUNNING', 'PROCESSING'].includes(String(task.status || '').toUpperCase()))
  selectedPredictionTask.value = (usable || candidates[0])?.id || null
}

const loadTrendConfig = async () => {
  try {
    const saved = await request.get('/v1/system-config/trend-dashboard')
    if (saved?.configJson) trendConfig.value = { ...trendConfig.value, ...JSON.parse(saved.configJson) }
  } catch (error) {
    const local = localStorage.getItem('river-trend-config')
    if (local) {
      try { trendConfig.value = { ...trendConfig.value, ...JSON.parse(local) } } catch (_) { /* ignore malformed local config */ }
    }
  }
}

const loadDashboardData = async () => {
  if (!selectedDataset.value) {
    dashboardData.value = null
    return
  }
  dashboardLoading.value = true
  try {
    const params = new URLSearchParams({ datasetId: String(selectedDataset.value) })
    if (selectedPredictionTask.value) params.set('predictionTaskId', String(selectedPredictionTask.value))
    if (yAxisField.value) params.set('measure', yAxisField.value)
    if (xAxisField.value) params.set('timeField', xAxisField.value)
    dashboardData.value = await request.get(`/v1/dashboards/trend-data?${params.toString()}`)
    await nextTick()
    renderDashboardChart()
  } catch (error) {
    dashboardData.value = null
    ElMessage.error(`大屏数据加载失败：${error.message || '后端接口不可用'}`)
  } finally { dashboardLoading.value = false }
}

const ensureTaskDashboardData = async () => {
  if (!selectedPredictionTask.value || autoAnalysisLoading.value) return
  const needsDiagnosis = !dashboardData.value?.trendDiagnosis
  const needsScenarios = !dashboardData.value?.scenarios
  const needsAnomaly = anomalyLoadedTask !== selectedPredictionTask.value
  if (!needsDiagnosis && !needsScenarios && !needsAnomaly) return
  autoAnalysisLoading.value = true
  try {
    if (needsDiagnosis) await request.post(`/v1/trend/diagnosis/${selectedPredictionTask.value}`)
    if (needsScenarios) await request.post('/v1/trend/decision/what-if', {
      predictionTaskId: selectedPredictionTask.value,
      scenarioName: '趋势分析工作台默认情景',
      adjustedFactors: {},
      assumptions: '基于当前预测任务基线自动生成三种情景'
    })
    if (needsAnomaly) {
      const alerts = await request.post(`/v1/trend/anomaly/detect-deviations/${selectedPredictionTask.value}`)
      anomalyLoadedTask = selectedPredictionTask.value
      const firstAlert = Array.isArray(alerts) ? alerts[0] : null
      if (firstAlert?.id) {
        try {
          const rca = await request.post(`/v1/trend/rca/${firstAlert.id}`)
          const contributors = rca?.topContributorsJson ? JSON.parse(rca.topContributorsJson) : []
          trendResult.value = { ...(trendResult.value || {}), alerts, rootCauses: contributors.map(item => ({ cause: `${item.dimension || '维度'} / ${item.value || '—'}`, weight: `${item.contributionPercent ?? 0}%`, contributionPercent: item.contributionPercent ?? 0, evidence: '后端根因分析结果', status: '已分析', type: 'success' })) }
        } catch (_) {
          trendResult.value = { ...(trendResult.value || {}), alerts }
        }
      } else {
        trendResult.value = { ...(trendResult.value || {}), alerts: [] }
      }
    }
    await loadDashboardData()
  } catch (error) {
    // The page still shows the real KPI and prediction data when optional
    // derived analysis cannot be generated.
    ElMessage.warning(`部分趋势分析暂不可用：${error.message || '请稍后重试'}`)
  } finally { autoAnalysisLoading.value = false }
}

const refreshTaskAnalyses = async () => {
  await loadDashboardData()
  await ensureTaskDashboardData()
}

const onDatasetChange = async () => {
  if (!selectedDataset.value) return
  try {
    if (!predictionTasks.value.some(task => String(task.id) === String(selectedPredictionTask.value) && String(task.datasetId) === String(selectedDataset.value))) {
      selectTaskForDataset(selectedDataset.value)
    }
    const data = await request.get(`/v1/datasets/${selectedDataset.value}/fields`)
    const rawFields = safeData(data, [])
    fieldTypes.value = {}
    fields.value = rawFields.map(field => {
      if (typeof field === 'string') return field
      const fieldName = field.fieldName || field.name
      if (fieldName && field.fieldType) fieldTypes.value[fieldName] = field.fieldType
      return fieldName
    }).filter(Boolean)
    xAxisField.value = dimensionFields.value[0] || fields.value[0] || ''
    yAxisField.value = numericFields.value[0] || ''
    await loadDashboardData()
    await ensureTaskDashboardData()
  } catch (error) {
    ElMessage.error(`字段加载失败：${error.message || '未知错误'}`)
  }
}

const recommendCharts = async () => {
  if (!selectedDataset.value) return ElMessage.warning('请选择数据集')
  const selected = datasets.value.find(ds => String(ds.id) === String(selectedDataset.value))
  if (selected && selected.status !== 'PARSED') return ElMessage.info('数据集正在解析，请解析完成后再生成图表')
  const requestId = ++recommendationRequestId
  chartLoading.value = true
  try {
    const data = await request.post(`/v1/charts/recommend?datasetId=${selectedDataset.value}`)
    recommendations.value = safeData(data, [])
    ElMessage.success('图表推荐已生成')
  } catch (error) {
    if (requestId !== recommendationRequestId) return
    ElMessage.error(`图表推荐失败：${error.message || '未知错误'}`)
  } finally { chartLoading.value = false }
}

const selectRecommendation = item => {
  chartType.value = item.chartType || chartType.value
  xAxisField.value = item.recommendedXField || xAxisField.value
  yAxisField.value = item.recommendedYField || yAxisField.value
  generateChart()
}

const generateChart = async () => {
  if (!selectedDataset.value) return ElMessage.warning('请选择数据集')
  if (!xAxisField.value || !yAxisField.value) return ElMessage.warning('请选择维度和指标')
  chartLoading.value = true
  try {
    const data = await request.post(`/v1/charts/generate?datasetId=${selectedDataset.value}&chartType=${chartType.value}&xAxisField=${xAxisField.value}&yAxisField=${yAxisField.value}`)
    chartData.value = safeData(data)
    await nextTick()
    renderChart()
    ElMessage.success('看板图表已生成')
  } catch (error) {
    chartData.value = null
    ElMessage.error(`生成图表失败：${error.message || '未知错误'}`)
  } finally { chartLoading.value = false }
}

const renderChart = () => {
  if (!chartRef.value || !chartData.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  // The backend returns the same x/y aggregates for all chart modes. Keep the
  // user's selected mode as the source of truth and adapt the series shape per
  // ECharts type; otherwise BAR/SCATTER/PIE fall back to the line-like data
  // structure and appear identical in the canvas.
  const requestedType = String(chartType.value || chartData.value.chartType || 'LINE').toUpperCase()
  const seriesType = ['LINE', 'BAR', 'SCATTER', 'PIE'].includes(requestedType) ? requestedType.toLowerCase() : 'line'
  const xData = chartData.value.xData || chartData.value.xAxisData || []
  const yData = chartData.value.yData || chartData.value.yAxisData || []
  const seriesData = seriesType === 'pie'
    ? xData.map((name, index) => ({ name, value: Number(yData[index] ?? 0) }))
    : seriesType === 'scatter'
      ? yData.map((value, index) => [index, Number(value ?? 0)])
      : yData
  const showGrid = trendConfig.value.axisRules?.includes('SHOW_GRID') !== false
  chartInstance.setOption({
    color: chartColors.value,
    tooltip: { trigger: seriesType === 'pie' ? 'item' : 'axis' },
    grid: { left: 42, right: 24, top: 36, bottom: 32 },
    xAxis: seriesType === 'pie' ? undefined : seriesType === 'scatter'
      ? { type: 'category', data: xData, axisLine: { lineStyle: { color: '#E5E6EB' } } }
      : { type: 'category', data: xData, axisLine: { lineStyle: { color: '#E5E6EB' } } },
    yAxis: seriesType === 'pie' ? undefined : { type: 'value', splitLine: { show: showGrid, lineStyle: { color: '#F2F3F5' } }, axisLabel: { formatter: axisFormatter } },
    series: [{ type: seriesType, smooth: seriesType === 'line', symbolSize: seriesType === 'scatter' ? 10 : undefined, data: seriesData }]
  })
}

const renderDashboardChart = () => {
  const comparison = dashboardData.value?.actualVsPredicted
  if (!dashboardChartRef.value || !comparison?.xAxis?.length) return
  if (dashboardChartInstance) dashboardChartInstance.dispose()
  dashboardChartInstance = echarts.init(dashboardChartRef.value)
  const dark = trendConfig.value.screenTheme !== 'LIGHT'
  const showGrid = trendConfig.value.axisRules?.includes('SHOW_GRID') !== false
  const requestedType = String(chartType.value || 'LINE').toUpperCase()
  const seriesType = ['LINE', 'BAR', 'SCATTER', 'PIE'].includes(requestedType) ? requestedType.toLowerCase() : 'line'
  const actual = (comparison.actual || []).map(value => Number(value ?? 0))
  const predicted = (comparison.predicted || []).map(value => Number(value ?? 0))
  const pieData = comparison.xAxis.map((name, index) => ({ name, value: Math.abs(actual[index] ?? predicted[index] ?? 0) }))
  const series = seriesType === 'pie'
    ? [{ name: '实际值占比', type: 'pie', radius: ['38%', '68%'], center: ['50%', '52%'], data: pieData, label: { color: dark ? '#c7d6e8' : '#4e5969', formatter: '{b}: {d}%' } }]
    : seriesType === 'scatter'
      ? [{ name: '实际值', type: 'scatter', symbolSize: 10, data: actual.map((value, index) => [index, value]) }, { name: '预测值', type: 'scatter', symbolSize: 10, data: predicted.map((value, index) => [index, value]) }]
      : [{ name: '实际值', type: seriesType, smooth: seriesType === 'line', data: actual }, { name: '预测值', type: seriesType, smooth: seriesType === 'line', data: predicted }]
  dashboardChartInstance.setOption({
    color: chartColors.value, backgroundColor: dark ? '#102238' : '#ffffff', tooltip: { trigger: seriesType === 'pie' ? 'item' : 'axis' }, legend: { textStyle: { color: dark ? '#c7d6e8' : '#4e5969' } },
    grid: seriesType === 'pie' ? undefined : { left: 42, right: 20, top: 32, bottom: 32 }, xAxis: seriesType === 'pie' ? undefined : { type: seriesType === 'scatter' ? 'value' : 'category', data: seriesType === 'scatter' ? undefined : comparison.xAxis, axisLabel: { color: '#9bb0c8' } },
    yAxis: seriesType === 'pie' ? undefined : { type: 'value', axisLabel: { color: dark ? '#9bb0c8' : '#4e5969', formatter: axisFormatter }, splitLine: { show: showGrid, lineStyle: { color: dark ? '#24425f' : '#e5e6eb' } } },
    series
  })
}

const refreshChartMode = async () => {
  await nextTick()
  if (dashboardData.value?.actualVsPredicted?.xAxis?.length) renderDashboardChart()
  else if (chartData.value) renderChart()
}

const runTrendDiagnosis = async () => {
  if (!selectedPredictionTask.value) return
  trendLoading.value = true
  try {
    trendResult.value = await request.post(`/v1/trend/diagnosis/${selectedPredictionTask.value}`)
    await loadDashboardData()
    ElMessage.success('趋势诊断完成')
  } catch (error) {
    ElMessage.error(`趋势诊断失败：${error.message || '后端接口不可用'}`)
  } finally { trendLoading.value = false }
}

const detectAnomalies = async () => {
  if (!selectedPredictionTask.value) return
  trendLoading.value = true
  try {
    const alerts = await request.post(`/v1/trend/anomaly/detect-deviations/${selectedPredictionTask.value}`)
    trendResult.value = { message: `异常检测完成，共发现 ${Array.isArray(alerts) ? alerts.length : 0} 条告警`, alerts }
    await loadDashboardData()
    ElMessage.success('异常检测完成')
  } catch (error) {
    ElMessage.error(`异常检测失败：${error.message || '后端接口不可用'}`)
  } finally { trendLoading.value = false }
}

const runWhatIf = async () => {
  if (!selectedPredictionTask.value) return
  trendLoading.value = true
  try {
    trendResult.value = await request.post('/v1/trend/decision/what-if', {
      predictionTaskId: selectedPredictionTask.value,
      scenarioName: '趋势分析工作台情景',
      adjustedFactors: {},
      assumptions: '使用当前任务基线生成后端情景结果'
    })
    await loadDashboardData()
    ElMessage.success('What-If 情景分析完成')
  } catch (error) {
    ElMessage.error(`情景分析失败：${error.message || '后端接口不可用'}`)
  } finally { trendLoading.value = false }
}

const generateReport = async () => {
  if (!selectedDataset.value) return ElMessage.warning('请选择数据集')
  reportLoading.value = true
  try {
    await request.post(`/v1/charts/reports?datasetId=${selectedDataset.value}&reportType=FULL`)
    ElMessage.success('报表已生成，可在图表中心查看')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '报表生成失败')
  } finally { reportLoading.value = false }
}

const saveDashboard = async () => {
  if (!selectedDataset.value) return ElMessage.warning('请选择数据集后保存看板')
  try {
    await request.post('/v1/dashboards', { name: '趋势分析工作台', description: '趋势、对比、异常检测与根因分析看板', datasetId: selectedDataset.value, category: 'TREND', layoutJson: JSON.stringify({ widgets: widgets.map(item => item.type) }), isDefault: false, isPublic: false })
    ElMessage.success('看板已保存到后台')
  } catch (error) { ElMessage.error(error.message || '看板保存失败') }
}
const saveTemplate = async () => {
  if (!selectedDataset.value) return ElMessage.warning('请选择数据集后保存模板')
  try {
    await request.post('/v1/dashboards/reports/templates', { name: '趋势分析工作台模板', description: '趋势、对比、异常检测与根因分析报表模板', datasetId: selectedDataset.value, reportType: 'TREND', sectionsJson: JSON.stringify({ widgets: widgets.map(item => item.type), chartType: chartType.value, xAxisField: xAxisField.value, yAxisField: yAxisField.value }) })
    ElMessage.success('报表模板已保存到后台')
  } catch (error) { ElMessage.error(error.message || '模板保存失败') }
}
const pushToEvaluation = () => router.push('/prediction-evaluation')

onDatasetSync(async datasetId => {
  if (!datasetId) return
  selectedDataset.value = Number(datasetId)
  await onDatasetChange()
})

onMounted(async () => {
  try {
    await loadTrendConfig()
    const [datasetData, taskData] = await Promise.all([
      request.get('/v1/datasets?page=1&size=50'),
      request.get('/v1/predictions?page=1&size=50')
    ])
    datasets.value = datasetData.records || datasetData.data?.records || []
    predictionTasks.value = taskData.records || taskData.data?.records || []
    const activeId = getActiveDatasetId()
    const activeDataset = activeId && datasets.value.find(dataset => String(dataset.id) === String(activeId))
    const taskDataset = datasets.value.find(dataset => usableTaskForDataset(dataset.id))
    // A trend dashboard without a completed prediction task can only show raw
    // KPIs. Prefer a dataset with a usable task on first entry so the forecast,
    // anomaly, scenario and root-cause panels have their real business context.
    const initialDataset = activeDataset && usableTaskForDataset(activeDataset.id)
      ? activeDataset
      : taskDataset || activeDataset || datasets.value[0]
    if (initialDataset) {
      selectedDataset.value = initialDataset.id
      selectTaskForDataset(initialDataset.id)
      await onDatasetChange()
    }
  } catch (error) {
    ElMessage.error(`趋势分析数据加载失败：${error.message || '未知错误'}`)
  }
})

onUnmounted(() => {
  document.body.classList.remove('trend-screen-active')
  if (chartInstance) chartInstance.dispose()
  if (dashboardChartInstance) dashboardChartInstance.dispose()
})
</script>

<style scoped>
.trend-workbench { min-height: calc(100vh - 60px); padding: 24px; max-width: 100%; }
.trend-workbench.screen-mode {
  position: fixed;
  inset: 0;
  z-index: 3000;
  box-sizing: border-box;
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  padding: 24px;
  overflow: auto;
  background: #071827;
}
.trend-workbench.screen-mode .trend-shell {
  grid-template-columns: minmax(0, 1fr) 320px;
  min-height: 0;
}
.trend-workbench.screen-mode .left-panel { display: none; }
.trend-workbench.screen-mode .canvas-panel { min-width: 0; }
.trend-workbench.screen-mode .ai-panel { position: sticky; top: 0; }
:global(body.trend-screen-active) { overflow: hidden; }
:global(body.trend-screen-active .sidebar-pro),
:global(body.trend-screen-active .top-header) { display: none !important; }
:global(body.trend-screen-active .main-content) {
  width: 100vw !important;
  height: 100vh !important;
  margin-left: 0 !important;
  padding: 0 !important;
  overflow: hidden !important;
}
.trend-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; padding: 20px; background: #fff; border: 1px solid var(--border-1); border-radius: 8px; box-shadow: var(--shadow-card); }
.task-option { display: grid; gap: 3px; line-height: 1.3; }
.task-option strong { color: var(--text-1); font-size: 13px; }
.task-option small { color: var(--text-3); font-size: 11px; }
.selected-task-info { display: grid; gap: 7px; margin-top: 10px; padding: 10px 12px; background: #f7f8fa; border: 1px solid var(--border-1); border-radius: 6px; }
.selected-task-info > div { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; }
.selected-task-info span { color: var(--text-3); flex-shrink: 0; }
.selected-task-info strong { color: var(--text-1); text-align: right; font-weight: 500; }
.eyebrow { color: #0f8b79; font-size: 12px; font-weight: 700; letter-spacing: 0; }
.trend-toolbar h1 { margin: 7px 0; color: var(--text-1); font-size: 24px; line-height: 1.2; font-weight: 600; }
.trend-toolbar p { max-width: 940px; margin: 0; color: var(--text-3); font-size: 14px; line-height: 1.5; }
.toolbar-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; min-width: 430px; }
.toolbar-actions .el-button { margin-left: 0; }
.trend-shell { display: grid; grid-template-columns: 260px minmax(0, 1fr) 320px; gap: 16px; align-items: stretch; }
.builder-panel, .canvas-panel { background: #fff; border: 1px solid var(--border-1); border-radius: 8px; box-shadow: var(--shadow-card); }
.builder-panel { padding: 16px; min-height: calc(100vh - 148px); }
.left-panel, .ai-panel { position: sticky; top: 0; align-self: start; }
.panel-head, .canvas-actions, .chart-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.panel-head { margin-bottom: 14px; }
.panel-head strong, .chart-title strong { color: var(--text-1); font-size: 16px; font-weight: 600; }
.left-panel .el-select { width: 100%; margin-bottom: 10px; }
.field-group, .chart-builder { padding-top: 14px; margin-top: 14px; border-top: 1px solid var(--border-1); }
.field-title { margin-bottom: 10px; color: var(--text-3); font-size: 12px; }
.field-chip, .widget-option { width: 100%; display: flex; align-items: center; gap: 8px; min-height: 32px; margin-bottom: 8px; padding: 0 10px; border: 1px solid var(--border-1); border-radius: 6px; background: #fff; color: var(--text-2); text-align: left; cursor: pointer; }
.field-chip:hover, .widget-option:hover { border-color: var(--primary); background: var(--primary-light); color: var(--primary); }
.field-chip.metric { border-color: #d7f0dd; background: #f7fffa; color: #168c3a; }
.canvas-panel { padding: 16px; min-width: 0; }
.canvas-actions { margin-bottom: 16px; }
.canvas-selectors { display: flex; flex-wrap: wrap; gap: 10px; min-width: 0; }
.canvas-selectors .el-select { width: 168px; }
.canvas-buttons { display: flex; gap: 8px; }
.metric-strip { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.overview-card { padding: 16px; border: 1px solid var(--border-1); border-radius: 8px; background: #fff; }
.overview-card span, .overview-card small { display: block; color: var(--text-3); font-size: 12px; }
.overview-card strong { display: block; margin: 8px 0 4px; color: var(--text-1); font-size: 22px; line-height: 1.2; }
.overview-card.primary { border-left: 3px solid var(--primary); }.overview-card.success { border-left: 3px solid var(--success); }.overview-card.warning { border-left: 3px solid var(--warning); }.overview-card.info { border-left: 3px solid var(--text-3); }
.dashboard-canvas { display: grid; grid-template-columns: 1.15fr .85fr; gap: 16px; }
.chart-card { min-width: 0; padding: 16px; border: 1px solid var(--border-1); border-radius: 8px; background: #fff; }
.main-chart { grid-row: span 2; min-height: 484px; }
.chart-title { margin-bottom: 14px; }
.chart-title span { color: var(--text-3); font-size: 12px; }
.chart-box { height: 420px; }
.dashboard-chart-box { background: #102238; border-radius: 6px; }
.chart-placeholder { height: 420px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; border: 1px dashed var(--border-3); border-radius: 8px; background: #fafbfc; color: var(--text-3); text-align: center; }
.chart-placeholder .el-icon { color: var(--primary); font-size: 36px; }
.chart-placeholder strong { color: var(--text-1); font-size: 16px; }
.comparison-bars { display: grid; gap: 12px; padding-top: 10px; }
.comparison-bars div { display: grid; grid-template-columns: 76px 1fr 42px; align-items: center; gap: 10px; color: var(--text-2); font-size: 12px; }
.comparison-bars i { height: 8px; border-radius: 999px; background: linear-gradient(90deg, #165DFF, #00B42A); }
.comparison-bars b { color: var(--text-1); font-weight: 600; }
.anomaly-list { display: grid; gap: 12px; }
.anomaly-list div { display: grid; grid-template-columns: 8px 1fr auto; align-items: center; gap: 10px; padding: 12px; border-radius: 6px; background: #f7f8fa; }
.anomaly-list em { width: 8px; height: 8px; border-radius: 50%; }.anomaly-list em.danger { background: var(--error); }.anomaly-list em.warning { background: var(--warning); }.anomaly-list em.primary { background: var(--primary); }
.table-card { grid-column: 2; }
.recommendation-panel { display: flex; gap: 10px; margin-top: 16px; padding: 14px; border: 1px solid var(--border-1); border-radius: 8px; background: #fff; overflow-x: auto; }
.recommendation-panel .chart-title { min-width: 150px; margin-bottom: 0; align-items: flex-start; flex-direction: column; }
.recommendation-panel button { min-width: 220px; padding: 12px; border: 1px solid var(--border-1); border-radius: 6px; background: #fff; text-align: left; cursor: pointer; }
.recommendation-panel button:hover { border-color: var(--primary); background: var(--primary-light); }.recommendation-panel b, .recommendation-panel span, .recommendation-panel small { display: block; }.recommendation-panel span { margin: 6px 0; color: var(--text-2); font-size: 12px; }.recommendation-panel small { color: var(--text-3); }
.ai-summary { padding: 14px; margin-bottom: 14px; border-radius: 8px; background: var(--primary-light); }.ai-summary span { color: var(--primary); font-size: 12px; }.ai-summary strong { display: block; margin: 6px 0; color: var(--text-1); font-size: 20px; }.ai-summary p { margin: 0; color: var(--text-2); font-size: 13px; line-height: 1.5; }
.ai-actions { display: grid; gap: 8px; margin-bottom: 16px; }.ai-actions .el-button { margin-left: 0; }
.insight-list { display: grid; gap: 12px; }.insight-item { display: grid; grid-template-columns: 8px 1fr; gap: 10px; }.insight-item > span { width: 8px; height: 8px; margin-top: 5px; border-radius: 50%; }.insight-item .primary { background: var(--primary); }.insight-item .warning { background: var(--warning); }.insight-item .success { background: var(--success); }.insight-item strong { color: var(--text-1); font-size: 13px; }.insight-item p { margin: 4px 0 0; color: var(--text-3); font-size: 12px; line-height: 1.5; }
.result-box { margin-top: 16px; padding: 12px; border: 1px solid var(--border-1); border-radius: 8px; background: #f7f8fa; }.result-box strong { font-size: 13px; }.result-box pre { max-height: 220px; margin: 8px 0 0; overflow: auto; color: var(--text-2); font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; }
.screen-mode .chart-placeholder { height: 300px; }
.screen-mode .chart-placeholder strong { color: #f4f8fc; }
.screen-mode .chart-placeholder span { color: #a9bdd2; }
.screen-mode .chart-placeholder .el-icon { color: #5f98ff; }
.screen-mode .compact-card { min-height: 168px; }
.screen-mode .screen-detail-card { min-height: 184px; }
.screen-mode .el-empty__description p { color: #a9bdd2; }
.screen-mode.screen-theme-light { background: #f2f6fb; color: var(--text-1); }.screen-mode.screen-theme-light .canvas-panel, .screen-mode.screen-theme-light .chart-card, .screen-mode.screen-theme-light .overview-card { background: #fff; border-color: var(--border-1); color: var(--text-1); }.screen-mode.screen-theme-light .scenario-item { border-color: var(--border-1); background: #f7f8fa; }.screen-mode.screen-theme-light .scenario-item span, .screen-mode.screen-theme-light .scenario-item small { color: var(--text-3); }.screen-mode.screen-theme-light .scenario-item strong, .screen-mode.screen-theme-light .chart-title strong, .screen-mode.screen-theme-light .overview-card strong, .screen-mode.screen-theme-light .contribution-list b { color: var(--text-1); }.screen-mode.screen-theme-light .chart-placeholder { background: #fafbfc; border-color: var(--border-3); }.screen-mode.screen-theme-light .el-table { --el-table-bg-color: #fff; --el-table-tr-bg-color: #fff; --el-table-header-bg-color: #f7f8fa; --el-table-text-color: #4e5969; --el-table-header-text-color: #1d2129; }
@media (max-width: 1280px) { .trend-shell { grid-template-columns: 240px minmax(0, 1fr); }.ai-panel { grid-column: 1 / -1; min-height: auto; position: static; }.metric-strip { grid-template-columns: repeat(2, 1fr); }.trend-workbench.screen-mode .trend-shell { grid-template-columns: minmax(0, 1fr) 300px; }.trend-workbench.screen-mode .ai-panel { grid-column: auto; position: sticky; } }
@media (max-width: 900px) { .trend-workbench { padding: 16px; overflow-x: hidden; }.trend-toolbar, .canvas-actions { align-items: stretch; flex-direction: column; }.toolbar-actions { min-width: 0; justify-content: flex-start; }.trend-shell, .dashboard-canvas, .trend-workbench.screen-mode .trend-shell { grid-template-columns: 1fr; }.builder-panel { min-height: auto; position: static; }.trend-workbench.screen-mode .ai-panel { position: static; }.table-card { grid-column: auto; }.canvas-selectors { display: grid; grid-template-columns: minmax(0, 1fr); width: 100%; }.canvas-selectors .el-segmented, .canvas-selectors .el-select, .canvas-buttons, .canvas-buttons .el-button { width: 100%; min-width: 0; }.canvas-selectors :deep(.el-segmented__group) { display: flex; width: 100%; min-width: 0; }.canvas-selectors :deep(.el-segmented__item) { flex: 1; min-width: 0; padding-inline: 4px; font-size: 12px; }.canvas-buttons { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }.canvas-buttons .el-button { padding-inline: 8px; }.metric-strip { grid-template-columns: minmax(0, 1fr); }.overview-card { min-width: 0; }.chart-card { min-width: 0; }.chart-box, .chart-placeholder { height: 320px; } }
</style>
