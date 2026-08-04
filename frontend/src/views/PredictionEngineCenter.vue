<template>
  <div class="engine-page page-container">
    <header class="hero">
      <div>
        <div class="eyebrow">MARKET DEMAND FORECASTING ENGINE</div>
        <h1>市场需求预测引擎</h1>
        <p>通过机器学习与深度学习完成市场需求预测、模型评估、A/B 对比和线上版本切换。</p>
      </div>
      <div class="hero-actions">
        <el-button @click="router.push('/prediction-engine/config')">需求预测引擎模块及模型管理后台</el-button>
        <el-button type="primary" @click="openCreate">新建预测任务</el-button>
      </div>
    </header>

    <section class="status-grid">
      <div class="status-card"><span>预测任务</span><strong>{{ tasks.length }}</strong><small>已提交到引擎的任务</small></div>
      <div class="status-card"><span>线上模型</span><strong>{{ productionModels.length }}</strong><small>当前可用于生产预测</small></div>
      <div class="status-card"><span>训练引擎</span><strong>{{ frameworkStatus.pythonEngineReachable ? '在线' : '待连接' }}</strong><small>TensorFlow / PyTorch 运行状态</small></div>
      <div class="status-card"><span>当前任务</span><strong>{{ selectedTask ? statusText(selectedTask.status) : '未选择' }}</strong><small>选择任务查看完整结果</small></div>
    </section>

    <div class="workspace">
      <aside class="task-rail panel">
        <div class="panel-title"><div><b>预测任务</b><small>选择任务查看训练和预测结果</small></div><el-button link :loading="loading" @click="loadAll">刷新</el-button></div>
        <el-input v-model="keyword" clearable placeholder="搜索任务名称" />
        <div class="task-list">
          <button v-for="taskItem in filteredTasks" :key="taskItem.id" :class="['task-row', { active: taskItem.id === selectedId }]" @click="selectTask(taskItem)">
            <span class="task-main"><b>{{ taskItem.name || `预测任务 ${taskItem.id}` }}</b><small>{{ taskItem.modelType || taskItem.taskType || 'AUTO' }} · {{ taskItem.targetField || '未设置目标字段' }}</small></span>
            <el-tag size="small" :type="statusType(taskItem.status)">{{ statusText(taskItem.status) }}</el-tag>
          </button>
          <el-empty v-if="!filteredTasks.length" description="暂无预测任务" :image-size="72" />
        </div>
      </aside>

      <main class="main-column">
        <section class="panel workflow-panel">
          <div class="panel-title"><div><b>预测工作台</b><small>从数据配置到模型结果的完整业务闭环</small></div><el-tag v-if="selectedTask" :type="statusType(selectedTask.status)">{{ statusText(selectedTask.status) }}</el-tag></div>
          <div class="steps"><div v-for="(step, index) in steps" :key="step" :class="['step', { active: activeStep >= index, current: activeStep === index }]" @click="activeStep = index"><i>{{ index + 1 }}</i><span>{{ step }}</span></div></div>

          <div v-if="activeStep === 0" class="config-view">
            <div v-if="!selectedTask" class="empty-start"><div class="empty-icon">01</div><h2>先创建一个预测任务</h2><p>选择市场数据、目标字段、时间字段和一个或多个预测模型，系统将真实调用后端预测引擎。</p><el-button type="primary" @click="openCreate">开始配置</el-button></div>
            <div v-else class="task-summary">
              <div class="summary-title"><div><span class="eyebrow">SELECTED TASK</span><h2>{{ selectedTask.name || `预测任务 ${selectedTask.id}` }}</h2></div><el-button type="primary" @click="openCreate">新建任务</el-button></div>
              <el-descriptions :column="3" border><el-descriptions-item label="数据集">{{ selectedTask.datasetId }}</el-descriptions-item><el-descriptions-item label="目标字段">{{ selectedTask.targetField }}</el-descriptions-item><el-descriptions-item label="时间字段">{{ selectedTask.timeField || '未设置' }}</el-descriptions-item><el-descriptions-item label="模型类型">{{ selectedTask.modelType || selectedTask.taskType }}</el-descriptions-item><el-descriptions-item label="预测周期">{{ selectedTask.forecastDays || 30 }} 天</el-descriptions-item><el-descriptions-item label="模型版本">{{ selectedTask.modelVersionId || '生成后关联' }}</el-descriptions-item></el-descriptions>
            </div>
          </div>

          <div v-else-if="activeStep === 1" class="result-view">
            <div v-if="!selectedTask" class="empty-start"><h2>暂无可训练任务</h2><p>请先创建预测任务。</p></div>
            <template v-else>
              <el-alert v-if="selectedTask.status === 'FAILED' && taskError" :title="taskError" type="error" show-icon :closable="false" class="task-error" />
              <div class="result-head"><div><h2>真实值与预测值</h2><p>展示当前任务输出的预测结果，异常偏差由后端结果标记提供。</p></div><el-button :loading="refreshing" @click="refreshTask">刷新结果</el-button></div>
              <div ref="resultChartRef" class="chart"></div>
              <div class="metric-grid"><div><span>MAE</span><b>{{ formatMetric(metrics.mae) }}</b></div><div><span>MAPE</span><b>{{ formatMetric(metrics.mape) }}%</b></div><div><span>准确率</span><b>{{ accuracy(metrics) }}</b></div><div><span>RMSE</span><b>{{ formatMetric(metrics.rmse) }}</b></div></div>
              <el-alert v-if="biasReport" :title="biasReport.recommendation || '偏差分析完成'" :type="biasReport.biasDetected ? 'warning' : 'success'" show-icon :closable="false" />
            </template>
          </div>

          <div v-else-if="activeStep === 2" class="training-view">
            <div class="result-head"><div><h2>模型训练与运行日志</h2><p>训练进度、Loss 曲线和迭代日志来自后端任务状态，不使用前端模拟进度。</p></div><el-button :disabled="!selectedTask" :loading="training" type="primary" @click="startRetrain">重新训练当前任务</el-button></div>
            <div class="training-status"><div><span>当前引擎</span><b>{{ trainingEngine }}</b></div><div><span>任务状态</span><b>{{ selectedTask ? statusText(selectedTask.status) : '未选择' }}</b></div><div><span>进度</span><b>{{ trainingProgress }}%</b></div></div>
            <el-progress :percentage="trainingProgress" :status="trainingStatus === 'FAILED' ? 'exception' : undefined" :stroke-width="10" />
            <div class="log-box"><div v-for="(line, i) in trainingLogs" :key="i">{{ line }}</div><span v-if="!trainingLogs.length">等待后端训练任务日志...</span></div>
            <div ref="lossChartRef" class="loss-chart"></div>
          </div>

          <div v-else class="ab-view">
            <div class="result-head"><div><h2>A/B 测试与模型择优</h2><p>选择两个已生成的模型，后端按 MAE、RMSE、MAPE 和拟合效果进行比较。</p></div><el-button :disabled="models.length < 2" :loading="comparing" type="primary" @click="compareModels">开始 A/B 测试</el-button></div>
            <div class="compare-select"><el-select v-model="championId" filterable placeholder="选择 Champion 模型"><el-option v-for="model in models" :key="`c-${model.id}`" :label="modelLabel(model)" :value="model.id" /></el-select><span>VS</span><el-select v-model="challengerId" filterable placeholder="选择 Challenger 模型"><el-option v-for="model in models" :key="`d-${model.id}`" :label="modelLabel(model)" :value="model.id" /></el-select></div>
            <el-alert v-if="comparison" :title="comparison.message || 'A/B 测试完成'" :type="comparison.recommendedModelId ? 'success' : 'info'" show-icon :closable="false" />
            <el-table v-if="comparison" :data="comparisonRows" stripe><el-table-column prop="role" label="角色" width="120"/><el-table-column prop="algorithm" label="算法"/><el-table-column prop="mae" label="MAE"/><el-table-column prop="rmse" label="RMSE"/><el-table-column prop="mape" label="MAPE"/><el-table-column prop="recommendation" label="结论"/></el-table>
            <el-empty v-else description="选择两个模型开始对比" :image-size="80" />
          </div>
        </section>

        <section class="panel version-panel">
          <div class="panel-title"><div><b>线上模型版本</b><small>主页面提供快速查看、上线和切换，完整生命周期管理在后台</small></div><el-button link @click="router.push('/prediction-engine/models')">进入完整管理</el-button></div>
          <el-table :data="models.slice(0, 5)" stripe size="small" empty-text="暂无模型版本"><el-table-column prop="modelName" label="模型" min-width="150"/><el-table-column prop="algorithmType" label="算法" min-width="120"/><el-table-column prop="versionNumber" label="版本" width="80"/><el-table-column prop="mape" label="MAPE" width="90"/><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.isProduction ? 'success' : 'info'">{{ row.isProduction ? '线上' : (row.status || '未发布') }}</el-tag></template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" :disabled="row.isProduction" @click="setProduction(row)">设为线上</el-button></template></el-table-column></el-table>
        </section>
      </main>
    </div>

    <el-dialog v-model="createVisible" title="新建预测任务" width="760px" destroy-on-close>
      <el-form :model="form" label-position="top"><el-row :gutter="16"><el-col :span="12"><el-form-item label="预测任务名称"><el-input v-model="form.name" placeholder="例如：华东区域下月需求预测"/></el-form-item></el-col><el-col :span="12"><el-form-item label="市场数据集"><el-select v-model="form.datasetId" filterable placeholder="选择数据集" style="width:100%" @change="loadFields"><el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="预测目标字段"><el-select v-model="form.targetField" :disabled="!fields.length" style="width:100%"><el-option v-for="field in targetFields" :key="field" :label="field" :value="field"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="时间字段"><el-select v-model="form.timeField" :disabled="!fields.length" style="width:100%"><el-option v-for="field in timeFields" :key="field" :label="field" :value="field"/></el-select></el-form-item></el-col></el-row>
        <el-form-item label="预测模型（可多选并行执行）"><div class="model-choice-grid"><label v-for="item in modelTypes" :key="item.key" :class="['model-choice', { selected: form.modelTypes.includes(item.key), disabled: !item.available.length }]" @click="item.available.length && toggleModel(item.key)"><span class="choice-check">{{ form.modelTypes.includes(item.key) ? '✓' : '' }}</span><span><b>{{ item.label }}</b><small>{{ item.description }}</small><em>{{ item.available.length ? item.available.map(a => a.name).join('、') : '后端未启用' }}</em></span></label></div></el-form-item>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="训练框架"><el-radio-group v-model="form.framework"><el-radio-button label="TensorFlow"/><el-radio-button label="PyTorch"/></el-radio-group></el-form-item></el-col><el-col :span="8"><el-form-item label="预测天数"><el-input-number v-model="form.forecastDays" :min="1" :max="365"/></el-form-item></el-col><el-col :span="8"><el-form-item label="滑动窗口"><el-input-number v-model="form.windowSize" :min="1" :max="365"/></el-form-item></el-col></el-row></el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="creating" @click="createTasks">创建并执行预测</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '../utils/request'
