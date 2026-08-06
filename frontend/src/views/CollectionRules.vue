<template>
  <div class="rules-page page-container">
    <header class="page-heading">
      <div><span class="eyebrow">COLLECTION ANNOTATION / GOVERNANCE</span><h1>数据采集标注规则配置后台</h1><p>统一维护清洗、抽检、仲裁、标签和导出规则，供任务配置页引用。</p></div>
      <el-button @click="router.push('/collection-annotation/config')">返回任务配置</el-button>
    </header>

    <el-alert title="配置保存后将作为新建任务的全局默认规则；任务级配置可以覆盖抽检比例、协同方式和标注规则。" type="info" show-icon :closable="false" class="intro" />

    <el-card shadow="never" class="rules-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="自动校验规则" name="validation">
          <div class="tab-intro">配置不同模态的标注合法性校验，以及空标注、越界标注和非法标签的处理方式。</div>
          <el-row :gutter="20">
            <el-col :xs="24" :lg="12"><el-card shadow="never" class="inner-card">
              <template #header><span>模态校验开关</span></template>
              <el-form-item label="文本标注校验"><el-switch v-model="form.validation.text" active-text="启用" inactive-text="停用" /></el-form-item>
              <el-form-item label="表格标注校验"><el-switch v-model="form.validation.table" active-text="启用" inactive-text="停用" /></el-form-item>
              <el-form-item label="图片框选校验"><el-switch v-model="form.validation.image" active-text="启用" inactive-text="停用" /></el-form-item>
              <el-form-item label="视频时段校验"><el-switch v-model="form.validation.video" active-text="启用" inactive-text="停用" /></el-form-item>
            </el-card></el-col>
            <el-col :xs="24" :lg="12"><el-card shadow="never" class="inner-card">
              <template #header><span>非法数据处理</span></template>
              <el-form-item label="空标注处理"><el-select v-model="form.validation.emptyAction"><el-option label="拦截并进入复核" value="REVIEW" /><el-option label="直接拒绝" value="REJECT" /><el-option label="仅记录告警" value="WARN" /></el-select></el-form-item>
              <el-form-item label="越界标注处理"><el-select v-model="form.validation.outOfBoundsAction"><el-option label="拦截并进入复核" value="REVIEW" /><el-option label="自动裁剪" value="CLIP" /><el-option label="直接拒绝" value="REJECT" /></el-select></el-form-item>
              <el-form-item label="非法标签处理"><el-select v-model="form.validation.invalidLabelAction"><el-option label="拦截并进入复核" value="REVIEW" /><el-option label="直接拒绝" value="REJECT" /><el-option label="仅记录告警" value="WARN" /></el-select></el-form-item>
            </el-card></el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="一致性与仲裁" name="consistency">
          <div class="tab-intro">控制多人、多轮标注的一致性判定阈值和争议自动进入仲裁池的条件。</div>
          <el-form :model="form.consistency" label-position="top" class="config-grid">
            <el-form-item label="标注相似度阈值"><el-slider v-model="form.consistency.similarityThreshold" :min="0.5" :max="1" :step="0.01" show-input /><span class="helper">低于该分值的样本判定为不一致。</span></el-form-item>
            <el-form-item label="自动触发仲裁比例"><el-slider v-model="form.consistency.arbitrationRate" :min="0" :max="1" :step="0.05" show-input /><span class="helper">分歧样本达到该比例时自动进入仲裁池。</span></el-form-item>
            <el-form-item label="最大标注轮次"><el-input-number v-model="form.consistency.maxRounds" :min="1" :max="20" /></el-form-item>
            <el-form-item label="低一致性处理"><el-select v-model="form.consistency.lowConsistencyAction"><el-option label="进入仲裁池" value="ARBITRATION" /><el-option label="下发重标" value="REANNOTATE" /><el-option label="进入人工复核" value="REVIEW" /></el-select></el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="质量评分权重" name="scoring">
          <div class="tab-intro">配置综合质量分的计算权重，权重合计应为 100%。</div>
          <el-form :model="form.scoring" label-position="top" class="config-grid">
            <el-form-item label="人工审核通过率权重"><el-input-number v-model="form.scoring.reviewWeight" :min="0" :max="100" /></el-form-item>
            <el-form-item label="多人一致性率权重"><el-input-number v-model="form.scoring.consistencyWeight" :min="0" :max="100" /></el-form-item>
            <el-form-item label="自动校验合格率权重"><el-input-number v-model="form.scoring.validationWeight" :min="0" :max="100" /></el-form-item>
            <el-form-item label="纠偏扣分权重"><el-input-number v-model="form.scoring.correctionPenaltyWeight" :min="0" :max="100" /></el-form-item>
            <el-form-item label="仲裁扣分权重"><el-input-number v-model="form.scoring.arbitrationPenaltyWeight" :min="0" :max="100" /></el-form-item>
          </el-form>
          <el-alert :title="`当前权重合计：${scoringTotal}%`" :type="scoringTotal === 100 ? 'success' : 'warning'" :closable="false" show-icon />
        </el-tab-pane>

        <el-tab-pane label="抽检与绩效规则" name="sampling">
          <div class="tab-intro">配置随机抽检、审核、仲裁以及标注员绩效核算规则。</div>
          <el-form :model="form.sampling" label-position="top" class="config-grid">
            <el-form-item label="默认抽检比例"><el-slider v-model="form.sampling.sampleRate" :min="0.05" :max="1" :step="0.05" show-input /><span class="helper">{{ Math.round(form.sampling.sampleRate * 100) }}% 的提交结果进入抽检。</span></el-form-item>
            <el-form-item label="审核机制"><el-radio-group v-model="form.sampling.reviewMode"><el-radio label="OPTIONAL">按任务配置</el-radio><el-radio label="REQUIRED">强制审核</el-radio></el-radio-group></el-form-item>
            <el-form-item label="仲裁机制"><el-switch v-model="form.sampling.arbitrationEnabled" active-text="启用" inactive-text="停用" /></el-form-item>
            <el-form-item label="仲裁触发条件"><el-select v-model="form.sampling.arbitrationTrigger"><el-option label="多人标签不一致" value="DISAGREEMENT" /><el-option label="审核驳回" value="REJECTION" /><el-option label="两者都满足" value="BOTH" /></el-select></el-form-item>
            <el-form-item label="驳回/仲裁失败是否扣绩效"><el-switch v-model="form.sampling.penalizeRejected" active-text="扣分" inactive-text="不扣分" /></el-form-item>
            <el-form-item label="绩效统计周期"><el-radio-group v-model="form.sampling.performanceCycle"><el-radio label="DAY">日</el-radio><el-radio label="WEEK">周</el-radio><el-radio label="MONTH">月</el-radio></el-radio-group></el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="actions">
        <span class="version">保存后生成新版本，当前权重合计 {{ scoringTotal }}%</span>
        <el-button @click="reset">恢复默认配置</el-button>
        <el-button :loading="snapshotting" @click="snapshot">保存版本快照</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存全部配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const activeTab = ref('validation')
