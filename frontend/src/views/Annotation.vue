<template>
  <div class="annotation">
    <h2>数据标注</h2>
    
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="8">
        <el-select v-model="selectedDataset" placeholder="选择数据集">
          <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-select v-model="selectedSchema" placeholder="选择标签体系">
          <el-option v-for="schema in labelSchemas" :key="schema.id" :label="schema.name" :value="schema.id" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-button type="primary" @click="createTask">创建标注任务</el-button>
      </el-col>
    </el-row>
    
    <el-card v-if="task" style="margin-bottom: 20px;">
      <template #header>
        <span>标注任务 - {{ task.name }}</span>
      </template>
      <el-descriptions :column="3">
        <el-descriptions-item label="任务状态">{{ task.status }}</el-descriptions-item>
        <el-descriptions-item label="总行数">{{ task.totalRows }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ task.completedRows }}</el-descriptions-item>
        <el-descriptions-item label="质量评分">{{ percent(task.qualityScore) }}</el-descriptions-item>
        <el-descriptions-item label="通过率">{{ percent(task.passRate) }}</el-descriptions-item>
        <el-descriptions-item label="一致性">{{ percent(task.consistencyRate) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="task" class="quality-card">
      <template #header><div class="quality-header"><span>标注质量管理</span><el-tag :type="qualityPassed ? 'success' : 'warning'">{{ qualityPassed ? '已达发布标准' : '需要整改' }}</el-tag></div></template>
      <div class="quality-actions">
        <el-button @click="autoValidate" :loading="loading">自动校验与纠偏</el-button>
        <el-button @click="runSampling" :loading="loading">质量抽检</el-button>
        <el-button @click="runConsistency" :loading="loading">一致性检查</el-button>
        <el-button type="primary" @click="loadQuality" :loading="loading">刷新质量指标</el-button>
        <el-button type="success" :disabled="!qualityPassed" @click="publishTask">达标发布</el-button>
      </div>
      <el-alert v-if="qualityReport" :title="qualityReport.recommendation || '质量检查完成'" :type="qualityPassed ? 'success' : 'warning'" :closable="false" show-icon />
      <el-row v-if="qualityMetrics" :gutter="12" class="metric-row">
        <el-col :span="6"><div class="metric"><b>{{ percent(qualityMetrics.qualityScore) }}</b><span>综合质量分</span></div></el-col>
        <el-col :span="6"><div class="metric"><b>{{ percent(qualityMetrics.approveRate) }}</b><span>审核通过率</span></div></el-col>
        <el-col :span="6"><div class="metric"><b>{{ percent(qualityMetrics.correctionRate) }}</b><span>纠偏率</span></div></el-col>
        <el-col :span="6"><div class="metric"><b>{{ qualityMetrics.rejectedCount || 0 }}</b><span>待整改数量</span></div></el-col>
      </el-row>
    </el-card>

    <el-card class="rules-card">
      <template #header><div class="quality-header"><span>质量规则配置后台</span><el-button type="primary" size="small" @click="openRule">新增规则</el-button></div></template>
      <el-table :data="rules" size="small" stripe>
        <el-table-column prop="name" label="规则名称" />
        <el-table-column prop="code" label="编码" />
        <el-table-column prop="ruleType" label="规则类型" />
        <el-table-column label="阈值"><template #default="s">{{ s.row.threshold == null ? '-' : s.row.threshold }}</template></el-table-column>
        <el-table-column label="状态"><template #default="s"><el-switch v-model="s.row.enabled" @change="toggleRule(s.row)" /></template></el-table-column>
        <el-table-column prop="priority" label="优先级" />
        <el-table-column label="操作" width="150"><template #default="s"><el-button link type="primary" @click="openRule(s.row)">编辑</el-button><el-button link type="danger" @click="removeRule(s.row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="rule-tip">启用规则会参与“自动校验与纠偏”，优先级数字越小越先执行。</div>
    </el-card>

    <el-dialog v-model="ruleDialog" :title="editingRule.id ? '编辑质量规则' : '新增质量规则'" width="520px">
      <el-form :model="editingRule" label-width="100px">
        <el-form-item label="规则名称"><el-input v-model="editingRule.name" /></el-form-item>
        <el-form-item label="规则编码"><el-input v-model="editingRule.code" :disabled="!!editingRule.id" placeholder="如 MIN_CONFIDENCE" /></el-form-item>
        <el-form-item label="规则类型"><el-select v-model="editingRule.ruleType" style="width: 100%"><el-option label="最低置信度" value="MIN_CONFIDENCE" /><el-option label="标签合法性" value="LABEL_IN_SCHEMA" /><el-option label="正则校验" value="REGEX" /></el-select></el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="editingRule.threshold" :min="0" :max="1" :step="0.05" /></el-form-item>
        <el-form-item label="处理动作"><el-select v-model="editingRule.action"><el-option label="进入复核" value="REVIEW" /><el-option label="阻止发布" value="BLOCK" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="editingRule.priority" :min="1" :max="999" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="editingRule.description" type="textarea" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editingRule.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="ruleDialog = false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template>
    </el-dialog>
    
    <el-card>
      <template #header>
        <span>标注列表</span>
      </template>
      <el-table :data="annotations" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="rowIndex" label="行号" width="100" />
        <el-table-column prop="labelCode" label="标签" />
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : 'info'">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-select v-model="scope.row.labelCode" placeholder="选择标签" @change="updateAnnotation(scope.row)">
              <el-option v-for="label in labels" :key="label.code" :label="label.name" :value="label.code" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="审核/仲裁" width="210" fixed="right">
          <template #default="scope">
            <el-button v-if="['SUBMITTED','IN_REVIEW'].includes(scope.row.status)" link type="success" @click="review(scope.row, true)">通过</el-button>
            <el-button v-if="['SUBMITTED','IN_REVIEW'].includes(scope.row.status)" link type="warning" @click="review(scope.row, false)">驳回重标</el-button>
            <el-button v-if="scope.row.status === 'REJECTED' || scope.row.status === 'IN_REVIEW'" link type="danger" @click="arbitrate(scope.row)">仲裁</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="task" class="performance-card">
      <template #header><div class="quality-header"><span>标注员绩效评估</span><el-button link type="primary" @click="loadPerformance">刷新</el-button></div></template>
      <el-table :data="performance" size="small">
        <el-table-column prop="annotatorId" label="标注员" width="100" />
        <el-table-column prop="total" label="标注量" />
        <el-table-column prop="approved" label="通过" />
        <el-table-column prop="rejected" label="驳回" />
        <el-table-column label="通过率"><template #default="s">{{ percent(s.row.approvalRate) }}</template></el-table-column>
        <el-table-column label="纠偏率"><template #default="s">{{ percent(s.row.correctionRate) }}</template></el-table-column>
        <el-table-column label="绩效分"><template #default="s"><el-tag :type="s.row.performanceScore >= .8 ? 'success' : 'warning'">{{ percent(s.row.performanceScore) }}</el-tag></template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const datasets = ref([])
const labelSchemas = ref([])
const selectedDataset = ref('')
const selectedSchema = ref('')
const task = ref(null)
const annotations = ref([])
const labels = ref([])
const qualityMetrics = ref(null)
const qualityReport = ref(null)
const performance = ref([])
const rules = ref([])
const ruleDialog = ref(false)
const editingRule = ref({ name: '', code: '', ruleType: 'MIN_CONFIDENCE', threshold: 0.7, action: 'REVIEW', priority: 100, enabled: true, description: '' })
const loading = ref(false)
const qualityPassed = computed(() => Boolean(qualityMetrics.value?.publishable))
const percent = value => value == null ? '-' : `${(Number(value) * 100).toFixed(1)}%`

onMounted(async () => {
  try {
    const data = await request.get('/v1/datasets?page=1&size=20')
    datasets.value = data.records || []
  } catch (e) {
    console.error('加载数据集失败:', e)
    ElMessage.error('加载数据集失败')
  }
  
  try {
    const data = await request.get('/v1/label-schemas?page=1&size=50')
    labelSchemas.value = data.records || []
  } catch (e) {
    console.error('加载标签体系失败:', e)
    labelSchemas.value = []
  }
  await loadRules()
})

watch(selectedSchema, async value => {
  labels.value = value ? await request.get(`/v1/label-schemas/${value}/children`) : []
})

const createTask = async () => {
  if (!selectedDataset.value || !selectedSchema.value) {
    ElMessage.warning('请选择数据集和标签体系')
    return
  }
  try {
    task.value = await request.post('/v1/annotation-tasks', {
      name: '数据标注任务', datasetId: selectedDataset.value, labelSchemaId: selectedSchema.value
    })
    await request.post(`/v1/annotation-tasks/${task.value.id}/pre-annotate`)
    task.value = await request.get(`/v1/annotation-tasks/${task.value.id}`)
    annotations.value = await request.get(`/v1/annotation-tasks/${task.value.id}/annotations`)
    await loadQuality()
    await loadPerformance()
    ElMessage.success('任务创建并完成预标注')
  } catch (e) {
    ElMessage.error('创建标注任务失败：' + (e.message || '未知错误'))
  }
}

const loadQuality = async () => {
  if (!task.value) return
  qualityMetrics.value = await request.get(`/v1/annotation-tasks/${task.value.id}/quality-metrics`)
  Object.assign(task.value, { qualityScore: qualityMetrics.value.qualityScore, passRate: qualityMetrics.value.approveRate, consistencyRate: qualityMetrics.value.consistencyRate || task.value.consistencyRate })
}

const loadPerformance = async () => {
  if (task.value) performance.value = await request.get(`/v1/annotation-tasks/${task.value.id}/annotator-performance`)
}

const loadRules = async () => {
  try { rules.value = await request.get('/v1/annotation-quality-rules') } catch (e) { ElMessage.error('加载质量规则失败：' + e.message) }
}

const openRule = rule => {
  editingRule.value = rule ? { ...rule } : { name: '', code: '', ruleType: 'MIN_CONFIDENCE', threshold: 0.7, action: 'REVIEW', priority: 100, enabled: true, description: '' }
  ruleDialog.value = true
}

const saveRule = async () => {
  try { await request.post('/v1/annotation-quality-rules', editingRule.value); ruleDialog.value = false; await loadRules(); ElMessage.success('质量规则已保存') } catch (e) { ElMessage.error('保存规则失败：' + e.message) }
}

const toggleRule = async rule => {
  try { await request.post('/v1/annotation-quality-rules', rule); ElMessage.success(rule.enabled ? '规则已启用' : '规则已停用') } catch (e) { rule.enabled = !rule.enabled; ElMessage.error('更新规则状态失败：' + e.message) }
}

const removeRule = async rule => {
  try { await ElMessageBox.confirm(`确认删除规则“${rule.name}”吗？`, '删除规则', { type: 'warning' }); await request.delete(`/v1/annotation-quality-rules/${rule.id}`); await loadRules(); ElMessage.success('规则已删除') } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '删除失败') }
}

