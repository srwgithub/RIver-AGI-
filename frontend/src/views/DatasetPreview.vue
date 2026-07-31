<template>
  <div class="dataset-preview">
    <el-button @click="$router.back()" style="margin-bottom: 20px;">返回</el-button>
    <h2>数据预览 - {{ dataset.name }}</h2>
    
    <el-card v-if="dataset" style="margin-bottom: 20px;">
      <el-descriptions :column="4">
        <el-descriptions-item label="文件名">{{ dataset.name }}</el-descriptions-item>
        <el-descriptions-item label="文件类型">{{ dataset.fileType }}</el-descriptions-item>
        <el-descriptions-item label="行数">{{ dataset.rowCount }}</el-descriptions-item>
        <el-descriptions-item label="列数">{{ dataset.columnCount }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatSize(dataset.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ dataset.status }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ dataset.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    
    <el-card>
      <template #header>
        <span>数据预览</span>
      </template>
      <el-table :data="previewData" style="width: 100%" :max-height="500">
        <el-table-column 
          v-for="col in columns" 
          :key="col" 
          :prop="col" 
          :label="col" 
        />
      </el-table>
    </el-card>
    
    <el-card style="margin-top: 20px;">
      <template #header>
        <span>字段识别</span>
      </template>
      <el-table :data="fields" style="width: 100%">
        <el-table-column prop="fieldName" label="字段名" />
        <el-table-column prop="fieldType" label="类型" />
        <el-table-column prop="nullCount" label="空值数" />
        <el-table-column prop="distinctCount" label="唯一值数" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const dataset = ref({})
const previewData = ref([])
const columns = ref([])
const fields = ref([])

const formatSize = (size) => {
  if (!size) return '0B'
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let result = size
  while (result >= 1024 && index < units.length - 1) {
    result /= 1024
    index++
  }
  return result.toFixed(2) + units[index]
}

onMounted(async () => {
  const id = route.params.id
  try {
    dataset.value = await request.get(`/v1/datasets/${id}`)
    fields.value = await request.get(`/v1/datasets/${id}/fields`)
    
    if (dataset.value.previewJson) {
      previewData.value = JSON.parse(dataset.value.previewJson)
      if (previewData.value.length > 0) {
        columns.value = Object.keys(previewData.value[0])
      }
    } else {
      columns.value = ['日期', '销售额', '利润', '客户数']
      previewData.value = [
        { '日期': '2026-01-01', '销售额': 10000, '利润': 2000, '客户数': 100 },
        { '日期': '2026-01-02', '销售额': 12000, '利润': 2400, '客户数': 120 }
      ]
    }
  } catch (e) {
    ElMessage.error('加载失败')
  }
})
</script>

<style scoped>
.dataset-preview h2 {
  margin-bottom: 20px;
}
</style>
