<template>
  <div class="datasets">
    <div class="header">
      <h2>数据集管理</h2>
      <el-button type="primary" @click="uploadFile">
        <el-icon><Plus /></el-icon>上传文件
      </el-button>
    </div>
    <el-card>
      <el-table :data="datasets" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="文件名" />
        <el-table-column prop="fileType" label="类型" width="100" />
        <el-table-column prop="rowCount" label="行数" width="100" />
        <el-table-column prop="columnCount" label="列数" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="previewDataset(scope.row)">预览</el-button>
            <el-button size="small" @click="analyzeDataset(scope.row)">分析</el-button>
            <el-button size="small" type="danger" @click="deleteDataset(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination 
        style="margin-top: 20px; text-align: right;"
        :current-page="page" 
        :page-size="size" 
        :total="total"
        @current-change="loadDatasets"
      />
    </el-card>
    
    <el-dialog title="上传文件" v-model="uploadDialogVisible">
      <el-upload
        class="upload-demo"
        :action="uploadUrl"
        :headers="uploadHeaders"
        :show-file-list="false"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        accept=".xlsx,.xls,.csv"
      >
        <el-button type="primary">选择文件</el-button>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { setActiveDatasetId } from '../utils/workspaceSync'

const router = useRouter()
const datasets = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const uploadDialogVisible = ref(false)

const uploadUrl = '/api/v1/datasets/upload'
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`
}))

const loadDatasets = async (currentPage = 1) => {
  page.value = currentPage
  try {
    const data = await request.get(`/v1/datasets?page=${page.value}&size=${size.value}`)
    datasets.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载失败')
  }
}

const uploadFile = () => {
  uploadDialogVisible.value = true
}

const handleUploadSuccess = (response) => {
  const datasetId = response?.data?.id || response?.id
  ElMessage.success('上传成功，正在生成数据概览')
  uploadDialogVisible.value = false
  if (datasetId) {
    setActiveDatasetId(datasetId)
    router.push({ path: '/dashboard', query: { datasetId } })
  } else {
    loadDatasets()
  }
}

const handleUploadError = () => {
  ElMessage.error('上传失败')
}

const previewDataset = (dataset) => {
  router.push(`/datasets/${dataset.id}/preview`)
}

const analyzeDataset = (dataset) => {
  router.push({ path: '/analysis', query: { datasetId: dataset.id } })
}

const deleteDataset = async (dataset) => {
  try {
    await request.delete(`/v1/datasets/${dataset.id}`)
    ElMessage.success('删除成功')
    loadDatasets()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

const getStatusType = (status) => {
  return status === 'PARSED' ? 'success' : status === 'RUNNING' ? 'warning' : 'info'
}

onMounted(() => {
  loadDatasets()
})
</script>

<style scoped>
.datasets .header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>
