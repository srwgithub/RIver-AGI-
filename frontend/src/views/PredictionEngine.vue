<template>
  <div class="page-container prediction-engine-page" v-loading="loading">
    <div class="pro-card" style="margin-bottom:var(--space-4);">
      <div class="pro-card-body" style="display:flex;align-items:center;justify-content:space-between;">
        <div style="display:flex;align-items:center;gap:16px;">
          <div class="kpi-icon" :style="{background:iconLight,color:iconColor}" style="width:52px;height:52px;border-radius:8px;">
            <el-icon :size="26"><component :is="iconComp" /></el-icon>
          </div>
          <div>
            <h2 style="font-size:20px;font-weight:600;margin:0 0 4px;color:var(--text-1);">{{ title }}</h2>
            <p style="font-size:13px;color:var(--text-3);margin:0;">{{ delivery }}</p>
          </div>
        </div>
        <div>
          <el-button @click="refreshFrame"><el-icon><Refresh /></el-icon> 刷新</el-button>
          <el-button type="primary" @click="toggleFullscreen"><el-icon><FullScreen /></el-icon> 全屏</el-button>
        </div>
      </div>
    </div>

    <div class="pro-card" style="margin-bottom:var(--space-4);">
      <div class="pro-card-header">
        <span class="pro-card-title">合同研发功能清单</span>
        <el-tag type="info" effect="plain">{{ features.length }} 项</el-tag>
      </div>
      <div class="pro-card-body" style="display:flex;flex-wrap:wrap;gap:8px;">
        <el-tag v-for="tag in features" :key="tag" :type="tagType(tag)" effect="plain" size="default" style="font-size:13px;padding:4px 10px;border-radius:4px;">{{ tag }}</el-tag>
      </div>
    </div>

    <div class="pro-card prediction-engine-frame" ref="iframeWrapperRef">
      <iframe ref="iframeRef" :src="iframeSrc" @load="onLoad"></iframe>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Cpu, Refresh, FullScreen } from '@element-plus/icons-vue'

const loading = ref(true)
const iframeRef = ref(null)
const iframeWrapperRef = ref(null)
const route = useRoute()
const iframeSrc = ref(buildIframeSrc(route.query.tab))
const iconComp = Cpu
const iconLight = '#f9f0ff'
const iconColor = '#722ed1'
const title = '市场需求预测引擎'
const delivery = '交付形式：需求预测引擎模块及模型管理后台'

const features = [
  '时间序列分析（Holt-Winters/ARIMA）',
  '回归预测算法',
  '分类预测（决策树/随机森林/逻辑回归）',
  '深度学习支持（LSTM/Transformer/MLP）',
  'TensorFlow/PyTorch集成',
  '多算法自动选优',
  '模型版本管理',
  'A/B测试对比',
  '生产版本部署',
  '置信区间预测',
  '模型训练监控'
]

function buildIframeSrc(tab) {
  // The outer router uses "models" while the embedded page calls this tab "versions".
  const normalizedTab = tab === 'models' ? 'versions' : tab
  return normalizedTab
    ? `/prediction-engine.html?tab=${encodeURIComponent(normalizedTab)}`
    : '/prediction-engine.html'
}

watch(() => route.query.tab, (tab) => {
  loading.value = true
  iframeSrc.value = buildIframeSrc(tab)
})

const tagType = (tag) => {
  if (tag.includes('审核') || tag.includes('安全') || tag.includes('管控') || tag.includes('仲裁')) return 'danger'
  if (tag.includes('学习') || tag.includes('TensorFlow') || tag.includes('PyTorch') || tag.includes('深度学习') || tag.includes('LSTM') || tag.includes('Transformer')) return ''
  if (tag.includes('算法') || tag.includes('模型') || tag.includes('预测') || tag.includes('分析')) return 'success'
  if (tag.includes('配置') || tag.includes('管理') || tag.includes('版本') || tag.includes('规则')) return 'warning'
  return 'info'
}

const onLoad = () => {
  loading.value = false
}

const refreshFrame = () => {
  loading.value = true
  iframeSrc.value = '/prediction-engine.html?t=' + Date.now()
}

const toggleFullscreen = () => {
  const el = iframeWrapperRef.value
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}
</script>

<style scoped>
.prediction-engine-page {
  min-height: 0;
  padding-bottom: 24px;
}

.prediction-engine-frame {
  height: calc(100vh - 320px);
  min-height: 620px;
  padding: 0;
  overflow: hidden;
}

.prediction-engine-frame iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 0 0 6px 6px;
}

@media (max-width: 900px) {
  .prediction-engine-frame {
    height: calc(100vh - 350px);
    min-height: 560px;
  }
}

@media (max-width: 600px) {
  .prediction-engine-page {
    padding: 16px;
  }

  .prediction-engine-frame {
    height: 680px;
    min-height: 0;
  }
}
</style>
