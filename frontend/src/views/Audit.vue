<template>
  <div class="audit">
    <div class="audit-heading"><h2>审计中心</h2><el-button type="primary" @click="exportLogs">导出审计记录</el-button></div>
    
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="8">
        <el-input v-model="userId" placeholder="用户ID" />
      </el-col>
      <el-col :span="8">
        <el-input v-model="resourceType" placeholder="资源类型" />
      </el-col>
      <el-col :span="8">
        <el-button type="primary" @click="loadLogs">查询</el-button>
      </el-col>
    </el-row>
    
    <el-card>
      <el-table :data="logs" style="width: 100%" stripe highlight-current-row @row-click="showDetail">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="actionType" label="操作类型" />
        <el-table-column prop="resourceType" label="资源类型" />
        <el-table-column prop="resourceName" label="资源名称" />
        <el-table-column prop="username" label="操作人" />
        <el-table-column prop="ipAddress" label="IP地址" />
        <el-table-column prop="result" label="结果">
          <template #default="scope">
            <el-tag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'">
              {{ scope.row.result }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" />
        <el-table-column prop="requestPath" label="请求路径" show-overflow-tooltip />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="operationDetails" label="操作内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="审计追溯" width="110" fixed="right">
          <template #default="scope">
            <el-button type="primary" link @click.stop="showDetail(scope.row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination 
        style="margin-top: 20px; text-align: right;"
        :current-page="page" 
        :page-size="size" 
        :total="total"
        @current-change="loadLogs"
      />
    </el-card>
    <el-drawer v-model="detailVisible" title="审计追溯详情" size="460px">
      <el-descriptions v-if="selectedLog" :column="1" border>
        <el-descriptions-item label="操作类型">{{ selectedLog.actionType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资源模块">{{ selectedLog.resourceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资源名称">{{ selectedLog.resourceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ selectedLog.username || '-' }}（用户 ID：{{ selectedLog.userId || '-' }}）</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ selectedLog.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备信息">{{ selectedLog.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求信息">{{ selectedLog.requestMethod || '-' }} {{ selectedLog.requestPath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作结果">{{ selectedLog.result || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ selectedLog.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完整操作内容"><pre>{{ selectedLog.operationDetails || '暂无详细内容' }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'

const logs = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const userId = ref('')
const resourceType = ref('')
const selectedLog = ref(null)
const detailVisible = ref(false)

const showDetail = row => {
  selectedLog.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadLogs()
})

const loadLogs = async (currentPage = 1) => {
  page.value = currentPage
  try {
    const params = new URLSearchParams()
    params.append('page', page.value)
    params.append('size', size.value)
    if (userId.value) params.append('userId', userId.value)
    if (resourceType.value) params.append('resourceType', resourceType.value)
    
    const data = await request.get(`/v1/audit/logs?${params}`)
    logs.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    console.error('加载审计日志失败:', e)
    logs.value = []
    total.value = 0
  }
}

const exportLogs = async () => {
  const params = new URLSearchParams()
  if (userId.value) params.append('userId', userId.value)
  if (resourceType.value) params.append('resourceType', resourceType.value)
  const response = await request.get(`/v1/audit/logs/export?${params}`, { responseType: 'blob' })
  const url = URL.createObjectURL(response)
  const link = document.createElement('a'); link.href = url; link.download = 'audit_logs.csv'; link.click(); URL.revokeObjectURL(url)
}
</script>

<style scoped>
.audit h2 {
  margin-bottom: 20px;
}
.audit-heading { display:flex; align-items:center; justify-content:space-between; margin-bottom:20px; }
pre { white-space: pre-wrap; word-break: break-all; margin: 0; }
</style>
