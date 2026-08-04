<template>
  <div class="reports-page">
    <div class="page-header">
      <div>
        <span class="eyebrow">CUSTOM REPORT CENTER</span>
        <h1>自定义报表</h1>
        <p>按数据集和业务主题配置报表内容，生成可追溯的报表实例。趋势看板中的实时分析不在此页面执行。</p>
      </div>
      <div class="header-actions">
        <el-button @click="router.push('/trend-dashboard')">返回趋势看板</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">保存报表模板</el-button>
      </div>
    </div>

    <div class="report-grid">
      <section class="panel builder-panel">
        <div class="panel-title"><strong>报表配置</strong><el-tag type="info" effect="plain">独立配置</el-tag></div>
        <el-form label-position="top">
          <el-form-item label="报表名称"><el-input v-model="form.name" placeholder="例如：月度需求趋势报告" /></el-form-item>
          <el-form-item label="数据集"><el-select v-model="form.datasetId" filterable placeholder="选择已解析数据集" @change="loadReportData"><el-option v-for="item in datasets" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="关联预测任务（可选）"><el-select v-model="form.predictionTaskId" clearable placeholder="选择预测任务"><el-option v-for="item in predictionTasks" :key="item.id" :label="item.name || `预测任务 #${item.id}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="导出格式"><el-radio-group v-model="form.exportFormat"><el-radio-button label="JSON">JSON</el-radio-button><el-radio-button label="HTML">HTML</el-radio-button><el-radio-button label="PDF">PDF</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="报表内容"><el-checkbox-group v-model="form.sections"><el-checkbox label="TREND">趋势分析</el-checkbox><el-checkbox label="COMPARISON">对比分析</el-checkbox><el-checkbox label="ANOMALY">异常检测</el-checkbox><el-checkbox label="ROOT_CAUSE">根因分析</el-checkbox><el-checkbox label="PREDICTION">预测结果</el-checkbox></el-checkbox-group></el-form-item>
          <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" placeholder="填写报表用途或交付说明" /></el-form-item>
          <el-button type="primary" :loading="generating" :disabled="!form.datasetId" @click="generateReport">生成报表</el-button>
        </el-form>
      </section>

      <section class="panel preview-panel">
        <div class="panel-title"><strong>报表预览</strong><el-tag :type="report ? 'success' : 'info'" effect="plain">{{ report ? '已生成' : '待生成' }}</el-tag></div>
        <div v-if="report" class="report-preview">
          <div class="preview-metrics"><div><span>报表编号</span><strong>#{{ report.id || '—' }}</strong></div><div><span>状态</span><strong>{{ report.status || 'COMPLETED' }}</strong></div><div><span>格式</span><strong>{{ report.exportFormat || form.exportFormat }}</strong></div></div>
          <h2>{{ report.title || form.name }}</h2>
          <pre>{{ prettyContent(report.contentJson) }}</pre>
          <el-button v-if="report.fileUrl" link type="primary" @click="downloadReport">下载报表文件</el-button>
        </div>
        <el-empty v-else description="完成配置并生成报表后显示结果" :image-size="96" />
      </section>
    </div>

    <section class="panel list-panel"><div class="panel-title"><strong>已保存模板</strong><el-button text type="primary" @click="loadTemplates">刷新</el-button></div><el-table :data="templates" v-loading="loadingTemplates" empty-text="暂无报表模板"><el-table-column prop="name" label="模板名称" min-width="210" /><el-table-column prop="reportType" label="类型" width="120" /><el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip /><el-table-column prop="updatedAt" label="更新时间" width="180" /><el-table-column label="操作" width="220"><template #default="scope"><el-button link type="primary" @click="useTemplate(scope.row)">使用</el-button><el-button link type="danger" @click="removeTemplate(scope.row)">删除</el-button></template></el-table-column></el-table></section>
    <section class="panel list-panel"><div class="panel-title"><strong>生成记录</strong><el-button text type="primary" @click="loadInstances">刷新</el-button></div><el-table :data="instances" v-loading="loadingInstances" empty-text="暂无生成记录"><el-table-column prop="title" label="报表标题" min-width="240" /><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="exportFormat" label="格式" width="100" /><el-table-column prop="generatedAt" label="生成时间" width="200" /><el-table-column label="文件" width="130"><template #default="scope"><el-button v-if="scope.row.fileUrl" link type="primary" @click="openFile(scope.row)">打开文件</el-button><span v-else class="muted">已入库</span></template></el-table-column></el-table></section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { getActiveDatasetId, onDatasetSync } from '../utils/workspaceSync'

