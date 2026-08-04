<template>
  <div class="import-page page-container">
    <header class="page-heading">
      <div><span class="eyebrow">STEP 1 / DATA INGESTION</span><h1>多源数据导入</h1><p>导入多模态数据，完成解析、清洗和数据集准备。</p></div>
      <el-button type="primary" :disabled="!selectedDataset" @click="goConfig">下一步：配置标签与派发任务 <el-icon><ArrowRight /></el-icon></el-button>
    </header>

    <div class="steps"><div class="step active"><b>1</b><span>上传清洗数据集</span></div><div class="line" /><div class="step"><b>2</b><span>配置标签与派发任务</span></div><div class="line" /><div class="step"><b>3</b><span>进入专属工作台</span></div></div>

    <el-row :gutter="16">
      <el-col :span="10"><el-card class="upload-card" shadow="never">
        <template #header><div class="card-title"><span>导入数据</span><el-tag size="small">多模态</el-tag></div></template>
        <el-upload drag multiple :show-file-list="false" :http-request="uploadFile" :disabled="uploading" accept=".csv,.xls,.xlsx,.json,.txt,.jpg,.jpeg,.png,.mp4,.mov,.mp3,.wav">
          <el-icon class="upload-icon"><UploadFilled /></el-icon><div class="el-upload__text">拖拽文件到此处，或 <em>选择文件</em></div><div class="upload-tip">CSV / XLSX / JSON / TXT / JPG / PNG / MP4 / MP3</div>
        </el-upload>
        <el-alert v-if="uploading" title="正在上传并解析，请稍候" type="info" :closable="false" show-icon />
        <div class="clean-box"><div class="section-label">自动清洗规则</div><el-checkbox-group v-model="cleanConfig"><el-checkbox label="removeEmpty">移除空行</el-checkbox><el-checkbox label="removeDuplicate">去除重复</el-checkbox><el-checkbox label="validateFormat">格式校验</el-checkbox></el-checkbox-group><div class="clean-actions"><el-button type="primary" plain :disabled="!selectedDataset" :loading="cleaning" @click="runCleaning">执行清洗并预览</el-button><el-button type="primary" :disabled="!selectedDataset" :loading="applying" @click="applyCleaning">生成清洗文件</el-button></div><el-button v-if="cleanResult.fileUrl" link type="success" :icon="Download" @click="downloadCleanResult">下载清洗结果 CSV</el-button></div>
      </el-card></el-col>
      <el-col :span="14"><el-card shadow="never"><template #header><div class="card-title"><span>数据集列表</span><el-button link :loading="loading" @click="loadDatasets">刷新</el-button></div></template>
        <el-table v-loading="loading" :data="datasets" stripe highlight-current-row @current-change="selectDataset"><el-table-column prop="name" label="数据集" min-width="190" show-overflow-tooltip /><el-table-column prop="fileType" label="类型" width="90" /><el-table-column prop="rowCount" label="行数" width="80" /><el-table-column prop="columnCount" label="字段" width="80" /><el-table-column prop="status" label="状态" width="100"><template #default="s"><el-tag size="small" :type="statusType(s.row.status)">{{ statusText(s.row.status) }}</el-tag></template></el-table-column><el-table-column label="操作" width="150"><template #default="s"><el-button link type="primary" :disabled="s.row.status !== 'PARSED'" @click="preview(s.row)">预览</el-button><el-button link type="danger" @click="removeDataset(s.row)">删除</el-button></template></el-table-column></el-table>
        <el-empty v-if="!loading && !datasets.length" description="暂无数据集，请先上传" :image-size="80" />
      </el-card></el-col>
    </el-row>

    <el-card v-if="selectedDataset" class="preview-card" shadow="never"><template #header><div class="card-title"><span>数据预览与清洗结果</span><div class="header-result"><el-tag type="success">{{ selectedDataset.name }}</el-tag><el-button v-if="cleanResult.fileUrl" size="small" type="success" :icon="Download" @click="downloadCleanResult">下载清洗结果</el-button></div></div></template><div class="stats"><div><b>{{ selectedDataset.rowCount ?? '-' }}</b><span>解析行数</span></div><div><b>{{ selectedDataset.columnCount ?? '-' }}</b><span>字段数量</span></div><div><b>{{ cleanSummary.duplicateRows ?? 0 }}</b><span>重复行</span></div><div><b>{{ cleanSummary.outputRows ?? '-' }}</b><span>清洗后可用</span></div></div><el-table v-if="previewRows.length" :data="previewRows" height="280" stripe><el-table-column v-for="field in previewFields" :key="field" :prop="field" :label="field" min-width="140" show-overflow-tooltip /></el-table><el-empty v-else description="选择已解析数据集查看预览" :image-size="70" /></el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Download, UploadFilled } from '@element-plus/icons-vue'
