<template>
  <div class="page-container" v-loading="loading">
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

    <div class="pro-card" style="padding:0;flex:1;min-height:500px;display:flex;flex-direction:column;" ref="iframeWrapperRef">
      <iframe ref="iframeRef" :src="iframeSrc" style="width:100%;flex:1;min-height:500px;border:none;border-radius:0 0 6px 6px;" @load="onLoad"></iframe>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Lock, Refresh, FullScreen } from '@element-plus/icons-vue'

const loading = ref(true)
const iframeRef = ref(null)
const iframeWrapperRef = ref(null)
const iframeSrc = ref('/security-audit.html')
const iconComp = Lock
const iconLight = '#fff2f0'
const iconColor = '#ff4d4f'
const title = '数据管理与安全审计'
const delivery = '交付形式：审计中心及安全管理后台'

const features = [
  '全量操作日志记录',
  '审计追溯查询',
  '数据安全管控',
  '权限分级管理（RBAC）',
  '数据备份机制',
  '数据恢复功能',
  '敏感数据检测',
  '数据脱敏处理',
  '安全策略配置',
  '备份记录管理',
  '合规检查清单',
  '《数据安全法》合规',
  '《个人信息保护法》合规'
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
  iframeSrc.value = '/security-audit.html?t=' + Date.now()
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
