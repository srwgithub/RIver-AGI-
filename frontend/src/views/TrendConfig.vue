<template>
  <div class="trend-config page-container">
    <header class="config-header">
      <div>
        <span class="eyebrow">TREND DASHBOARD / GOVERNANCE</span>
        <h1>趋势分析看板及可视化报表配置后台</h1>
        <p>统一管理市场趋势模型参数、异常判定阈值、根因分析规则、可视化模板、AI分析口径、报表导出与权限策略，为前端可视化分析能力提供统一底层规则支撑。</p>
      </div>
      <div class="config-actions">
        <el-button @click="restore">恢复默认</el-button>
        <el-button @click="router.push('/trend-dashboard')">返回工作台</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </div>
    </header>

    <section class="config-grid">
      <article class="config-card span-2">
        <div class="card-title"><strong>市场趋势模型参数</strong><el-tag effect="plain">模型底座</el-tag></div>
        <div class="form-grid three">
          <el-form-item label="周期识别阈值"><el-slider v-model="form.periodThreshold" :min="0.05" :max="1" :step="0.05" show-input /></el-form-item>
          <el-form-item label="季节拆分参数"><el-input-number v-model="form.seasonWindow" :min="3" :max="52" /></el-form-item>
          <el-form-item label="趋势平滑参数"><el-slider v-model="form.smoothing" :min="0.05" :max="1" :step="0.05" show-input /></el-form-item>
        </div>
      </article>

      <article class="config-card">
        <div class="card-title"><strong>异常检测规则</strong><el-tag type="warning" effect="plain">自动标记</el-tag></div>
        <el-form label-position="top">
          <el-form-item label="同比波动阈值"><el-slider v-model="form.yoyThreshold" :min="0.05" :max="1" :step="0.05" show-input /></el-form-item>
          <el-form-item label="预测偏差阈值"><el-slider v-model="form.deviationThreshold" :min="0.05" :max="1" :step="0.05" show-input /></el-form-item>
          <el-form-item label="极值自动标记"><el-switch v-model="form.autoMark" active-text="启用" inactive-text="停用" /></el-form-item>
        </el-form>
      </article>

      <article class="config-card">
        <div class="card-title"><strong>根因分析判定规则</strong><el-tag type="success" effect="plain">归因权重</el-tag></div>
        <div class="weight-list">
          <label v-for="item in rootCauseWeights" :key="item.key"><span>{{ item.label }}</span><el-slider v-model="form.rootCauseWeights[item.key]" :min="0" :max="100" /></label>
        </div>
      </article>

      <article class="config-card span-2">
        <div class="card-title"><strong>系统预设看板模板</strong><el-button link type="primary" @click="addTemplate">新增模板</el-button></div>
        <el-table :data="form.templates" size="small" height="232">
          <el-table-column prop="name" label="模板名称" min-width="160" />
          <el-table-column prop="scene" label="适用场景" min-width="150" />
          <el-table-column prop="widgets" label="组件" min-width="180" />
          <el-table-column label="状态" width="90"><template #default="scope"><el-switch v-model="scope.row.enabled" /></template></el-table-column>
        </el-table>
      </article>

      <article class="config-card">
        <div class="card-title"><strong>可视化全局样式</strong><el-tag effect="plain">大屏主题</el-tag></div>
        <el-form label-position="top">
          <el-form-item label="图表配色"><el-segmented v-model="form.palette" :options="paletteOptions" /></el-form-item>
          <el-form-item label="大屏主题"><el-select v-model="form.screenTheme"><el-option label="深色经营大屏" value="DARK" /><el-option label="浅色分析大屏" value="LIGHT" /></el-select></el-form-item>
          <el-form-item label="坐标轴规范"><el-checkbox-group v-model="form.axisRules"><el-checkbox label="SHOW_GRID">显示网格线</el-checkbox><el-checkbox label="AUTO_UNIT">自动单位</el-checkbox><el-checkbox label="ZERO_BASELINE">零基线</el-checkbox></el-checkbox-group></el-form-item>
        </el-form>
      </article>

      <article class="config-card">
        <div class="card-title"><strong>AI 智能分析口径</strong><el-tag type="info" effect="plain">输出模板</el-tag></div>
        <el-form label-position="top">
          <el-form-item label="趋势解读模板"><el-input v-model="form.aiTrendTemplate" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="异常诊断模板"><el-input v-model="form.aiAnomalyTemplate" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="决策建议模板"><el-input v-model="form.aiDecisionTemplate" type="textarea" :rows="3" /></el-form-item>
        </el-form>
      </article>

      <article class="config-card span-2">
        <div class="card-title"><strong>自定义报表权限与导出规则</strong><el-tag type="success" effect="plain">审计留痕</el-tag></div>
        <div class="form-grid three">
          <el-form-item label="报表格式"><el-checkbox-group v-model="form.formats"><el-checkbox label="PNG">图片</el-checkbox><el-checkbox label="PDF">PDF</el-checkbox><el-checkbox label="XLSX">Excel</el-checkbox></el-checkbox-group></el-form-item>
          <el-form-item label="定时推送"><el-switch v-model="form.schedule" active-text="启用" inactive-text="停用" /></el-form-item>
          <el-form-item label="留存周期"><el-input-number v-model="form.retentionDays" :min="7" :max="3650" /><span class="unit">天</span></el-form-item>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const saving = ref(false)