import request from '../utils/request'
import { getActiveDatasetId, setActiveDatasetId } from '../utils/workspaceSync'

const router = useRouter(); const datasets = ref([]); const selectedDataset = ref(null); const previewRows = ref([]); const previewFields = ref([]); const cleanSummary = ref({}); const cleanResult = ref({}); const cleanTaskId = ref(null); const cleanConfig = ref(['removeEmpty', 'removeDuplicate', 'validateFormat']); const loading = ref(false); const uploading = ref(false); const cleaning = ref(false); const applying = ref(false)
const statusText = s => ({ PARSED: '已解析', UPLOADED: '待解析', PARSING: '解析中', PROCESSING: '解析中', PARSE_FAILED: '解析失败', FAILED: '失败' }[s] || s || '未知')
const statusType = s => ({ PARSED: 'success', UPLOADED: 'warning', PARSING: 'primary', PROCESSING: 'primary', PARSE_FAILED: 'danger', FAILED: 'danger' }[s] || 'info')
const loadDatasets = async () => {
  loading.value = true
  try {
    const r = await request.get('/v1/datasets?page=1&size=100')
    datasets.value = r.records || []
    const activeId = getActiveDatasetId()
    const preferred = datasets.value.find(row => String(row.id) === String(activeId) && row.status === 'PARSED')
      || datasets.value.find(row => row.status === 'PARSED')
    if (preferred && (!selectedDataset.value || !datasets.value.some(row => row.id === selectedDataset.value.id))) {
      setActiveDatasetId(preferred.id)
      await preview(preferred)
    }
  } catch (e) { ElMessage.error(e.message || '数据集加载失败') } finally { loading.value = false }
}
const selectDataset = row => { if (row) { selectedDataset.value = row; setActiveDatasetId(row.id); if (row.status === 'PARSED') preview(row) } }
const preview = async row => {
  selectedDataset.value = row
  previewRows.value = []
  previewFields.value = []
  try {
    if (row.status !== 'PARSED') return
    const [raw, fields] = await Promise.all([request.get(`/v1/datasets/${row.id}/preview`), request.get(`/v1/datasets/${row.id}/fields`)]);
    let parsed = raw
    if (typeof raw === 'string') parsed = raw.trim() ? JSON.parse(raw) : []
    // Upload parsing is asynchronous; an empty preview is a valid intermediate state.
    previewRows.value = Array.isArray(parsed) ? parsed : (parsed?.rows || [])
    previewFields.value = Array.isArray(fields) ? fields.map(f => f.fieldName || f.name).filter(Boolean) : []
    if (!previewFields.value.length && previewRows.value.length) previewFields.value = Object.keys(previewRows.value[0] || {})
  } catch (e) { ElMessage.error(e.message || '数据预览失败') }
}
const waitForParsedDataset = async id => {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    await new Promise(resolve => setTimeout(resolve, 500))
    await loadDatasets()
    const row = datasets.value.find(x => String(x.id) === String(id))
    if (row?.status === 'PARSED' || row?.status === 'PARSE_FAILED') return row
  }
  return datasets.value.find(x => String(x.id) === String(id))
}
const uploadFile = async options => { uploading.value = true; const form = new FormData(); form.append('file', options.file); try { const media = /\.(jpg|jpeg|png|mp4|mov|mp3|wav)$/i.test(options.file.name); const result = media ? await request.post('/v1/collection-tasks/media-upload', form, { headers: { 'Content-Type': 'multipart/form-data' } }) : await request.post('/v1/datasets/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } }); if (media) { const old = JSON.parse(sessionStorage.getItem('river-import-media') || '[]'); old.push(result); sessionStorage.setItem('river-import-media', JSON.stringify(old)); ElMessage.success('媒体资源已导入，下一步可配置媒体任务') } else { ElMessage.info('数据集上传成功，正在解析'); setActiveDatasetId(result?.id); await loadDatasets(); const row = await waitForParsedDataset(result?.id); if (row?.status === 'PARSED') { setActiveDatasetId(row.id); await preview(row); ElMessage.success('数据集解析完成，可预览和清洗') } else if (row?.status === 'PARSE_FAILED') ElMessage.error('数据集解析失败，请检查文件格式'); else ElMessage.info('数据集仍在解析中，可稍后点击刷新') } } catch (e) { ElMessage.error(e.message || '文件上传失败') } finally { uploading.value = false } }
const getOrCreateCleaningTask = async () => { let tasks = await request.get('/v1/collection-tasks?page=1&size=100'); let task = (tasks.records || []).find(x => x.datasetId === selectedDataset.value.id && x.sourceType === 'DATASET'); if (!task) task = await request.post('/v1/collection-tasks', { name: `${selectedDataset.value.name} 清洗任务`, sourceType: 'DATASET', datasetId: selectedDataset.value.id }); cleanTaskId.value = task.id; return task }
const cleaningOptions = () => Object.fromEntries(['removeEmpty', 'removeDuplicate', 'validateFormat'].map(k => [k, cleanConfig.value.includes(k)]))
const runCleaning = async () => { if (!selectedDataset.value) return; cleaning.value = true; try { const task = await getOrCreateCleaningTask(); const result = await request.post(`/v1/collection-tasks/${task.id}/clean-preview`, cleaningOptions()); cleanSummary.value = result || {}; cleanResult.value = {}; previewRows.value = Array.isArray(result?.previewRows) ? result.previewRows : []; previewFields.value = Array.isArray(result?.previewFields) ? result.previewFields : (previewRows.value.length ? Object.keys(previewRows.value[0]) : []); ElMessage.success(previewRows.value.length ? '清洗预览完成' : '清洗完成，但没有可预览的数据') } catch (e) { ElMessage.error(e.message || '清洗失败') } finally { cleaning.value = false } }
const applyCleaning = async () => { if (!selectedDataset.value) return; applying.value = true; try { const task = cleanTaskId.value ? { id: cleanTaskId.value } : await getOrCreateCleaningTask(); const result = await request.post(`/v1/collection-tasks/${task.id}/clean-apply`, cleaningOptions()); cleanResult.value = result || {}; cleanSummary.value = { ...cleanSummary.value, ...result }; if (result?.outputDatasetId) setActiveDatasetId(result.outputDatasetId); ElMessage.success(`清洗文件已生成，共 ${result?.outputRows ?? 0} 条，可直接下载`) } catch (e) { ElMessage.error(e.message || '清洗文件生成失败') } finally { applying.value = false } }
const downloadCleanResult = async () => { if (!cleanResult.value.fileUrl) return; try { const fileUrl = new URL(cleanResult.value.fileUrl); const endpoint = fileUrl.pathname.replace(/^\/api/, ''); const blob = await request.get(endpoint, { responseType: 'blob' }); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${selectedDataset.value?.name || 'dataset'}_cleaned.csv`; document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url); ElMessage.success('清洗结果已下载') } catch (e) { ElMessage.error(e.message || '清洗结果下载失败') } }
const removeDataset = async row => { try { await ElMessageBox.confirm(`确认删除数据集“${row.name}”？`, '删除数据集', { type: 'warning' }); await request.delete(`/v1/datasets/${row.id}`); if (selectedDataset.value?.id === row.id) selectedDataset.value = null; await loadDatasets(); ElMessage.success('数据集已删除') } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '删除失败') } }
const goConfig = () => router.push({ path: '/collection-annotation/config', query: { datasetId: cleanResult.value.outputDatasetId || selectedDataset.value.id } })
onMounted(loadDatasets)
</script>

<style scoped>
.page-heading,.card-title,.header-result,.clean-actions{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:20px}.page-heading h1{margin:6px 0;font-size:26px}.page-heading p,.upload-tip{color:var(--river-muted,#86909c);font-size:13px}.eyebrow{color:#165dff;font-size:12px;font-weight:700;letter-spacing:.08em}.steps{display:flex;align-items:center;margin-bottom:20px}.step{display:flex;align-items:center;gap:8px;color:#86909c;font-size:14px}.step.active{color:#165dff;font-weight:600}.step b{display:grid;place-items:center;width:28px;height:28px;border-radius:50%;background:#f2f3f5}.step.active b{background:#e8f3ff;color:#165dff}.line{height:1px;flex:0 1 120px;margin:0 14px;background:#e5e6eb}.import-page :deep(.el-row){align-items:flex-start}.import-page :deep(.el-col){min-width:0}.upload-card{height:auto}.upload-icon{font-size:34px;color:#165dff}.upload-tip{text-align:center;margin-top:8px}.clean-box{display:grid;gap:12px;margin-top:18px;padding-top:16px;border-top:1px solid #e5e6eb}.clean-actions{justify-content:flex-start;gap:8px}.section-label{font-weight:600}.import-page :deep(.el-table){max-height:620px;overflow:auto}.preview-card{margin-top:16px}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}.stats div{padding:14px;background:#f7f8fa;border:1px solid #e5e6eb;border-radius:6px}.stats b,.stats span{display:block}.stats b{font-size:22px;color:#1d2129}.stats span{margin-top:4px;color:#86909c;font-size:12px}@media(max-width:900px){.page-heading{align-items:flex-start;flex-direction:column}.stats{grid-template-columns:repeat(2,1fr)}.line{flex:1}.header-result{align-items:flex-end;flex-direction:column;gap:6px}.import-page :deep(.el-col){width:100%;max-width:100%;flex:0 0 100%}}
</style>
