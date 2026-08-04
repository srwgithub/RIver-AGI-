<template>
  <div class="analysis">
    <h2>数据分析</h2>
    
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="8">
        <el-select v-model="selectedDataset" placeholder="选择数据集">
          <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
        </el-select>
      </el-col>
      <el-col :span="16">
        <el-button type="primary" @click="runProfile">数据画像</el-button>
        <el-button @click="runOutliers">异常检测</el-button>
        <el-button @click="runQuality">质量分析</el-button>
      </el-col>
    </el-row>
    
    <el-card v-if="profileData" style="margin-bottom: 20px;">
      <template #header>
        <span>数据画像</span>
      </template>
      <el-descriptions :column="3">
        <el-descriptions-item label="数据集ID">{{ profileData.datasetId }}</el-descriptions-item>
        <el-descriptions-item label="行数">{{ profileData.rowCount }}</el-descriptions-item>
        <el-descriptions-item label="列数">{{ profileData.columnCount }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="profileData.fieldStatistics" style="width: 100%; margin-top: 20px;">
        <el-table-column prop="fieldName" label="字段名" />
        <el-table-column prop="fieldType" label="字段类型">
          <template #default="scope">
            <el-tag :type="scope.row.fieldType === 'NUMERIC' ? 'primary' : 'info'">
              {{ scope.row.fieldType === 'NUMERIC' ? '数值' : '字符串' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="hasNumericFields" prop="minValue" label="最小值" />
        <el-table-column v-if="hasNumericFields" prop="maxValue" label="最大值" />
        <el-table-column v-if="hasNumericFields" prop="meanValue" label="平均值">
          <template #default="scope">
            <span>{{ scope.row.meanValue != null ? Number(scope.row.meanValue).toFixed(2) : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <el-card v-if="outlierData" style="margin-bottom: 20px;">
      <template #header>
        <span>异常检测结果 (共 {{ outlierData.totalOutliers || 0 }} 个异常)</span>
      </template>
      <el-table v-if="outlierData.outliers && outlierData.outliers.length > 0" :data="outlierData.outliers" style="width: 100%">
        <el-table-column prop="fieldName" label="字段名" />
        <el-table-column prop="rowIndex" label="行号" />
        <el-table-column prop="value" label="值" />
        <el-table-column prop="zScore" label="Z-score" />
        <el-table-column prop="outlierType" label="类型">
          <template #default="scope">
            <el-tag :type="scope.row.outlierType === 'EXTREME' ? 'danger' : 'warning'">
              {{ scope.row.outlierType === 'EXTREME' ? '极端' : '中度' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="未检测到异常值" />
    </el-card>
    
    <el-card v-if="qualityData">
      <template #header>
        <span>数据质量分析</span>
      </template>
      <el-descriptions :column="4">
        <el-descriptions-item label="完整性">{{ qualityData.qualityMetrics?.completeness }}</el-descriptions-item>
        <el-descriptions-item label="准确性">{{ qualityData.qualityMetrics?.accuracy }}</el-descriptions-item>
        <el-descriptions-item label="一致性">{{ qualityData.qualityMetrics?.consistency }}</el-descriptions-item>
        <el-descriptions-item label="总分">{{ qualityData.overallScore }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 20px;">
        <h4>问题汇总</h4>
        <el-tag type="danger">{{ qualityData.issueSummary?.criticalIssues }} 严重</el-tag>
        <el-tag type="warning">{{ qualityData.issueSummary?.warningIssues }} 警告</el-tag>
        <el-tag>{{ qualityData.issueSummary?.infoIssues }} 信息</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { getActiveDatasetId, onDatasetSync, setDatasetHealthScore } from '../utils/workspaceSync'

const datasets = ref([])
const selectedDataset = ref('')
const profileData = ref(null)
const outlierData = ref(null)
const qualityData = ref(null)

const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
const stateStorageKey = `river_analysis_state_${currentUser.id || currentUser.username || 'default'}`
const syncDataset = async datasetId => { if (!datasetId) return; const id = Number(datasetId); selectedDataset.value = Number.isNaN(id) ? datasetId : id; profileData.value = null; outlierData.value = null; qualityData.value = null }

const restoreState = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(stateStorageKey) || 'null')
    if (!saved) return
    selectedDataset.value = saved.selectedDataset || ''
    profileData.value = saved.profileData || null
    outlierData.value = saved.outlierData || null
    qualityData.value = saved.qualityData || null
  } catch (e) {
    localStorage.removeItem(stateStorageKey)
    console.warn('恢复分析页面状态失败，已清理旧状态', e)
  }
}

const persistState = () => {
  try {
    localStorage.setItem(stateStorageKey, JSON.stringify({
      selectedDataset: selectedDataset.value,
      profileData: profileData.value,
      outlierData: outlierData.value,
      qualityData: qualityData.value
    }))
  } catch (e) {
    // 结果过大时不影响当前页面使用。
    console.warn('保存分析页面状态失败', e)
  }
}

restoreState()

watch([selectedDataset, profileData, outlierData, qualityData], persistState, { deep: true })
onDatasetSync(syncDataset)
watch(selectedDataset, (datasetId, previousDatasetId) => {
  if (previousDatasetId && datasetId !== previousDatasetId) {
    profileData.value = null
    outlierData.value = null
    qualityData.value = null
  }
})

const hasNumericFields = computed(() => {
  if (!profileData.value?.fieldStatistics) return false
  return profileData.value.fieldStatistics.some(f => f.fieldType === 'NUMERIC')
})

onMounted(async () => {
  try {
    const data = await request.get('/v1/datasets?page=1&size=20')
    if (data && Array.isArray(data.records)) {
      datasets.value = data.records
    } else if (Array.isArray(data)) {
      datasets.value = data
    }
    const activeId = getActiveDatasetId()
    if (activeId && datasets.value.some(ds => String(ds.id) === String(activeId))) {
      selectedDataset.value = Number(activeId)
    } else if (selectedDataset.value && !datasets.value.some(ds => String(ds.id) === String(selectedDataset.value))) {
      selectedDataset.value = ''
      profileData.value = null
      outlierData.value = null
      qualityData.value = null
    }
  } catch (e) {
    console.error('Failed to load datasets:', e)
  }
})

const runProfile = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  try {
    const task = await request.post(`/v1/analysis/profile?datasetId=${selectedDataset.value}`)
    if (task.resultJson) {
      profileData.value = JSON.parse(task.resultJson)
      ElMessage.success('分析完成')
    }
  } catch (e) {
    ElMessage.error('分析失败: ' + (e.message || '未知错误'))
  }
}

const runOutliers = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  try {
    const task = await request.post(`/v1/analysis/outliers?datasetId=${selectedDataset.value}`)
    if (task.resultJson) {
      outlierData.value = JSON.parse(task.resultJson)
      ElMessage.success('分析完成')
    }
  } catch (e) {
    ElMessage.error('分析失败: ' + (e.message || '未知错误'))
  }
}

const runQuality = async () => {
  if (!selectedDataset.value) {
    ElMessage.warning('请选择数据集')
    return
  }
  try {
    const task = await request.post(`/v1/analysis/quality?datasetId=${selectedDataset.value}`)
    if (task.resultJson) {
      qualityData.value = JSON.parse(task.resultJson)
      const score = qualityData.value.overallScore ?? qualityData.value.qualityScore
      if (score != null) setDatasetHealthScore(selectedDataset.value, Number(score) <= 1 ? Number(score) * 100 : Number(score))
      ElMessage.success('分析完成')
    }
  } catch (e) {
    ElMessage.error('分析失败: ' + (e.message || '未知错误'))
  }
}
</script>

<style scoped>
.analysis h2 {
  margin-bottom: 20px;
}
</style>