import { getActiveDatasetId, setActiveDatasetId } from '../utils/workspaceSync'

const router = useRouter()
const tasks = ref([]); const models = ref([]); const datasets = ref([]); const algorithms = ref([]); const selectedId = ref(null); const selectedTask = ref(null); const keyword = ref(''); const loading = ref(false); const refreshing = ref(false); const creating = ref(false); const training = ref(false); const createVisible = ref(false); const activeStep = ref(0); const fields = ref([]); const fieldTypes = ref({}); const results = ref([]); const metrics = reactive({}); const biasReport = ref(null); const frameworkStatus = reactive({ pythonEngineReachable: false }); const trainingProgress = ref(0); const trainingStatus = ref('PENDING'); const trainingLogs = ref([]); const trainingEngine = ref('未选择'); const championId = ref(null); const challengerId = ref(null); const comparison = ref(null); const comparing = ref(false); const resultChartRef = ref(null); const lossChartRef = ref(null); let progressTimer
const steps = ['任务配置', '预测结果', '训练监控', 'A/B 测试']
const form = reactive({ name: '', datasetId: '', targetField: '', timeField: '', modelTypes: [], framework: 'PyTorch', forecastDays: 30, windowSize: 7 })
const modelTypes = computed(() => [
  { key: 'TIME_SERIES', label: '时间序列分析', description: '趋势预测、周期预测', available: algorithms.value.filter(a => ['TIME_SERIES', 'TIME_SERIES_ANALYSIS'].includes(String(a.family || a.algorithmFamily || a.taskType || '').toUpperCase()) || /ARIMA|HOLT|SMOOTH|MOVING|TIME|TREND/i.test(a.code || a.type || '')) },
  { key: 'REGRESSION', label: '回归预测', description: '数值拟合与需求量预测', available: algorithms.value.filter(a => /REGRESSION|LINEAR|EXPONENTIAL/i.test(a.code || a.type || '') && !/CLASSIFIER/i.test(a.code || a.type || '')) },
  { key: 'CLASSIFICATION', label: '分类预测', description: '场景、等级和需求类别预测', available: algorithms.value.filter(a => /CLASSIFICATION|CLASSIFIER/i.test(a.code || a.type || '')) },
  { key: 'SEQUENCE', label: '深度序列预测', description: 'LSTM、Transformer 等深度学习时序模型', available: algorithms.value.filter(a => /LSTM|TRANSFORMER|SEQUENCE|MLP/i.test(a.code || a.type || '')) }
])
const filteredTasks = computed(() => tasks.value.filter(t => !keyword.value || String(t.name || '').toLowerCase().includes(keyword.value.toLowerCase())))
const productionModels = computed(() => models.value.filter(m => m.isProduction))
const comparisonRows = computed(() => { if (!comparison.value) return []; const list = comparison.value.models || comparison.value.candidates || []; return list.map((m, i) => ({ role: m.id === comparison.value.recommendedModelId ? '推荐模型' : i === 0 ? 'Champion' : 'Challenger', algorithm: m.algorithm || m.algorithmType, mae: m.mae, rmse: m.rmse, mape: m.mape, recommendation: m.id === comparison.value.recommendedModelId ? '自动择优' : '' })) })
const taskError = computed(() => selectedTask.value?.errorMessage || parseJson(selectedTask.value?.parametersJson).errorMessage || '')

