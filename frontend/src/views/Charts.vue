<template>
  <div class="charts">
    <h2>图表报告</h2>
    
    <div class="chart-toolbar">
      <div class="toolbar-field dataset-field">
        <el-select v-model="selectedDataset" placeholder="选择数据集" @change="onDatasetChange">
          <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
        </el-select>
      </div>
      <div class="toolbar-field chart-type-field">
        <el-select v-model="chartType" placeholder="图表类型">
          <el-option label="折线图" value="LINE" />
          <el-option label="柱状图" value="BAR" />
          <el-option label="饼图" value="PIE" />
          <el-option label="散点图" value="SCATTER" />
        </el-select>
      </div>
      <div class="toolbar-field axis-field">
        <el-select v-model="xAxisField" placeholder="X轴字段" :disabled="!fields.length">
          <el-option v-for="f in fields" :key="f" :label="f" :value="f" />
        </el-select>
      </div>
      <div class="toolbar-field axis-field">
        <el-select v-model="yAxisField" placeholder="Y轴字段" :disabled="!fields.length">
          <el-option v-for="f in numericFields" :key="f" :label="f" :value="f" />
        </el-select>
      </div>
      <div class="toolbar-actions">
          <el-button type="primary" :loading="recommendLoading" @click="recommendCharts">推荐并生成</el-button>
        <el-button @click="generateChart">重新生成</el-button>
        <el-button @click="generateReport">生成报告</el-button>
      </div>
    </div>

    <el-card v-if="recommendations.length > 0" style="margin-bottom: 20px;">
      <template #header>
        <div class="recommend-header"><span>图表推荐</span><small>后端根据字段类型和数据结构给出可用图表及坐标建议</small></div>
      </template>
      <el-table :data="recommendations" style="width: 100%">
        <el-table-column prop="chartType" label="类型" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="recommendedXField" label="推荐X轴" />
        <el-table-column prop="recommendedYField" label="推荐Y轴" />
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" @click="selectRecommendation(scope.row)">使用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <el-card v-if="chartData">
      <template #header>
        <span>{{ chartData.title }}</span>
      </template>
      <div ref="chartRef" style="height: 400px;"></div>
    </el-card>
    
    <el-card v-if="report" style="margin-top: 20px;">
      <template #header>
        <span>分析报告</span>
      </template>
      <pre>{{ report.contentJson || report.content }}</pre>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '../utils/request'
import { getActiveDatasetId, onDatasetSync } from '../utils/workspaceSync'

const datasets = ref([])
const selectedDataset = ref('')
const chartType = ref('LINE')
const xAxisField = ref('')
const yAxisField = ref('')
const fields = ref([])
const fieldTypes = ref({})
const recommendations = ref([])
const recommendLoading = ref(false)
const chartData = ref(null)
const report = ref(null)
const chartRef = ref(null)
let chartInstance = null
let recommendationRequestId = 0
const CHART_STATE_KEY = 'river-charts-state'
const readChartState = () => {
  try { return JSON.parse(localStorage.getItem(CHART_STATE_KEY) || '{}') } catch (_) { return {} }
}
const persistChartState = () => {
  if (!selectedDataset.value) return
  localStorage.setItem(CHART_STATE_KEY, JSON.stringify({ selectedDataset: selectedDataset.value, chartType: chartType.value, xAxisField: xAxisField.value, yAxisField: yAxisField.value, recommendations: recommendations.value, chartData: chartData.value, report: report.value }))
}

const numericFields = computed(() => {
  return fields.value.filter(fieldName => {
    const fieldType = String(fieldTypes.value[fieldName] || '').toUpperCase()
    if (fieldType) return ['NUMERIC', 'NUMBER', 'INTEGER', 'DECIMAL', 'DOUBLE', 'FLOAT', 'LONG'].includes(fieldType)
    return !['date', 'time', '日期', '时间'].some(kw => fieldName.toLowerCase().includes(kw.toLowerCase()))
  })
})

const onDatasetChange = async () => {
  if (!selectedDataset.value) {
    fields.value = []
    fieldTypes.value = {}
    xAxisField.value = ''
    yAxisField.value = ''
    return
  }
  try {
    const saved = readChartState()
    const restoring = String(saved.selectedDataset) === String(selectedDataset.value)
    const data = await request.get(`/v1/datasets/${selectedDataset.value}/fields`)
    const rawFields = data.data || data || []
    fieldTypes.value = {}
    fields.value = rawFields.map(field => {
      if (typeof field === 'string') return field
      const fieldName = field.fieldName || field.name
      if (fieldName && field.fieldType) fieldTypes.value[fieldName] = field.fieldType
      return fieldName
    }).filter(Boolean)
    if (fields.value.length > 0) {
      xAxisField.value = fields.value[0]
      yAxisField.value = numericFields.value[0] || ''
    }
    if (restoring) {
      chartType.value = saved.chartType || chartType.value
      xAxisField.value = fields.value.includes(saved.xAxisField) ? saved.xAxisField : xAxisField.value
      yAxisField.value = numericFields.value.includes(saved.yAxisField) ? saved.yAxisField : yAxisField.value
      recommendations.value = Array.isArray(saved.recommendations) ? saved.recommendations : []
      chartData.value = saved.chartData || null
      report.value = saved.report || null
      await nextTick()
      if (chartData.value) renderChart()
      if (chartData.value || recommendations.value.length) return
    }
    // Selecting a dataset is an analysis action: load the backend recommendation
    // and render the first valid chart without requiring a second click.
    await nextTick()
    await recommendCharts()
  } catch (e) {
    console.error('加载字段失败:', e)
  }
}

