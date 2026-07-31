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
        <el-button type="primary" @click="recommendCharts">推荐图表</el-button>
        <el-button @click="generateChart">生成图表</el-button>
        <el-button @click="generateReport">生成报告</el-button>
      </div>
    </div>
    
    <el-card v-if="recommendations.length > 0" style="margin-bottom: 20px;">
      <template #header>
        <span>图表推荐</span>
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
import { ref, onMounted, nextTick, computed } from 'vue'
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
const chartData = ref(null)
const report = ref(null)
const chartRef = ref(null)
let chartInstance = null

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
    const data = await request.get(`/v1/datasets/${selectedDataset.value}/fields`)
    const rawFields = data.data || data || []
    fieldTypes.value = {}
    fields.value = rawFields.map(field => {
      if (typeof field === 'string') return field
      const fieldName = field.fieldName || field.name
      if (fieldName && field.fieldType) fieldTypes.value[fieldName] = field.fieldType
      return fieldName
    }).filter(Boolean)
    yAxisField.value = ''
    if (fields.value.length > 0) {
      xAxisField.value = fields.value[0]
      const firstNumeric = numericFields.value[0]
      if (firstNumeric) {
        yAxisField.value = firstNumeric
      }
    }
  } catch (e) {
    console.error('加载字段失败:', e)
  }
}

onMounted(async () => {
  try {
    const data = await request.get('/v1/datasets?page=1&size=20')
    datasets.value = data.records || data.data?.records || []
    const activeId = getActiveDatasetId()
    if (activeId && datasets.value.some(ds => String(ds.id) === String(activeId))) {
      selectedDataset.value = Number(activeId)
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
  try {
    const data = await request.post(`/v1/charts/recommend?datasetId=${selectedDataset.value}`)
    recommendations.value = data.data || data
  } catch (e) {
    console.error('获取图表推荐失败:', e)
    ElMessage.error('获取图表推荐失败')
    recommendations.value = []
  }
}

const selectRecommendation = (rec) => {
  chartType.value = rec.chartType
  if (rec.recommendedXField) xAxisField.value = rec.recommendedXField
  if (rec.recommendedYField) yAxisField.value = rec.recommendedYField
  generateChart()
}

const generateChart = async () => {
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
</style>
