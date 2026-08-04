<template>
  <div class="dashboard-page">
    <section class="dashboard-intro">
      <div>
        <div class="eyebrow"><span class="live-dot"></span> WORKSPACE / 数据智能工作区</div>
        <h1>把数据，变成下一步行动</h1>
        <p>{{ currentDate }} · {{ username }}，这里是你的分析概览。</p>
      </div>
      <div class="intro-actions">
        <el-button plain @click="go('/chat')"><el-icon><MagicStick /></el-icon>问问 AI</el-button>
        <el-button type="primary" @click="go('/collection-annotation')"><el-icon><Upload /></el-icon>导入数据</el-button>
      </div>
    </section>

    <section class="workspace-focus">
      <div class="focus-copy">
        <div class="section-kicker">当前工作区</div>
        <div class="focus-title">{{ activeDataset?.name || '尚未选择数据集' }}</div>
        <div class="focus-meta"><span class="status-pulse"></span> {{ activeDataset ? `已解析 · ${activeDataset.rowCount || 0} 行 · ${activeDataset.columnCount || 0} 个字段 · 最近更新 ${activeDataset.updatedAt || '—'}` : '上传数据后显示工作区状态' }}</div>
        <div class="focus-actions">
          <el-button type="primary" @click="go('/datasets')">查看数据集 <el-icon><ArrowRight /></el-icon></el-button>
          <el-button text @click="go('/analysis')">进入分析</el-button>
        </div>
      </div>
      <div class="focus-score">
        <div class="score-label">数据健康度</div>
        <div class="score-value">{{ healthScore == null ? '—' : healthScore }}<span>/100</span></div>
        <div class="score-note">{{ healthScore == null ? '运行数据质量分析后显示' : '来自最新质量分析结果' }}</div>
      </div>
      <div class="focus-orbit orbit-one"></div>
      <div class="focus-orbit orbit-two"></div>
    </section>

    <div class="dashboard-section-head">
      <div><div class="section-kicker">运营概览</div><h2>今天的工作进展</h2></div>
      <span class="section-note">数据实时同步 · {{ currentDate }}</span>
    </div>
    <section class="metric-grid">
      <article v-for="metric in metrics" :key="metric.label" class="metric-panel" :class="metric.tone">
        <div class="metric-top"><span>{{ metric.label }}</span><el-icon><component :is="metric.icon" /></el-icon></div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-foot"><span :class="metric.trendType"><el-icon><CaretTop v-if="metric.trendType === 'positive'" /><CaretBottom v-else /></el-icon>{{ metric.trend }}</span><span>{{ metric.caption }}</span></div>
      </article>
    </section>

    <div class="dashboard-section-head module-head">
      <div><div class="section-kicker">核心能力</div><h2>从数据采集到预测闭环</h2></div>
      <el-button text type="primary" @click="go('/annotation-platform')">查看全部模块 <el-icon><ArrowRight /></el-icon></el-button>
    </div>
    <section class="module-grid">
      <button v-for="module in modules" :key="module.path" class="module-tile" @click="go(module.path)">
        <span class="module-icon" :style="{ color: module.color, background: module.lightColor }"><el-icon :size="20"><component :is="module.icon" /></el-icon></span>
        <span class="module-body"><strong>{{ module.name }}</strong><small>{{ module.desc }}</small></span>
        <el-icon class="module-chevron"><TopRight /></el-icon>
      </button>
    </section>

    <section class="bottom-grid">
      <div class="surface-panel">
        <div class="panel-heading"><div><div class="section-kicker">最近数据</div><h2>数据集</h2></div><el-button text type="primary" @click="go('/datasets')">管理 <el-icon><ArrowRight /></el-icon></el-button></div>
        <div v-for="dataset in recentDatasets" :key="dataset.name" class="dataset-row">
          <div class="file-mark">XLS</div><div class="dataset-name"><strong>{{ dataset.name }}</strong><span>{{ dataset.rows }} 行 · {{ dataset.type.toUpperCase() }}</span></div><el-tag type="success" effect="plain" size="small">已解析</el-tag><el-icon class="row-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
      <div class="surface-panel activity-panel">
        <div class="panel-heading"><div><div class="section-kicker">工作记录</div><h2>最近活动</h2></div><el-button text @click="go('/audit')">审计日志 <el-icon><ArrowRight /></el-icon></el-button></div>
        <div v-for="(activity, index) in activities" :key="activity.text" class="activity-row"><span class="activity-line" :class="{ last: index === activities.length - 1 }"></span><span class="activity-dot" :style="{ background: activity.color }"></span><div><strong>{{ activity.text }}</strong><small>{{ activity.time }}</small></div></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, TopRight, Bell, CaretBottom, CaretTop, CircleCheck, Cpu, DataAnalysis, DataLine, Files, Lock, MagicStick, TrendCharts, Upload } from '@element-plus/icons-vue'