const statusText = s => ({ COMPLETED: '已完成', RUNNING: '运行中', PENDING: '待运行', FAILED: '失败', PROCESSING: '处理中' }[s] || s || '待运行')
const statusType = s => ({ COMPLETED: 'success', RUNNING: 'warning', PENDING: 'info', FAILED: 'danger', PROCESSING: 'warning' }[s] || 'info')
const formatMetric = v => v == null || Number.isNaN(Number(v)) ? '—' : Number(v).toFixed(4)
const accuracy = m => m.accuracyScore != null ? `${(Number(m.accuracyScore) * 100).toFixed(1)}%` : m.mape != null ? `${Math.max(0, 100 - Number(m.mape)).toFixed(1)}%` : '—'
const modelLabel = m => `${m.modelName || '模型'} · ${m.algorithmType || m.modelType || '未知'} v${m.versionNumber || 1}`
const openCreate = () => { createVisible.value = true; if (!datasets.value.length) loadDatasets() }
const toggleModel = key => { const i = form.modelTypes.indexOf(key); i >= 0 ? form.modelTypes.splice(i, 1) : form.modelTypes.push(key) }

async function loadDatasets() {
  try {
    const res = await request.get('/v1/datasets?page=1&size=100')
    datasets.value = res.records || res.data?.records || []
    const activeId = getActiveDatasetId()
    const preferred = datasets.value.find(d => String(d.id) === String(activeId) && d.status === 'PARSED') || datasets.value.find(d => d.status === 'PARSED')
    if (preferred) {
      if (!form.datasetId || !datasets.value.some(d => String(d.id) === String(form.datasetId))) form.datasetId = preferred.id
      setActiveDatasetId(form.datasetId)
      await loadFields()
    }
  } catch (e) { ElMessage.error('数据集加载失败') }
}
async function loadAlgorithms() {
  try {
    // Load classical and Python-engine algorithms together. The deep-learning
    // endpoint is separate; omitting it made the UI report a false "disabled" state.
    const [classicalRes, deepRes] = await Promise.all([
      request.get('/v1/predictions/algorithms'),
      request.get('/v1/predictions/deep-learning/algorithms')
    ])
    const classical = (classicalRes?.data || classicalRes || []).map(a => ({ ...a, code: a.code || a.type }))
    const deep = (deepRes?.data || deepRes || []).map(a => ({
      ...a,
      code: a.code || a.type,
      family: String(a.family || a.type || '').toUpperCase(),
      tasks: a.tasks || a.supportedTasks || []
    }))
    const merged = [...classical, ...deep]
    algorithms.value = merged.filter((item, index, list) => list.findIndex(candidate => String(candidate.code).toLowerCase() === String(item.code).toLowerCase()) === index)
    if (!form.modelTypes.length) form.modelTypes = modelTypes.value.filter(item => item.available.length).slice(0, 1).map(item => item.key)
  } catch (e) {
    ElMessage.error('预测模型列表加载失败')
  }
}
async function loadFields() { if (!form.datasetId) return; try { setActiveDatasetId(form.datasetId); const res = await request.get(`/v1/datasets/${form.datasetId}/fields`); const raw = res.data || res || []; fields.value = raw.map(f => typeof f === 'string' ? f : f.fieldName || f.name).filter(Boolean); fieldTypes.value = Object.fromEntries(raw.filter(f => typeof f !== 'string' && (f.fieldName || f.name)).map(f => [f.fieldName || f.name, f.fieldType])); const isTime = f => /DATE|TIME|TIMESTAMP/i.test(fieldTypes.value[f] || '') || /日期|时间|date|time/i.test(f); const isNumeric = f => /INT|DECIMAL|DOUBLE|FLOAT|NUMBER|NUMERIC|LONG/i.test(String(fieldTypes.value[f] || '')) || /demand|sales|amount|quantity|volume|value|销量|销售额|数量|金额/i.test(f); timeFields.value = fields.value.filter(isTime); if (!timeFields.value.length) timeFields.value = fields.value; targetFields.value = fields.value.filter(f => !isTime(f)); const preferredTarget = targetFields.value.find(f => /demand|sales|销量|销售额/i.test(f)) || targetFields.value.find(isNumeric) || targetFields.value[0] || fields.value[0] || ''; form.timeField = timeFields.value[0] || ''; form.targetField = preferredTarget } catch (e) { ElMessage.error('字段加载失败') } }
const targetFields = ref([]); const timeFields = ref([])
async function createTasks() { if (!form.datasetId || !form.targetField) return ElMessage.warning('请选择数据集和目标字段'); if (!form.timeField) return ElMessage.warning('请选择时间字段'); if (form.targetField === form.timeField) return ElMessage.warning('时间字段和目标字段不能是同一列'); if (!form.modelTypes.length) return ElMessage.warning('至少选择一个预测模型'); const numericTarget = /INT|DECIMAL|DOUBLE|FLOAT|NUMBER|NUMERIC/i.test(String(fieldTypes.value[form.targetField] || '')); if (form.modelTypes.some(type => ['TIME_SERIES','REGRESSION','SEQUENCE'].includes(type)) && !numericTarget) return ElMessage.warning('时间序列、回归和深度序列预测的目标字段必须是数值字段'); creating.value = true; try { let first; for (const type of form.modelTypes) { const candidate = modelTypes.value.find(m => m.key === type)?.available[0]; const res = await request.post('/v1/predictions', { name: form.name || `${type}需求预测`, datasetId: form.datasetId, targetField: form.targetField, timeField: form.timeField, modelType: candidate?.code || type, taskType: type, forecastDays: form.forecastDays, windowSize: form.windowSize, parametersJson: JSON.stringify({ framework: form.framework, modelFamily: type, parallelGroup: form.name || type }) }); if (!first) first = res.data || res } if (first) { createVisible.value = false; await loadAll(); selectTask(first); activeStep.value = 1; ElMessage.success(`已提交 ${form.modelTypes.length} 个模型预测任务`) } } catch (e) { ElMessage.error(e.message || '预测任务创建失败') } finally { creating.value = false } }
async function loadAll() { loading.value = true; try { const [taskRes, modelRes, statusRes] = await Promise.all([request.get('/v1/predictions?page=1&size=100'), request.get('/v1/predictions/models'), request.get('/v1/predictions/deep-learning/status')]); tasks.value = taskRes.records || []; models.value = modelRes || []; const reachable = Boolean(statusRes?.reachable); Object.assign(frameworkStatus, statusRes || {}, { pythonEngineReachable: reachable }); const activeId = getActiveDatasetId(); const scopedTasks = activeId ? tasks.value.filter(t => String(t.datasetId) === String(activeId)) : []; if (selectedId.value) { const found = tasks.value.find(t => t.id === selectedId.value); if (found) await selectTask(found) } else if (tasks.value.length) { const pool = scopedTasks.length ? scopedTasks : tasks.value; const usable = pool.find(t => ['COMPLETED', 'RUNNING', 'PROCESSING'].includes(String(t.status || '').toUpperCase())); await selectTask(usable || pool[0]) } } catch (e) { ElMessage.error(e.message || '预测引擎数据加载失败') } finally { loading.value = false } }
async function selectTask(item) { selectedId.value = item.id; selectedTask.value = item; trainingEngine.value = item.parametersJson ? parseJson(item.parametersJson).framework || '本地算法' : '本地算法'; await refreshTask(); loadTrainingStatus() }
const parseJson = value => { try { return typeof value === 'string' ? JSON.parse(value) : value || {} } catch { return {} } }
async function refreshTask() { if (!selectedTask.value) return; refreshing.value = true; try { const [taskRes, resultRes, metricRes] = await Promise.all([request.get(`/v1/predictions/${selectedTask.value.id}`), request.get(`/v1/predictions/${selectedTask.value.id}/results`), request.get(`/v1/predictions/${selectedTask.value.id}/metrics`)]); selectedTask.value = taskRes; results.value = resultRes || []; Object.assign(metrics, metricRes || {}); await nextTick(); renderResultChart() } catch (e) { ElMessage.error(e.message || '预测结果加载失败') } finally { refreshing.value = false } }
async function loadTrainingStatus() { if (!selectedTask.value) return; try { const res = await request.get(`/v1/predictions/deep-learning/train/${selectedTask.value.id}`); trainingProgress.value = Number(res.progress || 0); trainingStatus.value = res.status || 'PENDING'; trainingLogs.value = res.logs || [] } catch { trainingProgress.value = 0 } }
async function startRetrain() { if (!selectedTask.value) return; training.value = true; try { await request.post(`/v1/predictions/${selectedTask.value.id}/retrain`); activeStep.value = 2; await loadTrainingStatus(); progressTimer = window.setInterval(loadTrainingStatus, 1500); ElMessage.success('重训练任务已提交') } catch (e) { ElMessage.error(e.message || '重训练失败') } finally { training.value = false } }
async function compareModels() { if (!championId.value || !challengerId.value || championId.value === challengerId.value) return ElMessage.warning('请选择两个不同模型'); comparing.value = true; try { comparison.value = await request.post('/v1/predictions/models/compare', { modelId1: championId.value, modelId2: challengerId.value }) } catch (e) { ElMessage.error(e.message || 'A/B 测试失败') } finally { comparing.value = false } }
async function setProduction(model) { try { await request.post(`/v1/predictions/models/${model.id}/set-production`); ElMessage.success('模型已切换为线上版本'); await loadAll() } catch (e) { ElMessage.error(e.message || '模型发布失败') } }
function renderResultChart() { if (!resultChartRef.value) return; const chart = echarts.init(resultChartRef.value); const rows = results.value; chart.setOption({ grid: { left: 45, right: 20, top: 25, bottom: 35 }, tooltip: { trigger: 'axis' }, legend: { data: ['实际值', '预测值', '下界', '上界'] }, xAxis: { type: 'category', data: rows.map(r => r.predictionDate || r.date || r.time || '') }, yAxis: { type: 'value' }, series: [{ name: '实际值', type: 'line', data: rows.map(r => r.actualValue ?? r.actual ?? null) }, { name: '预测值', type: 'line', data: rows.map(r => r.predictedValue ?? r.predicted ?? r.value ?? null) }, { name: '下界', type: 'line', data: rows.map(r => r.lowerBound ?? null), lineStyle: { type: 'dashed' } }, { name: '上界', type: 'line', data: rows.map(r => r.upperBound ?? null), lineStyle: { type: 'dashed' } }] }) }
onMounted(async () => { await Promise.all([loadDatasets(), loadAlgorithms()]); await loadAll() })
onBeforeUnmount(() => { if (progressTimer) clearInterval(progressTimer) })
</script>

