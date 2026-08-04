<template>
  <section class="collection-page page-container">
    <header class="page-toolbar">
      <div>
        <h1>数据采集标注平台</h1>
        <p>统一完成多源导入、清洗、任务编排、协同标注和质量校验。</p>
      </div>
      <el-button type="primary" @click="taskDialog = true">
        <el-icon><Plus /></el-icon>
        新建采集标注任务
      </el-button>
    </header>

    <nav class="process-steps" aria-label="数据采集标注流程">
      <div
        v-for="(step, i) in steps"
        :key="step"
        :class="['process-step', stepState(i)]"
      >
        <span class="step-marker">
          <el-icon v-if="i < activeStep"><Check /></el-icon>
          <b v-else>{{ i + 1 }}</b>
        </span>
        <span>{{ step }}</span>
      </div>
    </nav>

    <div class="feature-grid">
      <article class="feature-card">
        <div class="feature-icon blue"><el-icon><Upload /></el-icon></div>
        <div>
          <h3>支持数据源</h3>
          <strong>表格 · 图片 · 视频 · 音频</strong>
          <p>统一上传入口与类型识别</p>
        </div>
      </article>
      <article class="feature-card">
        <div class="feature-icon green"><el-icon><Filter /></el-icon></div>
        <div>
          <h3>清洗与校验</h3>
          <strong>{{ cleanSummary.outputRows ?? 0 }} 条可用数据</strong>
          <p>空值、重复、格式校验预览</p>
        </div>
      </article>
      <article class="feature-card">
        <div class="feature-icon orange"><el-icon><UserFilled /></el-icon></div>
        <div>
          <h3>协同方式</h3>
          <strong>{{ taskForm.collaborationMode === 'TEAM' ? '多人协同' : '单人标注' }}</strong>
          <p>支持锁定、分配与复核</p>
        </div>
      </article>
    </div>

    <div class="main-grid">
      <el-card class="panel source-panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>1. 数据采集与清洗</span>
            <el-tag size="small" type="info">原始数据不覆盖</el-tag>
          </div>
        </template>

        <div
          class="upload-zone"
        @click="!uploading && $refs.sourceInput.click()"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
        :class="{ dragging: isDragging, uploading }"
        >
          <el-icon v-if="!uploading"><UploadFilled /></el-icon>
          <el-icon v-else class="is-loading"><Loading /></el-icon>
          <b>{{ uploading ? '文件上传处理中' : '上传表格或多媒体文件' }}</b>
          <span>{{ uploading ? '请勿关闭页面，完成后将自动刷新状态' : 'CSV / XLS / XLSX / JPG / PNG / MP4 / MP3' }}</span>
          <input ref="sourceInput" type="file" hidden multiple @change="handleSourceFiles">
        </div>

        <div v-if="uploadedFiles.length" class="file-list">
          <div v-for="file in uploadedFiles" :key="file.url || file.name" class="file-row">
            <span class="file-type">{{ file.mediaType || file.type?.toUpperCase() }}</span>
            <div>
              <b>{{ file.name }}</b>
              <small>{{ formatSize(file.size || 0) }} · 已完成上传</small>
            </div>
            <el-tag size="small" :type="file.mediaType ? 'success' : 'info'">
              {{ file.mediaType ? '媒体资源' : '数据集' }}
            </el-tag>
          </div>
        </div>

        <div class="clean-actions">
          <button
            v-for="item in cleanOptions"
            :key="item.key"
            type="button"
            :class="['switch-tag', { active: cleanConfig[item.key] }]"
            @click="cleanConfig[item.key] = !cleanConfig[item.key]"
          >
            {{ item.label }}
          </button>
          <el-button size="small" type="primary" plain :disabled="!selectedDataset" @click="previewClean">
            预览清洗结果
          </el-button>
          <el-button size="small" type="success" plain :disabled="!selectedDataset" :loading="cleanApplying" @click="applyClean">
            执行并保存清洗结果
          </el-button>
          <el-button v-if="cleanDownloadUrl" size="small" link type="primary" @click="downloadCleanResult">下载清洗结果</el-button>
        </div>

        <div class="clean-summary">
          <div><b>{{ cleanSummary.inputRows ?? '-' }}</b><span>输入行</span></div>
          <i>→</i>
          <div class="success"><b>{{ cleanSummary.outputRows ?? '-' }}</b><span>输出行</span></div>
          <div><b>{{ cleanSummary.duplicateRows ?? 0 }}</b><span>重复项</span></div>
          <div><b>{{ cleanSummary.invalidRows ?? 0 }}</b><span>异常项</span></div>
        </div>
      </el-card>

      <el-card class="panel task-panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <span>2. 任务与质量状态</span>
            <el-tooltip content="刷新任务进度" placement="top">
              <el-button class="icon-action" :icon="Refresh" circle @click="loadTasks" />
            </el-tooltip>
          </div>
        </template>

        <el-table v-if="tasks.length" v-loading="taskLoading" :data="tasks" height="352" class="task-table">
          <el-table-column prop="name" label="任务名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="mediaType" label="模态" width="88">
            <template #default="{ row }">
              <el-tag size="small" class="mode-tag">{{ row.mediaType || 'TEXT' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="96">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="statusType(row.status)"
                effect="light"
                :class="{ 'status-entry': row.status === 'READY' }"
                @click="row.status === 'READY' && enterAnnotation(row)"
              >
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" width="156">
            <template #default="{ row }">
              <el-tooltip :content="`${progress(row)}%`" placement="top">
                <el-progress :percentage="progress(row)" :stroke-width="6" :show-text="false" />
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="132" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip content="刷新" placement="top">
                  <el-button class="icon-action" :icon="Refresh" circle @click="refreshTask(row)" />
                </el-tooltip>
                <el-tooltip content="查看/编辑" placement="top">
                  <el-button class="icon-action" :icon="EditPen" circle @click="openTask(row)" />
                </el-tooltip>
                <el-tooltip content="删除任务" placement="top">
                  <el-button class="icon-action" :icon="Delete" circle @click="deleteTask(row)" />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div v-else v-loading="taskLoading" class="task-empty"><el-empty description="暂无采集标注任务"><el-button type="primary" plain @click="taskDialog = true">创建第一个任务</el-button></el-empty></div>
      </el-card>
    </div>

    <el-card class="panel workspace-panel" shadow="never">
      <template #header>
        <div class="panel-title">
          <span>3. 多模态标注工作区</span>
          <el-tag v-if="activeTask" type="success">{{ activeTask.name }}</el-tag>
          <span v-else class="muted">创建任务后开始标注</span>
        </div>
      </template>

      <div v-if="!activeTask" class="workspace-empty">
        <el-icon><EditPen /></el-icon>
        <b>选择或创建一个任务</b>
        <span>图片支持框选，视频支持关键帧，音频支持转写与时间段标注，文本支持分类与片段标注。</span>
        <el-button type="primary" plain @click="taskDialog = true">创建任务</el-button>
      </div>
      <div v-else class="workspace-content">
        <aside>
          <div class="media-chip" :class="activeTask.mediaType?.toLowerCase()">{{ activeTask.mediaType || 'TEXT' }}</div>
          <b>{{ activeTask.totalItems || 0 }} 个待处理项</b>
          <small>已完成 {{ activeTask.completedItems || 0 }} 个</small>
          <el-divider />
          <el-button size="small" type="primary" @click="goAnnotation">进入标注工作台</el-button>
          <el-button size="small" plain @click="runQuality">自动质量校验</el-button>
        </aside>
        <div class="preview-area">
          <div v-if="activeMedia" class="media-preview">
            <img v-if="activeMedia.mediaType === 'IMAGE'" :src="activeMedia.url" alt="待标注图片">
            <video v-else-if="activeMedia.mediaType === 'VIDEO'" :src="activeMedia.url" controls></video>
            <audio v-else-if="activeMedia.mediaType === 'AUDIO'" :src="activeMedia.url" controls></audio>
          </div>
          <div v-else class="preview-placeholder">
            <el-icon><Picture /></el-icon>
            <span>上传媒体资源后在这里预览</span>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="taskDialog" title="新建数据采集标注任务" width="560px">
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.name" placeholder="例如：商品图片分类任务" />
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="taskForm.sourceType" style="width:100%">
            <el-option label="已有数据集" value="DATASET" />
            <el-option label="图片" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
            <el-option label="音频" value="AUDIO" />
            <el-option label="文本" value="TEXT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="taskForm.sourceType === 'DATASET'" label="选择数据集">
          <el-select v-model="taskForm.datasetId" style="width:100%" placeholder="请选择已解析数据集">
            <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签体系">
          <el-select v-model="taskForm.labelSchemaId" style="width:100%" placeholder="选择标签体系">
            <el-option v-for="schema in schemas" :key="schema.id" :label="schema.name" :value="schema.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="协同方式">
          <el-radio-group v-model="taskForm.collaborationMode">
            <el-radio label="SINGLE">单人标注</el-radio>
            <el-radio label="TEAM">多人协同</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标注规则">
          <el-input v-model="taskForm.annotationRuleJson" type="textarea" :rows="3" placeholder="可填写格式、边界框、时间段等规则说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createTask">创建任务</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog" title="查看/编辑采集标注任务" width="520px">
      <el-form :model="editingTask" label-width="90px">
        <el-form-item label="任务名称" required><el-input v-model="editingTask.name" /></el-form-item>
        <el-form-item label="数据源"><el-input :model-value="editingTask.datasetId ? `数据集 #${editingTask.datasetId}` : (editingTask.mediaType || '未绑定')" disabled /></el-form-item>
        <el-form-item label="标签体系"><el-select v-model="editingTask.labelSchemaId" style="width:100%" clearable><el-option v-for="schema in schemas" :key="schema.id" :label="schema.name" :value="schema.id" /></el-select></el-form-item>
        <el-form-item label="协同方式"><el-radio-group v-model="editingTask.collaborationMode"><el-radio label="SINGLE">单人标注</el-radio><el-radio label="TEAM">多人协同</el-radio></el-radio-group></el-form-item>
        <el-form-item label="任务状态"><el-select v-model="editingTask.status" style="width:100%"><el-option label="草稿" value="DRAFT" /><el-option label="待标注" value="READY" /><el-option label="进行中" value="RUNNING" /><el-option label="已完成" value="COMPLETED" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="editDialog = false">取消</el-button><el-button type="primary" :loading="savingEdit" @click="saveTaskEdit">保存并更新进度</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, UploadFilled, Filter, UserFilled, EditPen, Picture, Check, Refresh, Delete, Loading } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { getActiveDatasetId, setActiveDatasetId } from '../utils/workspaceSync'

const steps = ['采集数据', '清洗校验', '任务配置', '标注质检']
const router = useRouter()
const cleanOptions = [
  { key: 'removeEmpty', label: '移除空行' },
  { key: 'removeDuplicate', label: '去除重复' },
  { key: 'validateFormat', label: '格式校验' }
]

const activeStep = ref(0)
const taskDialog = ref(false)
const editDialog = ref(false)
const savingEdit = ref(false)
const saving = ref(false)
const uploading = ref(false)
const taskLoading = ref(false)
const cleanApplying = ref(false)
const isDragging = ref(false)
const datasets = ref([])
const schemas = ref([])
const tasks = ref([])
const uploadedFiles = ref([])
const activeTask = ref(null)
const editingTask = ref({})
const activeMedia = ref(null)
const selectedDataset = ref(null)
const taskForm = reactive({ name: '', sourceType: 'DATASET', datasetId: null, labelSchemaId: null, collaborationMode: 'SINGLE', annotationRuleJson: '' })
const cleanConfig = reactive({ removeEmpty: true, removeDuplicate: true, validateFormat: true })
const cleanSummary = reactive({})
const cleanDownloadUrl = ref('')

const stepState = index => {
  if (index < activeStep.value) return 'done'
  if (index === activeStep.value) return 'current'
  return 'pending'
}

const loadData = async () => {
  try {
    const [ds, ss] = await Promise.all([request.get('/v1/datasets?page=1&size=100'), request.get('/v1/label-schemas?page=1&size=100')])
    datasets.value = (ds.records || []).filter(d => d.status === 'PARSED')
    schemas.value = ss.records || []
    const activeId = getActiveDatasetId()
    const preferred = datasets.value.find(d => String(d.id) === String(activeId)) || datasets.value[0]
    if (preferred) {
      selectedDataset.value = preferred.id
      taskForm.datasetId = preferred.id
      setActiveDatasetId(preferred.id)
    }
    if (!taskForm.labelSchemaId && schemas.value[0]) taskForm.labelSchemaId = schemas.value[0].id
    await loadTasks()
  } catch (e) {
    ElMessage.error('平台数据加载失败')
  }
}

const loadTasks = async () => {
  taskLoading.value = true
  try {
    const data = await request.get('/v1/collection-tasks?page=1&size=20')
    const records = data.records || []
    tasks.value = await Promise.all(records.map(async task => {
      try { return await request.get(`/v1/collection-tasks/${task.id}/progress`) } catch { return task }
    }))
    if (activeTask.value) {
      const current = tasks.value.find(task => task.id === activeTask.value.id)
      if (current) activeTask.value = current
    }
  } catch (e) {
    ElMessage.error('任务进度加载失败')
  } finally {
    taskLoading.value = false
  }
}

const handleDrop = event => {
  isDragging.value = false
  uploadFiles(event.dataTransfer.files)
}

const handleSourceFiles = event => {
  uploadFiles(event.target.files)
  event.target.value = ''
}

const uploadFiles = async files => {
  const sourceFiles = Array.from(files || [])
  if (!sourceFiles.length || uploading.value) return
  uploading.value = true
  for (const file of sourceFiles) {
    const isMedia = /\.(jpg|jpeg|png|gif|mp4|mov|mp3|wav)$/i.test(file.name)
    const form = new FormData()
    form.append('file', file)
    try {
      const uploaded = isMedia
        ? await request.post('/v1/collection-tasks/media-upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
        : await request.post('/v1/datasets/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      uploadedFiles.value.push({ ...(uploaded || {}), name: uploaded?.name || file.name, size: uploaded?.size || file.size, type: file.name.split('.').pop() })
      if (!isMedia && uploaded?.id) {
        selectedDataset.value = uploaded.id
        taskForm.datasetId = uploaded.id
        setActiveDatasetId(uploaded.id)
      }
      if (isMedia) activeMedia.value = uploaded
      ElMessage.success(`${file.name} 上传成功`)
    } catch (err) {
      ElMessage.error(`${file.name} 上传失败`)
    }
  }
  uploading.value = false
}

const previewClean = async () => {
  if (!selectedDataset.value) return ElMessage.warning('请先上传或选择一个已解析数据集')
  try {
    let task = tasks.value.find(t => t.datasetId === selectedDataset.value)
    if (!task) {
      task = await request.post('/v1/collection-tasks', { name: '数据清洗预览任务', sourceType: 'DATASET', datasetId: selectedDataset.value })
      tasks.value.unshift(task)
    }
    Object.assign(cleanSummary, await request.post(`/v1/collection-tasks/${task.id}/clean-preview`, cleanConfig))
    activeTask.value = task
    activeStep.value = 1
    ElMessage.success('清洗预览完成')
  } catch (e) {
    ElMessage.error(e.message || '清洗预览失败')
  }
}

const applyClean = async () => {
  if (!selectedDataset.value) return ElMessage.warning('请先上传或选择一个已解析数据集')
  cleanApplying.value = true
  try {
    let task = tasks.value.find(t => t.datasetId === selectedDataset.value)
    if (!task) {
      task = await request.post('/v1/collection-tasks', { name: '数据清洗任务', sourceType: 'DATASET', datasetId: selectedDataset.value })
      tasks.value.unshift(task)
    }
    const result = await request.post(`/v1/collection-tasks/${task.id}/clean-apply`, cleanConfig)
    Object.assign(cleanSummary, result)
    if (result?.outputDatasetId) {
      selectedDataset.value = result.outputDatasetId
      taskForm.datasetId = result.outputDatasetId
      setActiveDatasetId(result.outputDatasetId)
    }
    cleanDownloadUrl.value = result?.fileUrl || ''
    activeTask.value = task
    activeStep.value = 1
    await loadData()
    ElMessage.success(`清洗完成，已生成新数据集 #${result.outputDatasetId}`)
  } catch (e) {
    ElMessage.error(e.message || '执行清洗失败')
  } finally {
    cleanApplying.value = false
  }
}

const downloadCleanResult = () => {
  if (cleanDownloadUrl.value) window.open(cleanDownloadUrl.value, '_blank', 'noopener')
}

const createTask = async () => {
  if (!taskForm.name) return ElMessage.warning('请输入任务名称')
  saving.value = true
  try {
    const task = await request.post('/v1/collection-tasks', { ...taskForm, annotationRuleJson: taskForm.annotationRuleJson ? JSON.stringify({ description: taskForm.annotationRuleJson }) : null })
    if (activeMedia.value?.url) {
      await request.post(`/v1/collection-tasks/${task.id}/media-items`, { mediaType: activeMedia.value.mediaType, mediaUrl: activeMedia.value.url })
    }
    tasks.value.unshift(task)
    activeTask.value = task
    taskDialog.value = false
    activeStep.value = 2
    ElMessage.success('采集标注任务已创建并绑定媒体资源')
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    saving.value = false
  }
}

const refreshTask = async task => {
  try {
    const next = await request.get(`/v1/collection-tasks/${task.id}/progress`)
    const index = tasks.value.findIndex(item => item.id === task.id)
    if (index >= 0) tasks.value[index] = next
    if (activeTask.value?.id === task.id) activeTask.value = next
    ElMessage.success('任务进度已刷新')
  } catch (e) {
    ElMessage.error('任务进度刷新失败')
  }
}

const openTask = task => {
  activeTask.value = task
  activeStep.value = 3
  editingTask.value = { ...task }
  editDialog.value = true
}
const saveTaskEdit = async () => {
  if (!editingTask.value.name?.trim()) return ElMessage.warning('请输入任务名称')
  savingEdit.value = true
  try {
    const updated = await request.put(`/v1/collection-tasks/${editingTask.value.id}`, {
      name: editingTask.value.name,
      labelSchemaId: editingTask.value.labelSchemaId,
      collaborationMode: editingTask.value.collaborationMode,
      status: editingTask.value.status
    })
    activeTask.value = updated
    editDialog.value = false
    await loadTasks()
    ElMessage.success('任务已更新，进度已刷新')
  } catch (e) {
    ElMessage.error(e.message || '任务更新失败')
  } finally {
    savingEdit.value = false
  }
}
const deleteTask = async task => {
  try {
    await ElMessageBox.confirm(`确认删除采集标注任务“${task.name}”？`, '删除任务', { type: 'warning' })
    await request.delete(`/v1/collection-tasks/${task.id}`)
    if (activeTask.value?.id === task.id) activeTask.value = null
    await loadTasks()
    ElMessage.success('采集标注任务已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除任务失败')
  }
}
const progress = task => {
  if (task.status === 'COMPLETED' || task.status === 'FINISHED') return 100
  if (task.status === 'CLEANED') return 25
  if (task.status === 'READY') return 50
  if (!task.totalItems) return 0
  const itemProgress = Math.round((task.completedItems || 0) / task.totalItems * 50)
  return task.status === 'RUNNING' || task.status === 'IN_PROGRESS' ? 50 + itemProgress : itemProgress
}
const formatSize = n => n > 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`
const statusLabel = status => ({ DRAFT: '草稿', CLEANED: '清洗完成', READY: '待标注', RUNNING: '进行中', IN_PROGRESS: '进行中', COMPLETED: '已完成', FINISHED: '已完成', ERROR: '异常', FAILED: '异常' }[status] || status || '草稿')
const statusType = status => ({ COMPLETED: 'success', FINISHED: 'success', CLEANED: 'warning', READY: 'primary', RUNNING: 'primary', IN_PROGRESS: 'primary', ERROR: 'danger', FAILED: 'danger', DRAFT: 'info' }[status] || 'info')
const goAnnotation = async () => {
  if (!activeTask.value) return ElMessage.warning('请先选择采集标注任务')
  await enterAnnotation(activeTask.value)
}
const enterAnnotation = async task => {
  if (!task) return ElMessage.warning('请先选择采集标注任务')
  try {
    if (task.status === 'DRAFT') {
      task = await request.put(`/v1/collection-tasks/${task.id}`, { status: 'READY' })
      await loadTasks()
    }
    activeTask.value = task
    const query = new URLSearchParams({ collectionTaskId: String(task.id) })
    window.location.href = `/annotation-platform?${query.toString()}`
  } catch (e) {
    ElMessage.error(e.message || '任务状态更新失败')
  }
}
const runQuality = async () => {
  if (!activeTask.value) return ElMessage.warning('请先选择采集标注任务')
  if (!activeTask.value.datasetId) return ElMessage.warning('当前任务没有关联数据集，无法执行质量校验')
  try {
    const result = await request.get('/v1/annotation-tasks?page=1&size=100')
    const annotationTask = (result.records || []).find(item => String(item.datasetId) === String(activeTask.value.datasetId) && item.status !== 'CANCELLED')
    if (!annotationTask) return ElMessage.warning('当前数据集还没有标注任务，请先完成任务配置和派发')
    await request.post(`/v1/annotation-tasks/${annotationTask.id}/auto-validate`)
    ElMessage.success('自动质量校验完成，正在进入质量管理中心')
    router.push({ path: '/annotation-quality', query: { taskId: annotationTask.id } })
  } catch (e) {
    ElMessage.error(`自动质量校验失败：${e.message || '后端接口不可用'}`)
  }
}

let progressTimer
onMounted(async () => {
  await loadData()
  progressTimer = window.setInterval(loadTasks, 3000)
})
onUnmounted(() => {
  if (progressTimer) window.clearInterval(progressTimer)
})
</script>

<style scoped>
.collection-page {
  color: var(--text-1);
}

.status-entry {
  cursor: pointer;
}

.status-entry:hover {
  filter: brightness(0.96);
  transform: translateY(-1px);
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
}

.page-toolbar h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 600;
}

.page-toolbar p {
  margin: 8px 0 0;
  color: var(--text-3);
  font-size: 14px;
  line-height: 1.5;
}

.process-steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-bottom: 24px;
}

.process-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-3);
  font-size: 14px;
}

.process-step:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 40px;
  right: 16px;
  top: 15px;
  height: 1px;
  background: var(--border-1);
}

.step-marker {
  z-index: 1;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid var(--border-1);
  background: #fff;
  color: var(--text-3);
  font-size: 12px;
}

.process-step.done,
.process-step.current {
  color: var(--primary);
  font-weight: 500;
}

.process-step.done .step-marker,
.process-step.current .step-marker {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  min-height: 112px;
  background: #fff;
  border: 1px solid var(--border-1);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
  transition: box-shadow .18s ease, transform .18s ease, border-color .18s ease;
}

.feature-card:hover {
  transform: translateY(-2px);
  border-color: var(--primary-disabled);
  box-shadow: var(--shadow-hover);
}

.feature-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 6px;
  flex: 0 0 auto;
  font-size: 22px;
}

.feature-icon.blue { color: var(--primary); background: var(--primary-light); }
.feature-icon.green { color: var(--success); background: var(--success-light); }
.feature-icon.orange { color: var(--warning); background: var(--warning-light); }

.feature-card h3 {
  margin: 0 0 8px;
  color: var(--text-3);
  font-size: 12px;
  font-weight: 400;
}

.feature-card strong {
  display: block;
  color: var(--text-1);
  font-size: 16px;
  font-weight: 500;
}

.feature-card p {
  margin: 6px 0 0;
  color: var(--text-3);
  font-size: 12px;
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(420px, 2fr);
  gap: 16px;
  margin-bottom: 24px;
}

.panel {
  border-radius: 8px;
  border: 1px solid var(--border-1);
  box-shadow: var(--shadow-card);
}

.panel :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom-color: var(--border-1);
}

.panel :deep(.el-card__body) {
  padding: 20px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 28px;
  font-size: 18px;
  font-weight: 600;
}

.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 176px;
  padding: 24px;
  border: 1px dashed var(--border-3);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease;
}

.upload-zone:hover,
.upload-zone.dragging {
  border-color: var(--primary);
  background: var(--primary-light);
}
.upload-zone.uploading { cursor: wait; border-color: var(--primary-disabled); background: var(--primary-light); }
.upload-zone .is-loading { animation: rotating 1.5s linear infinite; }

.upload-zone .el-icon {
  color: var(--primary);
  font-size: 34px;
}

.upload-zone b {
  color: var(--text-1);
  font-size: 16px;
  font-weight: 500;
}

.upload-zone span,
.file-row small,
.muted {
  color: var(--text-3);
  font-size: 12px;
}

.file-list {
  margin-top: 16px;
  max-height: 128px;
  overflow: auto;
  border: 1px solid var(--border-1);
  border-radius: 8px;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-1);
}

.file-row:last-child {
  border-bottom: 0;
}

.file-row > div {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.file-row b {
  overflow: hidden;
  color: var(--text-1);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-type,
.mode-tag {
  border-radius: 4px;
  font-weight: 500;
}

.clean-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.switch-tag {
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--border-1);
  border-radius: 6px;
  background: #fff;
  color: var(--text-2);
  font-size: 14px;
  cursor: pointer;
}

.switch-tag:hover {
  background: var(--bg-hover);
}

.switch-tag.active {
  border-color: var(--primary);
  background: var(--primary-light);
  color: var(--primary);
}

.clean-summary {
  display: grid;
  grid-template-columns: 1fr 20px 1fr 1fr 1fr;
  align-items: center;
  margin-top: 16px;
  padding: 16px;
  border-radius: 8px;
  background: #F7F8FA;
  text-align: center;
}

.clean-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.clean-summary b {
  color: var(--text-1);
  font-size: 22px;
  line-height: 1.2;
  font-weight: 600;
}

.clean-summary span,
.clean-summary i {
  color: var(--text-3);
  font-size: 12px;
  font-style: normal;
}

.clean-summary .success b {
  color: var(--success);
}

.task-panel :deep(.el-card__body) {
  padding-top: 0;
}
.task-empty { min-height: 352px; display: grid; place-items: center; }

.task-table {
  width: 100%;
}

.task-table :deep(th.el-table__cell) {
  background: #F7F8FA !important;
  color: var(--text-2);
  font-weight: 600;
}

.table-actions {
  display: flex;
  gap: 6px;
}

.icon-action {
  width: 28px;
  height: 28px;
  border: 0;
  color: var(--text-3);
}

.icon-action:not(.is-disabled):hover {
  background: var(--primary-light);
  color: var(--primary);
}

.workspace-panel {
  min-height: 272px;
}

.workspace-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 24px;
  text-align: center;
}

.workspace-empty .el-icon,
.preview-placeholder .el-icon {
  color: var(--text-4);
  font-size: 36px;
}

.workspace-empty b {
  color: var(--text-1);
  font-size: 16px;
  font-weight: 500;
}

.workspace-empty span,
.preview-placeholder {
  color: var(--text-3);
  font-size: 14px;
}

.workspace-content {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 20px;
}

.workspace-content aside {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--border-1);
  border-radius: 8px;
  background: #F7F8FA;
}

.workspace-content aside small {
  color: var(--text-3);
}

.media-chip {
  width: 56px;
  padding: 7px 0;
  border-radius: 6px;
  background: #F2F3F5;
  color: var(--text-3);
  text-align: center;
  font-size: 12px;
  font-weight: 600;
}

.media-chip.image { color: var(--primary); background: var(--primary-light); }
.media-chip.video { color: #722ED1; background: #F9F0FF; }
.media-chip.audio { color: var(--warning); background: var(--warning-light); }

.preview-area {
  display: grid;
  place-items: center;
  min-height: 192px;
  overflow: hidden;
  border: 1px solid var(--border-1);
  border-radius: 8px;
  background: #F7F8FA;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.media-preview img,
.media-preview video {
  max-width: 100%;
  max-height: 260px;
  display: block;
}

.media-preview audio {
  width: min(420px, 70vw);
}

@media (max-width: 1100px) {
  .main-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .page-toolbar {
    flex-direction: column;
  }

  .feature-grid,
  .process-steps,
  .workspace-content {
    grid-template-columns: 1fr;
  }

  .process-step:not(:last-child)::after {
    display: none;
  }

  .clean-summary {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .clean-summary i {
    display: none;
  }
}
</style>