import request from '../utils/request'
import { getDatasetHealthScore, onDatasetHealth } from '../utils/workspaceSync'

const router = useRouter()
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const username = computed(() => user.value.realName || user.value.username || '系统管理员')
const currentDate = computed(() => new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date()))
const go = (path) => router.push(path)

const counts = reactive({ datasets: '—', analyses: '—', scans: '—', predictions: '—' })
const activeDataset = ref(null)
const healthScore = ref(null)
const recentDatasets = ref([])
const activities = ref([])
const metrics = computed(() => [
  { label: '数据集总数', value: counts.datasets, trend: '实时', caption: '当前数据库记录', trendType: 'neutral', tone: 'blue', icon: Files },
  { label: '分析任务', value: counts.analyses, trend: '实时', caption: '画像与质量分析', trendType: 'neutral', tone: 'green', icon: DataAnalysis },
  { label: '安全扫描', value: counts.scans, trend: '实时', caption: '已执行扫描任务', trendType: 'neutral', tone: 'orange', icon: Bell },
  { label: '预测任务', value: counts.predictions, trend: '实时', caption: '当前预测任务', trendType: 'neutral', tone: 'violet', icon: Cpu }
])
const modules = [
  { name: '数据采集与标注', desc: '导入、清洗、协同标注', icon: Upload, color: '#1664d9', lightColor: '#eaf2ff', path: '/annotation-platform' },
  { name: '标注质量管理', desc: '抽检、审核、质量评分', icon: CircleCheck, color: '#168b5b', lightColor: '#e8f7ef', path: '/annotation-quality' },
  { name: '市场需求预测', desc: '创建预测、管理模型版本', icon: Cpu, color: '#6c49b8', lightColor: '#f2ecff', path: '/prediction-engine' },
  { name: '趋势分析看板', desc: '趋势、异常与根因分析', icon: DataLine, color: '#087d91', lightColor: '#e6f8fa', path: '/trend-dashboard' },
  { name: '预测评估优化', desc: '评估、调优、自动重训练', icon: TrendCharts, color: '#b36b0b', lightColor: '#fff5df', path: '/prediction-evaluation' },
  { name: '安全审计中心', desc: '权限、日志与合规管控', icon: Lock, color: '#be4050', lightColor: '#fff0f1', path: '/security-audit' }
]
onMounted(async () => {
  try {
    const [datasets, analyses, scans, predictions, audit] = await Promise.all([
      request.get('/v1/datasets?page=1&size=20'), request.get('/v1/analysis/tasks/count'), request.get('/v1/security/scans/count'), request.get('/v1/predictions/count'), request.get('/v1/audit/logs?page=1&size=5')
    ])
    const records = datasets.records || datasets.data?.records || []
    recentDatasets.value = records.slice(0, 5).map(item => ({ name: item.name, type: item.fileType || item.fileName?.split('.').pop() || 'file', rows: item.rowCount || 0 }))
    activeDataset.value = records[0] || null
    healthScore.value = getDatasetHealthScore(activeDataset.value?.id)
    if (activeDataset.value?.id) {
      try {
        const task = await request.post(`/v1/analysis/quality?datasetId=${activeDataset.value.id}`)
        const result = typeof task?.resultJson === 'string' ? JSON.parse(task.resultJson) : task?.resultJson
        const score = result?.overallScore ?? result?.qualityScore
        healthScore.value = score == null ? healthScore.value : Math.round(Number(score) * (Number(score) <= 1 ? 100 : 1))
      } catch (qualityError) {
        healthScore.value = null
      }
    }
    counts.datasets = String(datasets.total ?? records.length)
    counts.analyses = String(analyses ?? 0); counts.scans = String(scans ?? 0); counts.predictions = String(predictions ?? 0)
    const logs = audit.records || audit.data?.records || []
    activities.value = logs.map((item, index) => ({ text: item.actionType || item.action || '系统操作', time: item.createdAt || '最近', color: ['#1664d9','#168b5b','#6c49b8','#b36b0b'][index % 4] }))
  } catch (error) {
    activities.value = []
  }
})

onDatasetHealth(({ datasetId, score }) => {
  if (activeDataset.value && String(activeDataset.value.id) === String(datasetId)) {
    healthScore.value = Math.round(Number(score))
  }
})
</script>