<style scoped>
.engine-page{color:#1d2129;min-width:0;overflow-x:hidden}.hero{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:18px}.hero h1{margin:6px 0 8px;font-size:26px}.hero p{margin:0;color:#86909c;font-size:14px}.eyebrow{color:#165dff;font-size:12px;font-weight:700;letter-spacing:.08em}.hero-actions{display:flex;gap:10px;flex-shrink:0}.status-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}.status-card,.panel{background:#fff;border:1px solid #e5e6eb;border-radius:8px;box-shadow:0 1px 4px rgba(0,0,0,.04)}.status-card{padding:16px 18px}.status-card span,.status-card small{display:block;color:#86909c;font-size:12px}.status-card strong{display:block;margin:7px 0 2px;font-size:24px}.workspace{display:grid;grid-template-columns:270px minmax(0,1fr);gap:16px;align-items:start;min-width:0}.task-rail{min-height:760px;padding:16px;min-width:0}.panel{padding:18px;min-width:0}.panel-title,.result-head,.summary-title{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0}.panel-title>div,.result-head>div,.summary-title>div{min-width:0}.panel-title b{font-size:16px}.panel-title small{display:block;margin-top:5px;color:#86909c;font-size:12px}.task-rail>.el-input{margin:14px 0}.task-list{display:grid;gap:2px}.task-row{width:100%;display:flex;align-items:center;gap:8px;padding:11px 6px;border:0;border-left:3px solid transparent;background:#fff;text-align:left;cursor:pointer;border-radius:4px;min-width:0}.task-row:hover,.task-row.active{background:#e8f3ff;border-left-color:#165dff}.task-main{min-width:0;flex:1}.task-main b,.task-main small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.task-main b{font-size:13px}.task-main small{margin-top:4px;color:#86909c;font-size:11px}.main-column{display:grid;gap:16px;min-width:0}.workflow-panel{min-height:570px;overflow:hidden}.steps{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0;margin:18px 0 22px;min-width:0}.step{position:relative;display:flex;align-items:center;gap:8px;color:#c9cdd4;font-size:13px;min-width:0}.step span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.step:not(:last-child):after{content:'';position:absolute;left:31px;right:0;top:14px;height:1px;background:#e5e6eb;z-index:0}.step i,.step span{position:relative;z-index:1}.step i{width:28px;height:28px;flex:0 0 28px;border:1px solid #c9cdd4;border-radius:50%;display:grid;place-items:center;background:#fff;font-style:normal}.step.active{color:#165dff}.step.active i{border-color:#165dff;background:#e8f3ff}.step.current span{font-weight:600}.empty-start{text-align:center;padding:72px 20px}.empty-icon{margin:auto;width:52px;height:52px;display:grid;place-items:center;background:#e8f3ff;color:#165dff;border-radius:8px;font-weight:700}.empty-start h2{margin:15px 0 8px;font-size:20px}.empty-start p{max-width:500px;margin:0 auto 20px;color:#86909c}.summary-title h2,.result-head h2{margin:4px 0;font-size:19px;overflow-wrap:anywhere}.summary-title{margin-bottom:18px}.chart{height:310px;margin-top:18px;max-width:100%}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-top:16px}.metric-grid div{padding:12px;background:#f7f8fa;border-radius:6px;min-width:0}.metric-grid span,.metric-grid b{display:block}.metric-grid span{color:#86909c;font-size:12px}.metric-grid b{margin-top:6px;font-size:19px;overflow-wrap:anywhere}.training-status{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:20px 0 14px}.training-status div{padding:14px;background:#f7f8fa;border-radius:6px;min-width:0}.training-status span,.training-status b{display:block}.training-status span{color:#86909c;font-size:12px}.training-status b{margin-top:6px;overflow-wrap:anywhere}.log-box{height:120px;overflow:auto;margin-top:16px;padding:12px;background:#1d2129;color:#d9e1f2;border-radius:6px;font:12px/1.8 monospace}.loss-chart{height:160px;margin-top:14px}.compare-select{display:flex;align-items:center;gap:12px;margin:20px 0;min-width:0}.compare-select .el-select{width:280px;max-width:100%}.version-panel{padding-bottom:10px;overflow:hidden}.model-choice-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.model-choice{display:flex;gap:10px;padding:13px;border:1px solid #e5e6eb;border-radius:6px;cursor:pointer;min-width:0}.model-choice:hover,.model-choice.selected{border-color:#165dff;background:#e8f3ff}.model-choice.disabled{opacity:.5;cursor:not-allowed}.choice-check{width:18px;height:18px;flex:0 0 18px;border:1px solid #c9cdd4;border-radius:4px;color:#fff;background:#fff;text-align:center;line-height:18px}.selected .choice-check{background:#165dff;border-color:#165dff}.model-choice b,.model-choice small,.model-choice em{display:block;overflow-wrap:anywhere}.model-choice small{margin-top:4px;color:#4e5969;font-size:12px}.model-choice em{margin-top:6px;color:#86909c;font-size:11px;font-style:normal}@media(max-width:1280px){.workspace{grid-template-columns:1fr}.task-rail{min-height:auto}.task-list{max-height:320px;overflow:auto}.status-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:700px){.hero{flex-direction:column}.hero-actions{width:100%;flex-wrap:wrap}.status-grid,.model-choice-grid,.metric-grid{grid-template-columns:1fr}.compare-select{align-items:stretch;flex-direction:column}.compare-select .el-select{width:100%}.steps{overflow:auto}.step{min-width:120px}}
/* The configuration step only contains a compact task summary. Let the
   workspace size follow its active step instead of reserving a large blank
   area above the model versions table. */
.workflow-panel{min-height:0}.config-view{padding-bottom:2px}.task-summary{padding-bottom:0}
</style>
