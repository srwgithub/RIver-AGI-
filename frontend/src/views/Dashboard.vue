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
        <div class="focus-title">RIver_AGI_下一轮综合测试数据.xlsx</div>
        <div class="focus-meta"><span class="status-pulse"></span> 已解析 · 25 行 · 8 个字段 · 最近更新 昨天 15:56</div>
        <div class="focus-actions">
          <el-button type="primary" @click="go('/datasets')">查看数据集 <el-icon><ArrowRight /></el-icon></el-button>
          <el-button text @click="go('/analysis')">进入分析</el-button>
        </div>
      </div>
      <div class="focus-score">
        <div class="score-label">数据健康度</div>
        <div class="score-value">92<span>/100</span></div>
        <div class="score-note">较上次检查 <strong>+4.8%</strong></div>
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
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, TopRight, Bell, CaretBottom, CaretTop, CircleCheck, Cpu, DataAnalysis, DataLine, Files, Lock, MagicStick, TrendCharts, Upload } from '@element-plus/icons-vue'

const router = useRouter()
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const username = computed(() => user.value.realName || user.value.username || '系统管理员')
const currentDate = computed(() => new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date()))
const go = (path) => router.push(path)

const metrics = [
  { label: '数据集总数', value: '01', trend: '12%', caption: '较上周', trendType: 'positive', tone: 'blue', icon: Files },
  { label: '分析任务', value: '16', trend: '8%', caption: '较上周', trendType: 'positive', tone: 'green', icon: DataAnalysis },
  { label: '安全扫描', value: '02', trend: '3%', caption: '风险下降', trendType: 'positive', tone: 'orange', icon: Bell },
  { label: '预测任务', value: '00', trend: '待创建', caption: '选择数据开始', trendType: 'neutral', tone: 'violet', icon: Cpu }
]
const modules = [
  { name: '数据采集与标注', desc: '导入、清洗、协同标注', icon: Upload, color: '#1664d9', lightColor: '#eaf2ff', path: '/annotation-platform' },
  { name: '标注质量管理', desc: '抽检、审核、质量评分', icon: CircleCheck, color: '#168b5b', lightColor: '#e8f7ef', path: '/annotation-quality' },
  { name: '市场需求预测', desc: '创建预测、管理模型版本', icon: Cpu, color: '#6c49b8', lightColor: '#f2ecff', path: '/prediction-engine' },
  { name: '趋势分析看板', desc: '趋势、异常与根因分析', icon: DataLine, color: '#087d91', lightColor: '#e6f8fa', path: '/trend-dashboard' },
  { name: '预测评估优化', desc: '评估、调优、自动重训练', icon: TrendCharts, color: '#b36b0b', lightColor: '#fff5df', path: '/prediction-evaluation' },
  { name: '安全审计中心', desc: '权限、日志与合规管控', icon: Lock, color: '#be4050', lightColor: '#fff0f1', path: '/security-audit' }
]
const recentDatasets = [{ name: 'RIver_AGI_下一轮综合测试数据.xlsx', type: 'xlsx', rows: 25 }]
const activities = [
  { text: '上传了综合测试数据集', time: '昨天 15:56', color: '#1664d9' },
  { text: '完成安全扫描，发现 0 个高风险项', time: '昨天 15:58', color: '#168b5b' },
  { text: '自动生成数据画像和推荐图表', time: '昨天 16:02', color: '#6c49b8' },
  { text: '系统备份已完成', time: '今天 02:00', color: '#b36b0b' },
  { text: 'AI 助手服务运行正常', time: '今天 09:00', color: '#087d91' }
]
</script>
