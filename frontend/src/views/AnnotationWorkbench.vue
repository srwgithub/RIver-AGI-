<template>
  <div class="workbench page-container">
    <header class="page-heading">
      <div>
        <span class="eyebrow">STEP 3 / ANNOTATION WORKBENCH</span>
        <h1>{{ task?.name || '专属标注工作台' }}</h1>
        <p>{{ modeLabel }} · 逐条完成标注，提交前自动校验。</p>
      </div>
      <div class="heading-actions">
        <el-tag :type="taskStatusType">{{ taskStatusText }}</el-tag>
        <el-button :icon="Download" :loading="exporting" :disabled="!task" @click="exportAnnotations">导出标注结果</el-button>
        <el-button @click="router.push({ path: '/collection-annotation/config', query: { datasetId: task?.datasetId } })">返回任务配置</el-button>
      </div>
    </header>

    <div class="steps"><div class="step done"><b>✓</b><span>上传清洗数据集</span></div><div class="line active" /><div class="step done"><b>✓</b><span>配置标签与派发任务</span></div><div class="line active" /><div class="step active"><b>3</b><span>专属标注工作台</span></div></div>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="error-alert" />
    <section class="progress-panel">
      <div><span class="section-kicker">ANNOTATION PROGRESS</span><strong>{{ completedCount }} / {{ annotations.length || task?.totalRows || 0 }}</strong><span>已提交</span></div>
      <el-progress :percentage="progress" :stroke-width="8" :status="progress === 100 ? 'success' : undefined" />
      <div class="progress-meta"><span>当前第 {{ currentPosition + 1 }} 条</span><span>{{ pendingCount }} 条待处理</span><el-button link type="primary" :loading="validating" @click="runValidation">在线校验全部结果</el-button></div>
    </section>

    <el-row :gutter="16" class="work-area">
      <el-col :xs="24" :lg="7">
        <el-card shadow="never" class="queue-card">
          <template #header><div class="card-title"><span>标注队列</span><el-button link :loading="loading" @click="loadAll">刷新</el-button></div></template>
          <div class="queue-filter"><el-radio-group v-model="queueFilter" size="small"><el-radio-button label="ALL">全部</el-radio-button><el-radio-button label="PENDING">待处理</el-radio-button><el-radio-button label="DONE">已完成</el-radio-button></el-radio-group></div>
          <div class="queue-list"><button v-for="(item, index) in filteredAnnotations" :key="item.id" :class="['queue-item', { selected: item.id === current?.id }]" @click="selectAnnotation(item)"><span class="queue-index">{{ item.rowIndex + 1 }}</span><span class="queue-content"><b>数据项 {{ item.rowIndex + 1 }}</b><small>{{ item.labelName || item.labelCode || '未标注' }}</small></span><el-tag size="small" :type="annotationStatusType(item.status)">{{ annotationStatusText(item.status) }}</el-tag></button><el-empty v-if="!filteredAnnotations.length" description="暂无符合条件的数据" :image-size="64" /></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="17">
        <el-card shadow="never" class="editor-card" v-loading="loading">
          <template #header><div class="editor-header"><div><span class="section-kicker">ITEM {{ currentPosition + 1 }}</span><h2>数据项 {{ (current?.rowIndex ?? 0) + 1 }}</h2></div><div class="editor-tools"><el-button size="small" @click="skipCurrent" :disabled="!current">跳过</el-button><el-button size="small" type="warning" plain @click="markDispute" :disabled="!current">标记争议</el-button></div></div></template>
          <template v-if="current">
            <div v-if="isMedia" class="media-editor">
              <video v-if="isVideo && mediaUrl" :src="mediaUrl" controls class="media-preview" />
              <div v-else-if="mediaUrl" class="image-stage" @mousedown="startBox" @mousemove="moveBox" @mouseup="finishBox" @mouseleave="finishBox"><img :src="mediaUrl" class="media-preview" alt="待标注媒体" /><span v-if="box.width" class="bounding-box" :style="boxStyle" /></div>
              <el-empty v-else description="当前任务尚未关联媒体资源" :image-size="80" />
              <div class="media-fields"><el-alert v-if="!isVideo && mediaUrl" title="在图片上按住鼠标拖拽框选目标区域" type="info" :closable="false" show-icon /><el-form-item label="标注说明"><el-input v-model="draft.comment" type="textarea" :rows="3" placeholder="描述目标、时间段或其他标注信息" /></el-form-item><el-row :gutter="12" v-if="isVideo"><el-col :span="12"><el-form-item label="开始时间（秒）"><el-input-number v-model="draft.startSecond" :min="0" /></el-form-item></el-col><el-col :span="12"><el-form-item label="结束时间（秒）"><el-input-number v-model="draft.endSecond" :min="0" /></el-form-item></el-col></el-row></div>
            </div>
            <div v-else class="data-editor"><div class="data-source"><span class="source-label">原始数据</span><div v-if="currentRow" class="field-grid"><div v-for="(value, key) in currentRow" :key="key" class="field"><small>{{ key }}</small><p>{{ value === null || value === undefined || value === '' ? '（空）' : value }}</p></div></div><el-empty v-else description="无法读取当前数据行" :image-size="72" /></div><el-form-item label="选择标签" class="label-picker"><el-radio-group v-model="draft.labelCode"><el-radio v-for="label in labels" :key="label.id" :label="label.code">{{ label.name }}</el-radio></el-radio-group><el-empty v-if="!labels.length" description="该任务没有可用标签，请返回任务配置添加" :image-size="60" /></el-form-item><el-form-item label="标注备注"><el-input v-model="draft.comment" type="textarea" :rows="3" placeholder="填写判断依据或需要复核的情况" /></el-form-item></div>
            <div class="editor-footer"><el-alert v-if="validationMessage" :title="validationMessage" :type="validationOk ? 'success' : 'warning'" show-icon :closable="false" /><div class="submit-actions"><el-button @click="saveDraft" :loading="saving">保存当前草稿</el-button><el-button type="primary" @click="submitCurrent" :loading="saving" :disabled="current.status === 'APPROVED' || current.status === 'PUBLISHED'">提交此条标注 <el-icon><ArrowRight /></el-icon></el-button></div></div>
          </template>
          <el-empty v-else description="请从左侧队列选择待标注数据" :image-size="100" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Download } from '@element-plus/icons-vue'
