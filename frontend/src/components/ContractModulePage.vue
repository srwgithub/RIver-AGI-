<template>
  <div class="module-page">
    <section class="module-hero">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h1>{{ title || summary.title }}</h1>
        <p class="desc">{{ description || summary.description }}</p>
      </div>
      <div class="dataset-box">
        <span>当前数据集</span>
        <strong>{{ activeDatasetId ? `#${activeDatasetId}` : '未选择' }}</strong>
        <small>{{ activeDatasetId ? '已与首页/数据集上传同步' : '上传文件后各模块会自动同步' }}</small>
      </div>
    </section>

    <section class="acceptance-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Acceptance Mapping</p>
          <h2>合同验收功能矩阵</h2>
        </div>
        <el-tag type="success" effect="light">模块交付能力前置展示</el-tag>
      </div>
      <div class="acceptance-grid">
        <div v-for="item in acceptance" :key="item.title" class="acceptance-item">
          <span class="check">✓</span>
          <div>
            <strong>{{ item.title }}</strong>
            <small>{{ item.desc }}</small>
          </div>
        </div>
      </div>
    </section>

    <section v-if="$slots.business" class="business-panel">
      <slot name="business" />
    </section>

    <section class="function-grid">
      <button
        v-for="item in actions"
        :key="item.name"
        class="function-card"
        :disabled="loading"
        @click="execute(item.name)"
      >
        <span class="badge">{{ item.badge }}</span>
        <strong>{{ item.name }}</strong>
        <small>{{ item.desc }}</small>
      </button>
    </section>

    <section class="content-grid">
      <div class="panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Workflow</p>
            <h2>用户操作流程</h2>
          </div>
          <el-button :loading="loading" @click="loadSummary">刷新状态</el-button>
        </div>
        <el-timeline class="timeline">
          <el-timeline-item v-for="step in summary.steps" :key="step" color="#0f766e">
            {{ step }}
          </el-timeline-item>
        </el-timeline>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Result</p>
            <h2>后端执行反馈</h2>
          </div>
          <el-tag :type="lastResult ? (lastResult.redirect ? 'warning' : 'success') : 'info'">
            {{ lastResult ? (lastResult.redirect ? '待跳转' : '已执行') : '待操作' }}
          </el-tag>
        </div>
        <el-empty v-if="!lastResult" :image-size="82" description="点击上方功能卡片后展示执行结果" />
        <div v-else class="result-card">
          <strong>{{ lastResult.action }}</strong>
          <p>{{ lastResult.message }}</p>
          <small>{{ lastResult.nextStep }}</small>
          <div v-if="lastResult.redirect" class="redirect-action" style="margin-top: 12px;">
            <el-tag type="primary" effect="plain" style="margin-right: 8px;">
              目标页面: {{ lastResult.redirect }}
            </el-tag>
            <el-button type="primary" size="small" @click="goToRedirect">
              立即跳转
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <section class="panel log-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Trace</p>
          <h2>最近操作记录</h2>
        </div>
      </div>
      <el-table :data="summary.lastActions || []" stripe empty-text="暂无操作记录">
        <el-table-column prop="action" label="操作" min-width="180" />
        <el-table-column prop="message" label="结果" min-width="320" show-overflow-tooltip />
        <el-table-column prop="finishedAt" label="完成时间" width="210" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : (row.status === 'REDIRECT' ? 'warning' : 'info')">
              {{ row.status === 'COMPLETED' ? '已完成' : (row.status === 'REDIRECT' ? '跳转中' : row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { getActiveDatasetId, onDatasetSync } from '../utils/workspaceSync'

const router = useRouter()
const props = defineProps({
  module: { type: String, required: true },
  eyebrow: { type: String, default: 'Contract Module' },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  actions: { type: Array, required: true },
  acceptance: { type: Array, default: () => [] }
})

const loading = ref(false)
const activeDatasetId = ref(getActiveDatasetId())
const lastResult = ref(null)
const summary = ref({
  title: '',
  description: '',
  steps: [],
  lastActions: []
})

let offSync = null

onMounted(() => {
  offSync = onDatasetSync(id => {
    activeDatasetId.value = id
  })
  loadSummary()
})

onBeforeUnmount(() => {
  if (offSync) offSync()
})

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await request.get(`/v1/module-actions/summary?module=${props.module}`)
  } catch (error) {
    ElMessage.error(error.message || '模块状态加载失败')
  } finally {
    loading.value = false
  }
}

async function execute(action) {
  loading.value = true
  try {
    const redirect = resolveBusinessRoute(props.module, action)
    lastResult.value = { action, redirect, status: 'REDIRECT', message: `已打开${action}业务入口`, nextStep: '请在目标页面完成真实业务操作。' }
    await router.push(redirect)
  } catch (error) {
    ElMessage.error(error.message || `${action} 执行失败`)
  } finally {
    loading.value = false
  }
}

function resolveBusinessRoute(module, action) {
  const text = String(action || '')
  if (module === 'annotation-platform') return text.includes('标注') || text.includes('标签') ? '/annotation-platform' : '/collection-annotation'
  if (module === 'annotation-quality') return text.includes('规则') ? '/annotation-quality/rules' : '/annotation-quality'
  if (module === 'prediction-engine') return text.includes('版本') || text.includes('A/B') ? `/prediction-engine?tab=${text.includes('A/B') ? 'abtest' : 'models'}` : '/prediction'
  if (module === 'prediction-evaluation') return '/model-optimization'
  if (module === 'trend-dashboard') return text.includes('报告') ? '/charts' : '/trend-dashboard'
  if (module === 'security-audit') return text.includes('日志') || text.includes('审计') ? '/audit' : (text.includes('权限') || text.includes('备份') ? '/security-admin' : '/security')
  return '/dashboard'
}

function goToRedirect() {
  if (lastResult.value?.redirect) {
    router.push(lastResult.value.redirect)
  }
}
</script>

<style scoped>
.module-page {
  min-height: calc(100vh - 96px);
  padding: 22px;
  background:
    radial-gradient(circle at top right, rgba(15, 118, 110, .08), transparent 30%),
    linear-gradient(180deg, #f8faf9 0%, #eef4f3 100%);
}

.module-hero,
.panel,
.acceptance-panel,
.acceptance-item,
.function-card,
.dataset-box {
  border: 1px solid #dce8e6;
  background: rgba(255, 255, 255, .94);
  border-radius: 18px;
  box-shadow: 0 14px 32px rgba(15, 23, 42, .06);
}

.module-hero {
  display: flex;
  justify-content: space-between;
  gap: 22px;
  padding: 24px;
  margin-bottom: 16px;
}

.eyebrow {
  margin: 0 0 7px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .08em;
  text-transform: uppercase;
}

h1,
h2 {
  margin: 0;
  color: #10201f;
}

h1 {
  font-size: clamp(24px, 2.6vw, 34px);
}

h2 {
  font-size: 17px;
}

.desc {
  max-width: 780px;
  margin: 10px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.dataset-box {
  min-width: 220px;
  padding: 16px;
}

.dataset-box span,
.dataset-box small {
  display: block;
  color: #64748b;
}

.dataset-box strong {
  display: block;
  margin: 8px 0;
  color: #0f766e;
  font-size: 26px;
}

.function-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.business-panel {
  margin-bottom: 16px;
}

.business-panel :deep(> *) {
  padding: 18px;
  border: 1px solid #dce8e6;
  background: rgba(255, 255, 255, .94);
  border-radius: 18px;
  box-shadow: 0 14px 32px rgba(15, 23, 42, .06);
}

.acceptance-panel {
  padding: 18px;
  margin-bottom: 16px;
}

.acceptance-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.acceptance-item {
  display: flex;
  gap: 12px;
  min-height: 92px;
  padding: 14px;
  transition: transform .18s ease, border-color .18s ease, background .18s ease;
}

.acceptance-item:hover {
  transform: translateY(-3px);
  border-color: #8bd1c9;
  background: #f6fcfb;
}

.check {
  display: inline-flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  background: #0f766e;
  font-weight: 900;
}

.acceptance-item strong,
.acceptance-item small {
  display: block;
}

.acceptance-item strong {
  color: #10201f;
  font-size: 15px;
}

.acceptance-item small {
  margin-top: 6px;
  color: #64748b;
  line-height: 1.55;
}

.function-card {
  min-height: 146px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.function-card:hover:not(:disabled) {
  transform: translateY(-4px);
  border-color: #8bd1c9;
  box-shadow: 0 18px 36px rgba(15, 118, 110, .13);
}

.function-card:disabled {
  cursor: not-allowed;
  opacity: .7;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-bottom: 12px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  font-weight: 800;
}

.function-card strong,
.function-card small {
  display: block;
}

.function-card strong {
  color: #10201f;
  font-size: 16px;
}

.function-card small {
  margin-top: 8px;
  color: #64748b;
  line-height: 1.55;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, .7fr);
  gap: 16px;
  margin-bottom: 16px;
}

.panel {
  padding: 18px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.timeline {
  margin-top: 16px;
}

.result-card {
  margin-top: 14px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid #cde8e4;
  background: #f3fbfa;
}

.result-card strong,
.result-card small {
  display: block;
}

.result-card p {
  margin: 8px 0;
  color: #334155;
}

.result-card small {
  color: #0f766e;
}

.log-panel :deep(.el-table) {
  margin-top: 12px;
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-button--primary) {
  --el-button-bg-color: #0f766e;
  --el-button-border-color: #0f766e;
  --el-button-hover-bg-color: #115e59;
  --el-button-hover-border-color: #115e59;
}

:deep(.el-table th.el-table__cell) {
  background: #edf5f4;
  color: #334155;
}

@media (max-width: 1180px) {
  .function-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .acceptance-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .module-page {
    padding: 14px;
  }

  .module-hero {
    flex-direction: column;
  }

  .function-grid {
    grid-template-columns: 1fr;
  }

  .acceptance-grid {
    grid-template-columns: 1fr;
  }
}
</style>