const defaults = {
  periodThreshold: 0.2,
  seasonWindow: 12,
  smoothing: 0.35,
  yoyThreshold: 0.25,
  deviationThreshold: 0.18,
  autoMark: true,
  rootCauseWeights: { market: 35, sample: 24, quality: 18, model: 23 },
  templates: [
    { name: '市场需求趋势总览', scene: '经营复盘 / 战略规划', widgets: '趋势折线、异常散点、同比环比卡片', enabled: true },
    { name: '模型效果决策看板', scene: '预测评估 / 模型迭代', widgets: 'A/B对比、偏差分析、根因表格', enabled: true },
    { name: '质量与绩效看板', scene: '标注质量 / 数据治理', widgets: '质量趋势、绩效排名、异常分布', enabled: false }
  ],
  palette: 'BUSINESS',
  screenTheme: 'DARK',
  axisRules: ['SHOW_GRID', 'AUTO_UNIT'],
  aiTrendTemplate: '请基于真实销量、AI预测需求量和周期波动，总结上涨、下降、平稳或周期性变化。',
  aiAnomalyTemplate: '请识别预测偏离、数据跳变、极值异常，并输出异常时段、幅度和影响范围。',
  aiDecisionTemplate: '请结合趋势与根因，输出产能、库存、渠道投放和模型迭代建议。',
  formats: ['PNG', 'PDF', 'XLSX'],
  schedule: false,
  retentionDays: 365
}
const form = reactive(JSON.parse(JSON.stringify(defaults)))
const rootCauseWeights = [
  { key: 'market', label: '市场波动' },
  { key: 'sample', label: '数据样本问题' },
  { key: 'quality', label: '标注质量问题' },
  { key: 'model', label: '模型拟合偏差' }
]
const paletteOptions = [{ label: '商务蓝绿', value: 'BUSINESS' }, { label: '风险红橙', value: 'RISK' }, { label: '大屏深色', value: 'SCREEN' }]

const save = async () => {
  saving.value = true
  try {
    await request.put('/v1/system-config/trend-dashboard', JSON.stringify(form), { headers: { 'Content-Type': 'application/json' } })
    ElMessage.success('趋势看板配置已保存')
  } catch (error) {
    ElMessage.error(error.message || '趋势看板配置保存失败，未写入后台')
  } finally { saving.value = false }
}
const restore = () => { Object.assign(form, JSON.parse(JSON.stringify(defaults))); ElMessage.info('已恢复默认配置，请保存后生效') }
const addTemplate = () => form.templates.push({ name: '自定义分析看板', scene: '业务自助分析', widgets: '趋势、对比、表格', enabled: true })

onMounted(async () => {
  try {
    const saved = await request.get('/v1/system-config/trend-dashboard')
    if (saved?.configJson) Object.assign(form, JSON.parse(saved.configJson))
  } catch (error) {
    ElMessage.warning(error.message || '趋势配置读取失败，当前显示未保存的表单默认值')
  }
})
</script>

<style scoped>
.trend-config { box-sizing: border-box; width: 100%; min-width: 0; padding: 24px; max-width: 1440px; overflow: hidden; }
.config-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; padding: 20px; background: #fff; border: 1px solid var(--border-1); border-radius: 8px; box-shadow: var(--shadow-card); }
.eyebrow { color: #0f8b79; font-size: 12px; font-weight: 700; letter-spacing: 0; }
.config-header h1 { margin: 7px 0; color: var(--text-1); font-size: 24px; line-height: 1.2; font-weight: 600; }
.config-header p { max-width: 900px; margin: 0; color: var(--text-3); font-size: 14px; line-height: 1.5; }
.config-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; min-width: 320px; }.config-actions .el-button { margin-left: 0; }
.config-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.config-card { min-width: 0; overflow: hidden; padding: 18px; background: #fff; border: 1px solid var(--border-1); border-radius: 8px; box-shadow: var(--shadow-card); }
.config-card.span-2 { grid-column: span 2; }
.card-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-width: 0; margin-bottom: 16px; }.card-title strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-title strong { color: var(--text-1); font-size: 16px; font-weight: 600; }
.form-grid { display: grid; gap: 16px; }.form-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.trend-config :deep(.el-form-item) { margin-bottom: 14px; }.trend-config :deep(.el-select) { width: 100%; }.trend-config :deep(.el-input-number) { width: 140px; }
.trend-config :deep(.form-grid .el-form-item) { display: block; }
.trend-config :deep(.form-grid .el-form-item__label) { display: block; width: auto; height: auto; margin-bottom: 8px; padding: 0; line-height: 20px; text-align: left; }
.trend-config :deep(.form-grid .el-form-item__content) { display: block; min-width: 0; margin-left: 0 !important; line-height: normal; }
.trend-config :deep(.form-grid .el-input-number), .trend-config :deep(.form-grid .el-slider) { width: 100%; max-width: 100%; }
.weight-list { display: grid; gap: 12px; }.weight-list label { display: grid; grid-template-columns: 90px 1fr; align-items: center; gap: 10px; color: var(--text-2); font-size: 13px; }
.unit { margin-left: 8px; color: var(--text-3); font-size: 12px; }
@media (min-width: 1600px) { .config-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
@media (max-width: 1180px) { .form-grid.three { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .trend-config { padding: 16px; }.config-header { align-items: stretch; flex-direction: column; }.config-actions { min-width: 0; justify-content: flex-start; }.config-grid, .config-card.span-2 { grid-template-columns: 1fr; grid-column: auto; } }
</style>