const router = useRouter()
const datasets = ref([]); const predictionTasks = ref([]); const templates = ref([]); const instances = ref([]); const report = ref(null)
const saving = ref(false); const generating = ref(false); const loadingTemplates = ref(false); const loadingInstances = ref(false)
const form = reactive({ name: '市场需求自定义报表', description: '', datasetId: '', predictionTaskId: '', exportFormat: 'JSON', sections: ['TREND', 'COMPARISON', 'ANOMALY', 'ROOT_CAUSE', 'PREDICTION'] })
const unwrap = value => value?.data ?? value
const prettyContent = content => { if (!content) return '报表内容将在生成后由后端写入。'; try { return JSON.stringify(typeof content === 'string' ? JSON.parse(content) : content, null, 2) } catch { return String(content) } }
const loadReportData = async () => { if (!form.datasetId) return; await Promise.all([loadTemplates(), loadInstances()]) }
const loadTemplates = async () => { loadingTemplates.value = true; try { templates.value = unwrap(await request.get('/v1/dashboards/reports/templates', { params: { datasetId: form.datasetId || undefined, reportType: 'CUSTOM' } })) || [] } catch (e) { ElMessage.error('模板加载失败') } finally { loadingTemplates.value = false } }
const loadInstances = async () => { loadingInstances.value = true; try { instances.value = unwrap(await request.get('/v1/dashboards/reports/instances', { params: { datasetId: form.datasetId || undefined } })) || [] } catch (e) { ElMessage.error('生成记录加载失败') } finally { loadingInstances.value = false } }
const saveTemplate = async () => { if (!form.name.trim() || !form.datasetId) return ElMessage.warning('请填写报表名称并选择数据集'); saving.value = true; try { await request.post('/v1/dashboards/reports/templates', { name: form.name, description: form.description, datasetId: form.datasetId, reportType: 'CUSTOM', sectionsJson: JSON.stringify(form.sections), parametersJson: JSON.stringify({ exportFormat: form.exportFormat }) }); ElMessage.success('报表模板已保存'); await loadTemplates() } catch (e) { ElMessage.error(e?.response?.data?.message || '模板保存失败') } finally { saving.value = false } }
const generateReport = async () => { if (!form.datasetId) return ElMessage.warning('请选择数据集'); generating.value = true; try { report.value = unwrap(await request.post('/v1/dashboards/reports/generate', { datasetId: form.datasetId, predictionTaskId: form.predictionTaskId || undefined, exportFormat: form.exportFormat })); ElMessage.success('自定义报表生成成功'); await loadInstances() } catch (e) { ElMessage.error(e?.response?.data?.message || '报表生成失败') } finally { generating.value = false } }
const useTemplate = row => { form.name = row.name || form.name; form.description = row.description || ''; form.datasetId = row.datasetId || form.datasetId; try { form.sections = JSON.parse(row.sectionsJson || '[]') } catch { form.sections = ['TREND'] }; ElMessage.success('已载入模板配置') }
const removeTemplate = async row => { try { await ElMessageBox.confirm(`确定删除模板“${row.name}”吗？`, '删除确认', { type: 'warning' }); await request.delete(`/v1/dashboards/reports/templates/${row.id}`); ElMessage.success('模板已删除'); await loadTemplates() } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error('模板删除失败') } }
const openFile = row => { if (row.fileUrl) window.open(row.fileUrl, '_blank', 'noopener') }; const downloadReport = () => openFile(report.value)
onDatasetSync(id => { if (id) { form.datasetId = Number(id); loadReportData() } })
onMounted(async () => { try { const [ds, tasks] = await Promise.all([request.get('/v1/datasets?page=1&size=50'), request.get('/v1/predictions?page=1&size=50')]); const d = unwrap(ds); const t = unwrap(tasks); datasets.value = d?.records || d || []; predictionTasks.value = t?.records || t || []; const active = getActiveDatasetId(); const first = datasets.value.find(item => String(item.id) === String(active)) || datasets.value.find(item => item.status === 'PARSED'); if (first) { form.datasetId = first.id; await loadReportData() } } catch (e) { ElMessage.error('报表基础数据加载失败') } })
</script>

<style scoped>
.reports-page { min-height: 100%; padding: 24px; background: #f7f8fa; color: #1d2129; }.page-header { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 16px; padding: 22px 24px; background: #fff; border: 1px solid #e5e6eb; border-radius: 8px; }.eyebrow { color: #165dff; font-size: 12px; font-weight: 700; } h1 { margin: 8px 0; font-size: 24px; line-height: 1.2; }.page-header p { margin: 0; color: #86909c; font-size: 14px; }.header-actions { display: flex; align-items: flex-start; gap: 8px; flex-shrink: 0; }.report-grid { display: grid; grid-template-columns: 390px minmax(0, 1fr); gap: 16px; margin-bottom: 16px; }.panel { padding: 20px; background: #fff; border: 1px solid #e5e6eb; border-radius: 8px; box-shadow: 0 1px 4px rgb(0 0 0 / 6%); }.panel-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 18px; font-size: 16px; }.builder-panel :deep(.el-select), .builder-panel :deep(.el-input) { width: 100%; }.builder-panel :deep(.el-checkbox-group) { display: grid; gap: 10px; }.report-preview { min-height: 420px; }.preview-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 22px; }.preview-metrics div { padding: 14px; background: #f7f8fa; border-radius: 6px; }.preview-metrics span, .preview-metrics strong { display: block; }.preview-metrics span { color: #86909c; font-size: 12px; }.preview-metrics strong { margin-top: 7px; font-size: 16px; }.report-preview h2 { margin: 0 0 12px; font-size: 20px; }.report-preview pre { min-height: 260px; max-height: 430px; margin: 0 0 12px; padding: 14px; overflow: auto; background: #f7f8fa; border-radius: 6px; color: #4e5969; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; }.list-panel { margin-bottom: 16px; }.muted { color: #86909c; font-size: 12px; } @media (max-width: 1000px) { .report-grid { grid-template-columns: 1fr; }.page-header { flex-direction: column; }.header-actions { align-items: stretch; }.preview-metrics { grid-template-columns: 1fr; } }
</style>