import request from '../utils/request'
import { getActiveDatasetId, setActiveDatasetId } from '../utils/workspaceSync'

const route = useRoute(); const router = useRouter()
let taskId = Number(route.query.taskId); const collectionTaskId = Number(route.query.collectionTaskId); const mode = String(route.query.mode || 'TABLE').toUpperCase()
const task = ref(null); const annotations = ref([]); const labels = ref([]); const rows = ref([]); const mediaItems = ref([]); const current = ref(null); const loading = ref(false); const saving = ref(false); const validating = ref(false); const exporting = ref(false); const errorMessage = ref(''); const validationMessage = ref(''); const validationOk = ref(false); const queueFilter = ref('ALL')
const draft = reactive({ labelCode: '', comment: '', startSecond: 0, endSecond: 0 })
const box = reactive({ x: 0, y: 0, width: 0, height: 0 }); const drawing = ref(false); const boxStart = reactive({ x: 0, y: 0 })
const modeLabel = computed(() => ({ TEXT: '文本分类', TABLE: '表格标注', IMAGE: '图片框选', VIDEO: '视频时段标注' }[mode] || '数据标注'))
const isMedia = computed(() => ['IMAGE', 'VIDEO'].includes(mode)); const isVideo = computed(() => mode === 'VIDEO')
const currentPosition = computed(() => Math.max(0, annotations.value.findIndex(item => item.id === current.value?.id)))
const filteredAnnotations = computed(() => annotations.value.filter(item => queueFilter.value === 'ALL' || (queueFilter.value === 'DONE' ? ['SUBMITTED', 'IN_REVIEW', 'APPROVED', 'ARBITRATED', 'PUBLISHED'].includes(item.status) : !['SUBMITTED', 'IN_REVIEW', 'APPROVED', 'ARBITRATED', 'PUBLISHED'].includes(item.status))))
const completedCount = computed(() => annotations.value.filter(item => ['SUBMITTED', 'IN_REVIEW', 'APPROVED', 'ARBITRATED', 'PUBLISHED'].includes(item.status)).length)
const pendingCount = computed(() => Math.max(0, annotations.value.length - completedCount.value)); const progress = computed(() => annotations.value.length ? Math.round(completedCount.value / annotations.value.length * 100) : 0)
const currentRow = computed(() => {
  if (!current.value || !rows.value.length) return null
  const index = Number(current.value.rowIndex)
  if (!Number.isFinite(index)) return null
  // Existing tasks may persist rowIndex as either zero-based or one-based.
  return rows.value[index] || (index > 0 ? rows.value[index - 1] : null) || rows.value.find(row => Number(row?.rowIndex) === index || Number(row?.id) === index) || null
})
const mediaUrl = computed(() => { const item = mediaItems.value.find(x => x.id === current.value?.rowIndex || x.rowIndex === current.value?.rowIndex); return item?.mediaUrl || item?.url || '' })
const boxStyle = computed(() => ({ left: `${box.x}%`, top: `${box.y}%`, width: `${box.width}%`, height: `${box.height}%` }))
const taskStatusText = computed(() => ({ PENDING: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', IN_REVIEW: '审核中', PUBLISHED: '已发布' }[task.value?.status] || task.value?.status || '加载中'))
const taskStatusType = computed(() => task.value?.status === 'COMPLETED' || task.value?.status === 'PUBLISHED' ? 'success' : task.value?.status === 'IN_REVIEW' ? 'warning' : 'primary')
const annotationStatusText = s => ({ PENDING: '待标注', PRE_ANNOTATED: '预标注', SUBMITTED: '已提交', IN_REVIEW: '审核中', APPROVED: '已通过', REJECTED: '待重标', ARBITRATED: '已仲裁', PUBLISHED: '已发布' }[s] || s || '待标注')
const annotationStatusType = s => ['APPROVED', 'PUBLISHED'].includes(s) ? 'success' : ['SUBMITTED', 'IN_REVIEW', 'ARBITRATED'].includes(s) ? 'primary' : s === 'REJECTED' ? 'danger' : 'info'
const loadAll = async () => { loading.value = true; errorMessage.value = ''; try { if (!taskId) { const result = await request.get('/v1/annotation-tasks?page=1&size=100'); const taskRows = (result.records || []).filter(item => item.status !== 'CANCELLED'); const activeId = getActiveDatasetId(); const scoped = activeId ? taskRows.filter(item => String(item.datasetId) === String(activeId)) : []; const candidate = (scoped.length ? scoped : taskRows).find(item => item.status !== 'PUBLISHED') || (scoped.length ? scoped : taskRows)[0]; if (!candidate) { errorMessage.value = '当前默认数据集还没有标注任务，请先在任务配置页创建并派发任务'; return } taskId = candidate.id } task.value = await request.get(`/v1/annotation-tasks/${taskId}`); if (task.value.datasetId) setActiveDatasetId(task.value.datasetId); const list = await request.get(`/v1/annotation-tasks/${taskId}/annotations`); annotations.value = (list || []).map(item => ({ ...item, rowIndex: Number(item.rowIndex ?? 0) })); if (task.value.labelSchemaId) labels.value = await request.get(`/v1/label-schemas/${task.value.labelSchemaId}/children`); if (task.value.datasetId) { const [preview, media] = await Promise.all([request.get(`/v1/datasets/${task.value.datasetId}/preview`).catch(() => []), collectionTaskId ? request.get(`/v1/media-annotations/tasks/${collectionTaskId}`).catch(() => []) : Promise.resolve([])]); let parsed = preview; if (typeof parsed === 'string') { try { parsed = JSON.parse(parsed || '[]') } catch { parsed = [] } } rows.value = Array.isArray(parsed) ? parsed : (parsed?.rows || parsed?.data || []); mediaItems.value = media?.data || media || [] } if (!current.value && annotations.value.length) selectAnnotation(annotations.value[0]); else if (current.value) selectAnnotation(annotations.value.find(x => x.id === current.value.id) || annotations.value[0]) } catch (e) { errorMessage.value = e.message || '工作台数据加载失败' } finally { loading.value = false } }
const selectAnnotation = item => { current.value = item; draft.labelCode = item.labelCode || ''; draft.comment = item.comment || ''; draft.startSecond = 0; draft.endSecond = 0; box.x = 0; box.y = 0; box.width = 0; box.height = 0; validationMessage.value = '' }
const pointerPercent = event => { const rect = event.currentTarget.getBoundingClientRect(); return { x: Math.max(0, Math.min(100, (event.clientX - rect.left) / rect.width * 100)), y: Math.max(0, Math.min(100, (event.clientY - rect.top) / rect.height * 100)) } }
const startBox = event => { if (isVideo.value) return; const point = pointerPercent(event); drawing.value = true; boxStart.x = point.x; boxStart.y = point.y; box.x = point.x; box.y = point.y; box.width = 0; box.height = 0 }
const moveBox = event => { if (!drawing.value) return; const point = pointerPercent(event); box.x = Math.min(boxStart.x, point.x); box.y = Math.min(boxStart.y, point.y); box.width = Math.abs(point.x - boxStart.x); box.height = Math.abs(point.y - boxStart.y) }
const finishBox = () => { drawing.value = false }
const submitCurrent = async () => { if (!current.value) return; if (!isMedia.value && !draft.labelCode) return ElMessage.warning('请选择一个标签后再提交'); if (mode === 'IMAGE' && mediaUrl.value && (!box.width || !box.height)) return ElMessage.warning('请先在图片上框选标注区域'); if (isVideo.value && draft.endSecond <= draft.startSecond) return ElMessage.warning('结束时间必须大于开始时间'); saving.value = true; try { if (isMedia.value && collectionTaskId && mediaUrl.value) { await request.post('/v1/media-annotations', { taskId: collectionTaskId, mediaType: mode, mediaUrl: mediaUrl.value, boundingBoxes: isVideo.value ? null : JSON.stringify([{ x: box.x, y: box.y, width: box.width, height: box.height }]), keyFrames: isVideo.value ? JSON.stringify([{ startSecond: draft.startSecond, endSecond: draft.endSecond }]) : null, comment: draft.comment, status: 'COMPLETED' }) } const result = await request.post(`/v1/annotations/${current.value.id}/submit`, { labelCode: draft.labelCode, labelName: labels.value.find(x => x.code === draft.labelCode)?.name || draft.labelCode, comment: draft.comment }); Object.assign(current.value, result || {}, { status: result?.status || 'SUBMITTED', labelCode: draft.labelCode, comment: draft.comment }); ElMessage.success('标注已提交'); const next = annotations.value.find(x => !['SUBMITTED', 'IN_REVIEW', 'APPROVED', 'ARBITRATED', 'PUBLISHED'].includes(x.status)); if (next) selectAnnotation(next) } catch (e) { ElMessage.error(e.message || '提交失败') } finally { saving.value = false } }
const saveDraft = () => { if (current.value) { current.value.labelCode = draft.labelCode; current.value.comment = draft.comment; ElMessage.success('当前标注草稿已保存在本次会话') } }
const skipCurrent = () => { const next = annotations.value[(currentPosition.value + 1) % annotations.value.length]; if (next) selectAnnotation(next) }
const markDispute = async () => { if (!current.value) return; try { const reason = await ElMessageBox.prompt('填写争议原因，提交后将进入复核队列。', '标记争议', { inputPlaceholder: '例如：标签边界不清晰' }); await request.post(`/v1/annotations/${current.value.id}/submit`, { labelCode: draft.labelCode, labelName: labels.value.find(x => x.code === draft.labelCode)?.name || draft.labelCode, comment: `[争议] ${reason.value} ${draft.comment || ''}` }); current.value.status = 'IN_REVIEW'; current.value.comment = `[争议] ${reason.value}`; ElMessage.success('已标记为争议，等待复核') } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '争议标记失败') } }
const runValidation = async () => { if (!taskId) return; validating.value = true; try { const result = await request.post(`/v1/annotation-tasks/${taskId}/auto-validate`); validationOk.value = !result?.failedCount; validationMessage.value = result?.message || `在线校验完成，${result?.failedCount || 0} 条需要复核`; await loadAll() } catch (e) { validationOk.value = false; validationMessage.value = e.message || '在线校验失败' } finally { validating.value = false } }
const exportAnnotations = async () => { if (!taskId) return; exporting.value = true; try { const result = await request.post(`/v1/annotation-tasks/${taskId}/export`); const fileUrl = new URL(result.fileUrl); const blob = await request.get(fileUrl.pathname.replace(/^\/api/, ''), { responseType: 'blob' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = `${task.value?.name || 'annotation'}_annotated.csv`; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url); ElMessage.success(`已导出 ${result.outputRows || 0} 行标注结果`) } catch (e) { ElMessage.error(e.message || '标注结果导出失败') } finally { exporting.value = false } }
watch(() => route.query.taskId, loadAll); onMounted(loadAll)
</script>

<style scoped>
.page-heading,.editor-header,.card-title,.progress-meta,.heading-actions{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:18px}.page-heading h1{margin:6px 0;font-size:26px}.page-heading p{margin:0;color:#86909c;font-size:13px}.eyebrow,.section-kicker{color:#165dff;font-size:12px;font-weight:700;letter-spacing:.08em}.steps{display:flex;align-items:center;margin-bottom:18px}.step{display:flex;align-items:center;gap:8px;color:#86909c;font-size:14px}.step.active,.step.done{color:#165dff;font-weight:600}.step b{display:grid;place-items:center;width:28px;height:28px;border-radius:50%;background:#f2f3f5}.step.active b,.step.done b{background:#e8f3ff;color:#165dff}.line{height:1px;flex:0 1 120px;margin:0 14px;background:#e5e6eb}.line.active{background:#165dff}.error-alert{margin-bottom:16px}.progress-panel{padding:16px 20px;margin-bottom:16px;background:#fff;border:1px solid #e5e6eb;border-radius:8px;box-shadow:0 1px 4px #0000000a}.progress-panel>div:first-child{display:flex;align-items:baseline;gap:10px}.progress-panel strong{font-size:24px;color:#1d2129}.progress-panel span{color:#86909c;font-size:13px}.progress-panel .el-progress{margin:12px 0 8px}.progress-meta{font-size:12px}.queue-card,.editor-card{min-height:590px}.queue-filter{padding-bottom:12px;border-bottom:1px solid #e5e6eb}.queue-list{padding-top:8px}.queue-item{width:100%;display:flex;align-items:center;gap:10px;padding:12px 8px;border:0;border-bottom:1px solid #f2f3f5;background:#fff;text-align:left;cursor:pointer}.queue-item:hover,.queue-item.selected{background:#e8f3ff}.queue-index{width:28px;height:28px;display:grid;place-items:center;border-radius:50%;background:#f2f3f5;color:#4e5969;font-size:12px}.queue-item.selected .queue-index{background:#165dff;color:#fff}.queue-content{min-width:0;flex:1}.queue-content b,.queue-content small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.queue-content b{color:#1d2129;font-size:14px}.queue-content small{margin-top:4px;color:#86909c;font-size:12px}.editor-header h2{margin:5px 0 0;font-size:18px}.editor-tools{display:flex;gap:8px}.data-source{padding:16px;background:#f7f8fa;border:1px solid #e5e6eb;border-radius:6px}.source-label{display:block;margin-bottom:12px;color:#86909c;font-size:12px}.field-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.field{padding:10px;background:#fff;border:1px solid #e5e6eb;border-radius:4px}.field small{color:#86909c}.field p{margin:5px 0 0;color:#1d2129;word-break:break-word}.label-picker{margin-top:20px}.label-picker :deep(.el-radio-group){display:flex;flex-wrap:wrap;gap:10px}.editor-footer{margin-top:20px;padding-top:16px;border-top:1px solid #e5e6eb}.submit-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:14px}.media-editor{display:grid;grid-template-columns:minmax(0,1.3fr) minmax(260px,1fr);gap:20px}.media-preview{width:100%;max-height:420px;object-fit:contain;background:#1d2129;border-radius:6px}.media-fields{padding:8px}.editor-card :deep(.el-card__body){min-height:480px}@media(max-width:900px){.page-heading,.heading-actions{align-items:flex-start;flex-direction:column}.line{flex:1}.media-editor{grid-template-columns:1fr}.queue-card,.editor-card{min-height:auto}.field-grid{grid-template-columns:1fr}}
 .image-stage{position:relative;cursor:crosshair;line-height:0}.bounding-box{position:absolute;border:2px solid #165dff;background:#165dff33;pointer-events:none}
</style>
