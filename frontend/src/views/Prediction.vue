<template>
  <div class="prediction">
    <h2>需求预测</h2>
    
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="5">
        <el-select v-model="selectedDataset" placeholder="选择数据集" @change="onDatasetChange">
          <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-select v-model="targetField" placeholder="目标字段" :disabled="!targetFields.length">
          <el-option v-for="f in targetFields" :key="f" :label="f" :value="f" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-select v-model="timeField" placeholder="时间字段" :disabled="!timeFields.length">
          <el-option v-for="f in timeFields" :key="f" :label="f" :value="f" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-select v-model="modelType" placeholder="预测算法" :disabled="!algorithms.length">
          <el-option v-for="algo in algorithms" :key="algo.code" :label="algo.name" :value="algo.code" />
        </el-select>
      </el-col>
      <el-col :span="3">
        <el-input-number v-model="forecastDays" :min="1" :max="365" placeholder="预测天数" />
      </el-col>
      <el-col :span="4">
        <el-button type="primary" @click="createPrediction">创建预测</el-button>
      </el-col>
    </el-row>
    
    <el-card v-if="task" style="margin-bottom: 20px;">
      <template #header>
        <span>预测任务 - {{ task.name || task.datasetId }}</span>
      </template>
      <el-descriptions :column="4">
        <el-descriptions-item label="任务状态">{{ task.status }}</el-descriptions-item>
        <el-descriptions-item label="目标字段">{{ task.targetField }}</el-descriptions-item>
        <el-descriptions-item label="模型类型">{{ task.modelType }}</el-descriptions-item>
        <el-descriptions-item label="预测天数">{{ task.forecastDays || 30 }}</el-descriptions-item>
      </el-descriptions>
      <el-button v-if="task.status === 'COMPLETED'" type="primary" @click="retrainPrediction" style="margin-top: 20px;">
        重新训练
      </el-button>
    </el-card>

    <el-card v-if="task" class="evaluation-center">
      <template #header><div class="center-header"><span>预测评估中心及模型优化系统</span><el-tag :type="evaluationStatus === 'PASSED' ? 'success' : 'warning'">{{ evaluationStatusText }}</el-tag></div></template>
      <div class="center-actions">
        <el-button @click="evaluateCurrent" :loading="optimizing">记录本次评估</el-button>
        <el-button @click="detectBias" :loading="optimizing">偏差分析</el-button>
        <el-button type="primary" @click="autoTune" :loading="optimizing">自动调优并选优</el-button>
        <el-button type="warning" @click="autoRetrain" :loading="optimizing">偏差触发重训</el-button>
        <el-button @click="loadEvaluationCenter">刷新监控</el-button>
      </div>
      <el-row :gutter="12" class="evaluation-metrics" v-if="evaluation">
        <el-col :span="5"><div class="eval-metric"><b>{{ metric(evaluation.mae) }}</b><span>MAE</span></div></el-col>
        <el-col :span="5"><div class="eval-metric"><b>{{ metric(evaluation.rmse) }}</b><span>RMSE</span></div></el-col>
        <el-col :span="5"><div class="eval-metric"><b>{{ metric(evaluation.mape) }}%</b><span>MAPE</span></div></el-col>
        <el-col :span="5"><div class="eval-metric"><b>{{ percent(evaluation.accuracyScore) }}</b><span>准确率评分</span></div></el-col>
        <el-col :span="4"><div class="eval-metric"><b>{{ evaluation.algorithm || '—' }}</b><span>当前算法</span></div></el-col>
      </el-row>
      <el-alert v-if="biasReport" :title="biasReport.recommendation || '偏差分析完成'" :type="biasReport.biasDetected ? 'warning' : 'success'" :closable="false" show-icon />
      <div v-if="candidateModels.length" class="candidate-box">
        <strong>自动调优候选模型</strong>
        <el-table :data="candidateModels" size="small" stripe>
          <el-table-column prop="algorithm" label="算法" />
          <el-table-column prop="mae" label="MAE" />
          <el-table-column prop="rmse" label="RMSE" />
          <el-table-column prop="mape" label="MAPE" />
        </el-table>
      </div>
      <div ref="evaluationChartRef" class="evaluation-chart"></div>
      <el-table v-if="evaluationHistory.length" :data="evaluationHistory" size="small" stripe>
        <el-table-column prop="createdAt" label="评估时间" width="180" />
        <el-table-column prop="evaluationType" label="类型" />
        <el-table-column prop="algorithm" label="算法" />
        <el-table-column prop="rmse" label="RMSE" />
        <el-table-column prop="mape" label="MAPE" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="recommendation" label="建议" show-overflow-tooltip />
      </el-table>
    </el-card>
    
    <el-card v-if="results.length > 0" style="margin-bottom: 20px;">
      <template #header>
        <span>预测结果</span>
      </template>
      <div ref="resultChartRef" style="height: 400px;"></div>
    </el-card>
    
    <el-card v-if="modelMetrics">
      <template #header>
        <span>模型指标</span>
      </template>
      <el-descriptions :column="4">
        <el-descriptions-item label="MAE">{{ modelMetrics.mae?.toFixed(4) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="RMSE">{{ modelMetrics.rmse?.toFixed(4) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="MAPE">{{ modelMetrics.mape?.toFixed(2) || '—' }}%</el-descriptions-item>
        <el-descriptions-item label="R²">{{ modelMetrics.r2?.toFixed(4) || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 20px;">
        <h4>特征重要性</h4>
        <div ref="importanceChartRef" style="height: 300px;"></div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '../utils/request'

const datasets = ref([])
const selectedDataset = ref('')
const targetField = ref('')
const timeField = ref('')
const targetFields = ref([])
const timeFields = ref([])
const fieldTypes = ref({})
const algorithms = ref([])
const forecastDays = ref(30)
const modelType = ref('AUTO')
const task = ref(null)
const results = ref([])
const modelMetrics = ref(null)
const evaluation = ref(null)
const evaluationHistory = ref([])
const biasReport = ref(null)
const candidateModels = ref([])
const evaluationChartRef = ref(null)
const optimizing = ref(false)
const metric = value => value == null ? '—' : Number(value).toFixed(4)
const percent = value => value == null ? '—' : `${(Number(value) * 100).toFixed(1)}%`
const evaluationStatus = computed(() => evaluation.value?.status || 'PENDING')
const evaluationStatusText = computed(() => ({ PASSED: '效果达标', NEEDS_OPTIMIZATION: '需要优化', OPTIMIZED: '已自动优化', PENDING: '待评估' }[evaluationStatus.value] || evaluationStatus.value))
const resultChartRef = ref(null)
const importanceChartRef = ref(null)

const onDatasetChange = async () => {
  if (!selectedDataset.value) {
    targetFields.value = []
    timeFields.value = []
    fieldTypes.value = {}
    targetField.value = ''
    timeField.value = ''
    return
  }
  try {
    const data = await request.get(`/v1/datasets/${selectedDataset.value}/fields`)
    const rawFields = data.data || data || []
    fieldTypes.value = {}
    const fields = rawFields.map(field => {
      if (typeof field === 'string') return field
      const fieldName = field.fieldName || field.name
      if (fieldName && field.fieldType) fieldTypes.value[fieldName] = field.fieldType
      return fieldName
    }).filter(Boolean)
    const isTimeField = fieldName => {
      const fieldType = String(fieldTypes.value[fieldName] || '').toUpperCase()
      return ['DATE', 'DATETIME', 'TIMESTAMP', 'TIME'].includes(fieldType) ||
        ['date', 'time', '日期', '时间'].some(kw => fieldName.toLowerCase().includes(kw.toLowerCase()))
    }
    const numeric = fields.filter(fieldName => String(fieldTypes.value[fieldName] || '').toUpperCase() === 'NUMERIC')
    targetFields.value = numeric.length ? numeric : fields.filter(fieldName => !isTimeField(fieldName))
    timeFields.value = fields.filter(isTimeField)
    // 对未被识别为日期类型的数据，仍展示字段供用户选择，避免页面被锁死。
    if (!timeFields.value.length) timeFields.value = fields
    targetField.value = ''
    timeField.value = ''
    if (targetFields.value.length > 0) {
      targetField.value = targetFields.value.find(f => !timeFields.value.includes(f)) || targetFields.value[0]
    }
    if (timeFields.value.length > 0) {
      timeField.value = timeFields.value[0]
    }
  } catch (e) {
    console.error('加载字段失败:', e)
  }
}

const loadAlgorithms = async () => {
  try {
    const data = await request.get('/v1/predictions/algorithms')
    const rawAlgorithms = data.data || data || []
    algorithms.value = rawAlgorithms.length ? rawAlgorithms.map(algo => ({
      ...algo,
      code: algo.code || algo.type
    })) : [
      { code: 'AUTO', name: '自动选择' },
      { code: 'LINEAR', name: '线性回归' },
      { code: 'EXPONENTIAL_SMOOTHING', name: '指数平滑' },
      { code: 'HOLT_WINTERS', name: 'Holt-Winters' },
      { code: 'MOVING_AVERAGE', name: '移动平均' }
    ]
  } catch (e) {
    console.error('加载算法列表失败:', e)
    algorithms.value = [
      { code: 'AUTO', name: '自动选择' },
      { code: 'LINEAR', name: '线性回归' },
      { code: 'EXPONENTIAL_SMOOTHING', name: '指数平滑' },
      { code: 'HOLT_WINTERS', name: 'Holt-Winters' },
      { code: 'MOVING_AVERAGE', name: '移动平均' }
    ]
  }
}

onMounted(async () => {
  try {
    const data = await request.get('/v1/datasets?page=1&size=20')
    datasets.value = data.records || data.data?.records || []
  } catch (e) {
    console.error('加载数据集失败:', e)
    ElMessage.error('加载数据集失败')
  }
  await loadAlgorithms()
})

const createPrediction = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  if (!targetField.value) {
    ElMessage.warning('请选择目标字段')
    return
  }
  try {
    const data = await request.post('/v1/predictions', {
      datasetId: selectedDataset.value,
      targetField: targetField.value,
      timeField: timeField.value,
      modelType: modelType.value,
      forecastDays: forecastDays.value
    })
    task.value = data.data || data
    
    if (task.value && task.value.id) {
      await new Promise(resolve => setTimeout(resolve, 500))
      const taskData = await request.get(`/v1/predictions/${task.value.id}`)
      task.value = taskData.data || taskData
      
      const resultsData = await request.get(`/v1/predictions/${task.value.id}/results`)
      results.value = resultsData.data || resultsData || []
      
      if (task.value.modelVersionId) {
        try {
          const model = await request.get(`/v1/predictions/models/${task.value.modelVersionId}`)
          const modelData = model.data || model
          modelMetrics.value = {
            mae: modelData.mae,
            rmse: modelData.rmse,
            mape: modelData.mape,
            r2: modelData.r2
          }
        } catch (e) {
          console.warn('获取模型指标失败:', e)
        }
      }
      await loadEvaluationCenter()
      
      await nextTick()
      renderCharts()
    }
  } catch (e) {
    ElMessage.error('创建预测失败：' + (e.message || '请检查时间字段、目标字段和数据质量'))
  }
}

const loadEvaluationCenter = async () => {
  if (!task.value) return
  try {
    evaluationHistory.value = await request.get(`/v1/predictions/${task.value.id}/evaluation-history`)
    const latest = evaluationHistory.value[0]
    if (latest) evaluation.value = latest
    else if (modelMetrics.value) evaluation.value = { ...modelMetrics.value, algorithm: task.value.modelType }
    await nextTick()
    renderEvaluationChart()
  } catch (e) { console.warn('加载评估中心失败:', e) }
}

const evaluateCurrent = async () => {
  optimizing.value = true
  try { evaluation.value = await request.post(`/v1/predictions/${task.value.id}/evaluate`); await loadEvaluationCenter(); ElMessage.success('评估结果已记录') } catch (e) { ElMessage.error(e.message) } finally { optimizing.value = false }
}

const detectBias = async () => {
  optimizing.value = true
  try { biasReport.value = await request.post(`/v1/predictions/${task.value.id}/bias-detection`); ElMessage.success('偏差分析完成') } catch (e) { ElMessage.error(e.message) } finally { optimizing.value = false }
}

const autoTune = async () => {
  optimizing.value = true
  try { const result = await request.post(`/v1/predictions/${task.value.id}/auto-tune`); candidateModels.value = result.candidates || []; await refreshPrediction(); ElMessage.success(`自动调优完成，已选择 ${result.selectedAlgorithm}`) } catch (e) { ElMessage.error('自动调优失败：' + e.message) } finally { optimizing.value = false }
}

const autoRetrain = async () => {
  optimizing.value = true
  try { await request.post(`/v1/predictions/${task.value.id}/auto-retrain`); ElMessage.success('偏差检测已完成，必要时已启动重训') } catch (e) { ElMessage.error(e.message) } finally { optimizing.value = false }
}

const refreshPrediction = async () => {
  task.value = await request.get(`/v1/predictions/${task.value.id}`)
  results.value = await request.get(`/v1/predictions/${task.value.id}/results`)
  await loadEvaluationCenter()
  await nextTick(); renderCharts()
}

const renderEvaluationChart = () => {
  if (!evaluationChartRef.value || !evaluationHistory.value.length) return
  const chart = echarts.init(evaluationChartRef.value)
  chart.setOption({ tooltip: { trigger: 'axis' }, legend: { data: ['RMSE', 'MAPE'] }, xAxis: { type: 'category', data: evaluationHistory.value.map(e => e.createdAt?.slice(0, 16)) }, yAxis: { type: 'value' }, series: [{ name: 'RMSE', type: 'line', data: evaluationHistory.value.map(e => e.rmse) }, { name: 'MAPE', type: 'line', data: evaluationHistory.value.map(e => e.mape) }] })
}

const renderCharts = () => {
  if (resultChartRef.value && results.value.length > 0) {
    const chart = echarts.init(resultChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { 
        type: 'category', 
        data: results.value.map(r => r.predictionDate || r.date),
        axisLabel: { rotate: 45 }
      },
      yAxis: { type: 'value' },
      series: [
        { type: 'line', name: '预测值', data: results.value.map(r => r.predictedValue || r.value) },
        { type: 'line', name: '下界', data: results.value.map(r => r.lowerBound), lineStyle: { type: 'dashed' } },
        { type: 'line', name: '上界', data: results.value.map(r => r.upperBound), lineStyle: { type: 'dashed' } }
      ]
    })
  }
  
  if (importanceChartRef.value) {
    const chart = echarts.init(importanceChartRef.value)
    const featureNames = modelMetrics.value?.featureImportance?.map(f => f.name) || ['趋势', '天气', '假期', '促销', '价格']
    const importanceValues = modelMetrics.value?.featureImportance?.map(f => f.value) || [5, 15, 20, 25, 35]
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: featureNames },
      series: [{ type: 'bar', data: importanceValues }]
    })
  }
}

const retrainPrediction = async () => {
  if (!task.value) return
  try {
    const data = await request.post(`/v1/predictions/${task.value.id}/retrain`)
    task.value = data.data || data
    ElMessage.success('重新训练成功')
    
    const resultsData = await request.get(`/v1/predictions/${task.value.id}/results`)
    results.value = resultsData.data || resultsData || []
    
    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error('重新训练失败')
  }
}
</script>

<style scoped>
.prediction h2 {
  margin-bottom: 20px;
}

.evaluation-center {
  margin-bottom: 20px;
}

.center-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.center-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.evaluation-metrics {
  margin-bottom: 16px;
}

.eval-metric {
  min-height: 74px;
  padding: 12px 10px;
  border: 1px solid var(--river-line);
  border-radius: 8px;
  background: #f8fbfb;
  text-align: center;
}

.eval-metric b,
.eval-metric span {
  display: block;
}

.eval-metric b {
  overflow: hidden;
  color: var(--river-brand);
  font-size: 19px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.eval-metric span {
  margin-top: 5px;
  color: var(--river-muted);
  font-size: 12px;
}

.candidate-box {
  margin: 16px 0;
}

.evaluation-chart {
  height: 240px;
  margin: 16px 0;
}
</style>