const autoValidate = async () => {
  loading.value = true
  try { qualityReport.value = await request.post(`/v1/annotation-tasks/${task.value.id}/auto-validate`); await reloadTask(); ElMessage.success(`校验完成，${qualityReport.value.routedToReview} 条进入复核`) } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

const runSampling = async () => {
  loading.value = true
  try { qualityReport.value = await request.post(`/v1/annotation-tasks/${task.value.id}/quality-sampling`, { sampleRate: 0.1 }); await reloadTask(); ElMessage.success('质量抽检完成') } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

const runConsistency = async () => {
  loading.value = true
  try { qualityReport.value = await request.post(`/v1/annotation-tasks/${task.value.id}/consistency-check`); await loadQuality(); ElMessage.success('一致性检查完成') } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

const reloadTask = async () => { task.value = await request.get(`/v1/annotation-tasks/${task.value.id}`); annotations.value = await request.get(`/v1/annotation-tasks/${task.value.id}/annotations`); await loadQuality(); await loadPerformance() }

const review = async (item, approved) => {
  const { value } = await ElMessageBox.prompt(approved ? '请输入审核备注（可选）' : '请输入驳回原因', approved ? '审核通过' : '驳回重标', { inputValidator: v => approved || v ? true : '驳回必须填写原因' })
  await request.post(`/v1/annotations/${item.id}/review`, { approved, reviewComment: value || '' }); await reloadTask(); ElMessage.success(approved ? '审核通过' : '已退回重新标注')
}

const arbitrate = async item => {
  if (!labels.value.length) return ElMessage.warning('当前标签体系没有子标签')
  const { value } = await ElMessageBox.prompt('请输入最终标签编码', '专家仲裁', { inputValue: item.labelCode })
  const label = labels.value.find(l => l.code === value)
  if (!label) return ElMessage.error('标签编码不属于当前标签体系')
  await request.post(`/v1/annotations/${item.id}/arbitrate`, { labelCode: label.code, labelName: label.name, comment: '专家仲裁确认' }); await reloadTask(); ElMessage.success('仲裁完成')
}

const publishTask = async () => { await request.post(`/v1/annotation-tasks/${task.value.id}/publish`); await reloadTask(); ElMessage.success('标注结果已发布') }

const updateAnnotation = async (item) => {
  if (!item.labelCode) return
  try {
    const label = labels.value.find(l => l.code === item.labelCode)
    const updated = await request.post(`/v1/annotations/${item.id}/submit`, {
      labelCode: item.labelCode, labelName: label?.name || item.labelCode
    })
    Object.assign(item, updated)
    ElMessage.success('标注已提交')
  } catch (e) {
    ElMessage.error('提交标注失败：' + (e.message || '未知错误'))
  }
}
</script>

<style scoped>
.annotation h2 {
  margin-bottom: 20px;
}

.quality-card,
.performance-card,
.rules-card {
  margin-bottom: 20px;
}

.quality-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.quality-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.metric-row {
  margin-top: 16px;
}

.metric {
  padding: 16px;
  border: 1px solid var(--river-line);
  border-radius: 8px;
  background: #f8fbfb;
  text-align: center;
}

.metric b,
.metric span {
  display: block;
}

.metric b {
  color: var(--river-brand);
  font-size: 22px;
}

.metric span {
  margin-top: 5px;
  color: var(--river-muted);
  font-size: 13px;
}

.rule-tip {
  margin-top: 12px;
  color: var(--river-muted);
  font-size: 13px;
}
</style>