const saving = ref(false)
const snapshotting = ref(false)
const defaults = {
  validation: { text: true, table: true, image: true, video: true, emptyAction: 'REVIEW', outOfBoundsAction: 'REVIEW', invalidLabelAction: 'REVIEW' },
  consistency: { similarityThreshold: 0.8, arbitrationRate: 0.2, maxRounds: 3, lowConsistencyAction: 'ARBITRATION' },
  scoring: { reviewWeight: 30, consistencyWeight: 25, validationWeight: 25, correctionPenaltyWeight: 10, arbitrationPenaltyWeight: 10 },
  sampling: { sampleRate: 0.1, reviewMode: 'OPTIONAL', arbitrationEnabled: true, arbitrationTrigger: 'DISAGREEMENT', penalizeRejected: true, performanceCycle: 'WEEK' }
}
const form = reactive(JSON.parse(JSON.stringify(defaults)))
const scoringTotal = computed(() => Object.values(form.scoring).reduce((sum, value) => sum + Number(value || 0), 0))

const applyConfig = value => {
  if (!value) return
  try { Object.assign(form.validation, value.validation || {}); Object.assign(form.consistency, value.consistency || {}); Object.assign(form.scoring, value.scoring || {}); Object.assign(form.sampling, value.sampling || {}) } catch (e) { ElMessage.warning('配置格式无法解析，已使用默认值') }
}
const load = async () => {
  try {
    const saved = await request.get('/v1/system-config/collection-annotation')
    if (saved?.configJson) applyConfig(JSON.parse(saved.configJson))
  } catch (e) { ElMessage.info('暂无已保存配置，当前使用系统默认值') }
}
const save = async () => {
  if (scoringTotal.value !== 100) return ElMessage.warning('质量评分权重合计必须为 100%')
  saving.value = true
  try { await request.put('/v1/system-config/collection-annotation', JSON.stringify(form), { headers: { 'Content-Type': 'application/json' } }); ElMessage.success('全部配置已保存') }
  catch (e) { ElMessage.error(e.message || '配置保存失败') }
  finally { saving.value = false }
}
const snapshot = async () => {
  if (scoringTotal.value !== 100) return ElMessage.warning('质量评分权重合计必须为 100%')
  snapshotting.value = true
  try { await request.post('/v1/system-config/collection-annotation/snapshots', JSON.stringify(form), { headers: { 'Content-Type': 'application/json' } }); ElMessage.success('配置版本快照已保存') }
  catch (e) { ElMessage.error(e.message || '版本快照保存失败') }
  finally { snapshotting.value = false }
}
const reset = () => { Object.assign(form.validation, JSON.parse(JSON.stringify(defaults.validation))); Object.assign(form.consistency, JSON.parse(JSON.stringify(defaults.consistency))); Object.assign(form.scoring, JSON.parse(JSON.stringify(defaults.scoring))); Object.assign(form.sampling, JSON.parse(JSON.stringify(defaults.sampling))); ElMessage.info('已恢复默认配置，点击保存后生效') }
onMounted(load)
</script>

<style scoped>
.page-heading,.actions{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:18px}.page-heading h1{margin:6px 0;font-size:26px}.page-heading p,.helper,.tab-intro,.version{margin:0;color:#86909c;font-size:13px}.eyebrow{color:#165dff;font-size:12px;font-weight:700;letter-spacing:.08em}.intro{margin-bottom:16px}.rules-card{min-height:520px}.tab-intro{padding:4px 0 18px}.inner-card{height:100%;background:#fafbfc}.inner-card :deep(.el-form-item){margin-bottom:18px}.config-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px 24px;max-width:980px}.config-grid :deep(.el-select){width:100%}.config-grid :deep(.el-input-number){width:180px}.helper{display:block;margin-top:6px}.actions{justify-content:flex-end;border-top:1px solid #e5e6eb;padding-top:18px;margin-top:20px}.version{margin-right:auto}@media(max-width:700px){.page-heading,.actions{align-items:flex-start;flex-direction:column}.config-grid{grid-template-columns:1fr}.version{margin-right:0}}
</style>
