<template>
  <div class="page-container annotation-platform-page" v-loading="loading">
    <header class="platform-header">
      <div class="platform-identity">
          <div class="platform-icon">
            <el-icon :size="26"><component :is="iconComp" /></el-icon>
          </div>
          <div>
            <h1>{{ title }}</h1>
            <p>{{ delivery }}</p>
          </div>
      </div>
      <div class="platform-actions">
          <el-button @click="refreshFrame"><el-icon><Refresh /></el-icon> 刷新</el-button>
          <el-button type="primary" @click="toggleFullscreen"><el-icon><FullScreen /></el-icon> 全屏</el-button>
      </div>
    </header>

    <div class="capability-strip">
      <span v-for="tag in features" :key="tag"><el-icon><CircleCheck /></el-icon>{{ tag }}</span>
    </div>

    <div class="pro-card annotation-platform-frame" ref="iframeWrapperRef">
      <iframe ref="iframeRef" :src="iframeSrc" @load="onLoad"></iframe>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Upload, Refresh, FullScreen, CircleCheck } from '@element-plus/icons-vue'

const loading = ref(true)
const iframeRef = ref(null)
const iframeWrapperRef = ref(null)
const iframeSrc = ref('/annotation-platform.html')
const iconComp = Upload
const title = '数据采集与标注平台'
const delivery = '统一管理数据导入、标签配置、协同标注与质量审核'

const features = [
  '多源数据导入',
  '文本/图片/视频/音频多模态支持',
  '数据清洗（去重/去空/格式校验）',
  '标注任务分配',
  '协同标注工作台',
  '自定义标注规则',
  '标签体系管理',
  '标注质量抽检',
  '数据校验功能',
  '多模态媒体预览'
]

const onLoad = () => {
  loading.value = false
}

const refreshFrame = () => {
  loading.value = true
  iframeSrc.value = '/annotation-platform.html?t=' + Date.now()
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
.annotation-platform-page {
  min-height: 0;
  padding-bottom: 24px;
}
.platform-header { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 24px; }.platform-identity { display: flex; align-items: center; gap: 14px; }.platform-icon { width: 48px; height: 48px; display: grid; place-items: center; color: var(--primary); background: var(--primary-light); border-radius: 8px; }.platform-header h1 { margin: 0; color: var(--text-1); font-size: 24px; line-height: 1.2; font-weight: 600; }.platform-header p { margin: 7px 0 0; color: var(--text-3); font-size: 14px; }.platform-actions { display: flex; gap: 8px; }.capability-strip { display: flex; align-items: center; gap: 20px; margin-bottom: 16px; padding: 12px 16px; overflow-x: auto; background: #fff; border: 1px solid var(--border-1); border-radius: 8px; color: var(--text-2); white-space: nowrap; }.capability-strip span { display: flex; align-items: center; gap: 6px; font-size: 12px; }.capability-strip .el-icon { color: var(--success); }

.annotation-platform-frame {
  height: calc(100vh - 320px);
  min-height: 620px;
  padding: 0;
  overflow: hidden;
}

.annotation-platform-frame iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 0 0 6px 6px;
}

@media (max-width: 900px) {
  .annotation-platform-frame {
    height: calc(100vh - 350px);
    min-height: 560px;
  }
}

@media (max-width: 600px) {
  .annotation-platform-page {
    padding: 16px;
  }
  .platform-header { align-items: flex-start; flex-direction: column; }.platform-actions { width: 100%; }.platform-actions .el-button { flex: 1; }

  .annotation-platform-frame {
    height: 680px;
    min-height: 0;
  }
}
</style>