onMounted(async () => {
  try {
    const data = await request.get('/v1/datasets?page=1&size=20')
    datasets.value = data.records || data.data?.records || []
    const saved = readChartState()
    const activeId = getActiveDatasetId()
    const activeDataset = datasets.value.find(ds => String(ds.id) === String(activeId))
    const datasetId = activeDataset?.status === 'PARSED'
      ? activeDataset.id
      : datasets.value.find(ds => ds.status === 'PARSED')?.id
    if (datasetId && datasets.value.some(ds => String(ds.id) === String(datasetId))) {
      selectedDataset.value = Number(datasetId)
      if (!activeId) localStorage.setItem('river_active_dataset_id', String(datasetId))
      await onDatasetChange()
    }
  } catch (e) {
    console.error('加载数据集失败:', e)
    ElMessage.error('加载数据集失败')
  }
})

const recommendCharts = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  const requestId = ++recommendationRequestId
  const selected = datasets.value.find(ds => String(ds.id) === String(selectedDataset.value))
  if (selected && selected.status !== 'PARSED') {
    ElMessage.info('数据集正在解析，请解析完成后再生成图表')
    return
  }
  recommendLoading.value = true
  try {
    const data = await request.post(`/v1/charts/recommend?datasetId=${selectedDataset.value}`)
    recommendations.value = data.data || data
    if (!recommendations.value.length) {
      ElMessage.info('后端未返回推荐，使用当前字段生成默认图表')
      if (xAxisField.value && yAxisField.value) await generateChart(false)
    } else {
      const recommendation = recommendations.value[0]
      chartType.value = recommendation.chartType || chartType.value
      xAxisField.value = recommendation.recommendedXField || xAxisField.value
      yAxisField.value = recommendation.recommendedYField || yAxisField.value
      await generateChart(false)
      ElMessage.success(`已根据后端推荐生成 ${recommendation.chartType || '图表'}`)
    }
  } catch (e) {
    if (requestId !== recommendationRequestId) return
    console.error('获取图表推荐失败:', e)
    ElMessage.error('获取图表推荐失败')
    recommendations.value = []
    chartData.value = null
    report.value = null
  } finally { persistChartState(); recommendLoading.value = false }
}

const selectRecommendation = async rec => {
  chartType.value = rec.chartType
  if (rec.recommendedXField) xAxisField.value = rec.recommendedXField
  if (rec.recommendedYField) yAxisField.value = rec.recommendedYField
  await generateChart()
}

const generateChart = async (showMessage = true) => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  if (!xAxisField.value || !yAxisField.value) {
    ElMessage.warning('请选择X轴和Y轴字段')
    return
  }
  try {
    const data = await request.post(`/v1/charts/generate?datasetId=${selectedDataset.value}&chartType=${chartType.value}&xAxisField=${xAxisField.value}&yAxisField=${yAxisField.value}`)
    chartData.value = data.data || data
    await nextTick()
    renderChart()
    if (showMessage) ElMessage.success('图表已生成')
    persistChartState()
  } catch (e) {
    console.error('生成图表失败:', e)
    ElMessage.error('生成图表失败')
    chartData.value = null
  }
}

const renderChart = () => {
  if (!chartRef.value || !chartData.value) return
  
  if (chartInstance) {
    chartInstance.dispose()
  }
  
  chartInstance = echarts.init(chartRef.value)
  const option = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: chartData.value.xData || chartData.value.xAxisData || [] },
    yAxis: { type: 'value' },
    series: [{
      type: (chartData.value.chartType || chartType.value).toLowerCase(),
      data: chartData.value.yData || chartData.value.yAxisData || []
    }]
  }
  chartInstance.setOption(option)
}

// Chart type buttons are executable choices: regenerate the real backend chart
// as soon as both axes are available instead of requiring a second click.
watch(chartType, value => {
  if (value && selectedDataset.value && xAxisField.value && yAxisField.value) generateChart()
})

watch([xAxisField, yAxisField], ([x, y], [oldX, oldY]) => {
  if (x && y && selectedDataset.value && chartData.value && (x !== oldX || y !== oldY)) generateChart()
})

onDatasetSync(async datasetId => {
  if (!datasetId) return
  selectedDataset.value = Number(datasetId)
  await onDatasetChange()
})

const generateReport = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  try {
    const data = await request.post(`/v1/charts/reports?datasetId=${selectedDataset.value}&reportType=FULL`)
    report.value = data.data || data
    persistChartState()
    ElMessage.success('报告生成成功')
  } catch (e) {
    console.error('生成报告失败:', e)
    ElMessage.error(e?.response?.data?.message || '生成报告失败')
  }
}
</script>

<style scoped>
.chart-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 20px;
  margin-bottom: 20px;
}

.toolbar-field {
  flex: 0 1 160px;
  min-width: 140px;
}

.dataset-field {
  flex-basis: 240px;
}

.chart-type-field {
  flex-basis: 140px;
}

.axis-field {
  flex-basis: 150px;
}

.toolbar-field :deep(.el-select) {
  width: 100%;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.toolbar-actions .el-button {
  margin-left: 0;
}

@media (max-width: 900px) {
  .toolbar-actions {
    width: 100%;
    margin-left: 0;
  }
}

.charts h2 {
  margin-bottom: 20px;
}

.trend-actions {
  margin-bottom: 20px;
}

.trend-header,
.trend-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trend-header {
  justify-content: space-between;
}

.trend-toolbar {
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.trend-task-select {
  width: 260px;
}

.trend-result {
  max-height: 260px;
  margin: 14px 0 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--river-line);
  border-radius: 6px;
  background: #f7f8fa;
  color: var(--text-2);
  font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
