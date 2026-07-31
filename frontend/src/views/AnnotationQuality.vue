<template>
  <div class="page-container annotation-quality-page" v-loading="loading">
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

    <div class="pro-card annotation-quality-frame" ref="iframeWrapperRef">
      <iframe ref="iframeRef" :src="iframeSrc" @load="onLoad"></iframe>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { CircleCheck, Refresh, FullScreen } from '@element-plus/icons-vue'

const loading = ref(true)
const iframeRef = ref(null)
const iframeWrapperRef = ref(null)
const iframeSrc = ref('/annotation-quality.html')
const iconComp = CircleCheck
const iconLight = '#f6ffed'
const iconColor = '#52c41a'
const title = '标注质量管理模块'
const delivery = '交付形式：标注质量管理模块及规则配置后台'

const features = [
  '质量审核机制',
  '一致性检查',
  '标注员绩效评估',
  '自动校验与纠偏',
  '多轮标注支持',
  '专家仲裁机制',
  '质量评分体系',
  '抽检比例配置',
  '规则配置后台',
  '发布门禁控制',
  '质量报告导出'
]

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
  iframeSrc.value = '/annotation-quality.html?t=' + Date.now()
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
.annotation-quality-page {
  min-height: 0;
  padding-bottom: 24px;
}

.annotation-quality-frame {
  height: calc(100vh - 320px);
  min-height: 620px;
  padding: 0;
  overflow: hidden;
}

.annotation-quality-frame iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 0 0 6px 6px;
}

@media (max-width: 900px) {
  .annotation-quality-frame {
    height: calc(100vh - 350px);
    min-height: 560px;
  }
}

@media (max-width: 600px) {
  .annotation-quality-page {
    padding: 16px;
  }

  .annotation-quality-frame {
    height: 680px;
    min-height: 0;
  }
}
</style>
