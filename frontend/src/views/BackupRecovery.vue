<template>
  <div class="backup-page page-container">
    <header class="page-heading"><div><span class="eyebrow">BACKUP & RECOVERY CENTER</span><h1>备份恢复中心</h1><p>独立管理数据备份、备份状态、恢复操作和历史版本，不与权限管理混用。</p></div><el-button @click="router.push('/security-audit')">返回审计中心</el-button></header>
    <div class="stats"><el-card shadow="never"><small>备份总数</small><strong>{{ backups.length }}</strong></el-card><el-card shadow="never"><small>最近备份</small><strong>{{ latestTime }}</strong></el-card><el-card shadow="never"><small>服务状态</small><strong>{{ status?.status || '未知' }}</strong></el-card></div>
    <el-card shadow="never"><template #header><div class="panel-title"><span>备份恢复策略</span><el-button type="primary" :loading="creating" @click="createBackup">立即创建全量备份</el-button></div></template><el-alert title="恢复操作会覆盖当前运行数据，请确认备份版本和恢复范围后再执行。" type="warning" :closable="false"/><el-table :data="backups" stripe class="backup-table" empty-text="暂无备份记录"><el-table-column prop="backupId" label="备份 ID" min-width="230"/><el-table-column prop="backupType" label="类型" width="100"/><el-table-column prop="status" label="状态" width="110"/><el-table-column prop="createdAt" label="创建时间" min-width="180"/><el-table-column prop="size" label="大小" width="120"/><el-table-column label="操作" width="220" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="restore(row)">恢复</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table></el-card>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
const router = useRouter(), backups = ref([]), status = ref({}), creating = ref(false)
const latestTime = computed(() => backups.value[0]?.createdAt || '暂无')
async function load() { try { const [rows, info] = await Promise.all([request.get('/v1/backups'), request.get('/v1/backups/status')]); backups.value = Array.isArray(rows) ? rows : []; status.value = info || {} } catch (e) { ElMessage.error(`备份数据加载失败：${e.message || '接口不可用'}`) } }
async function createBackup() { creating.value = true; try { await request.post('/v1/backups/create?type=FULL'); await load(); ElMessage.success('全量备份已创建') } catch (e) { ElMessage.error(`备份失败：${e.message || '接口不可用'}`) } finally { creating.value = false } }
async function restore(row) { try { await ElMessageBox.confirm(`确认恢复备份 ${row.backupId}？此操作会覆盖当前数据。`, '高风险操作', { type: 'warning' }); await request.post(`/v1/backups/${row.backupId}/restore`); await load(); ElMessage.success('备份恢复完成') } catch (e) { if (e !== 'cancel') ElMessage.error(`恢复失败：${e.message || '接口不可用'}`) } }
async function remove(row) { try { await ElMessageBox.confirm(`确认删除备份 ${row.backupId}？`, '删除备份', { type: 'warning' }); await request.delete(`/v1/backups/${row.backupId}`); await load(); ElMessage.success('备份已删除') } catch (e) { if (e !== 'cancel') ElMessage.error(`删除失败：${e.message || '接口不可用'}`) } }
onMounted(load)
</script>
<style scoped>.page-heading,.panel-title{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:18px}.page-heading h1{margin:6px 0;font-size:26px}.page-heading p{margin:0;color:#86909c;font-size:13px}.eyebrow{color:#165dff;font-size:12px;font-weight:700;letter-spacing:.08em}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:16px}.stats small{display:block;color:#86909c}.stats strong{display:block;margin-top:10px;font-size:24px;color:#1d2129}.backup-table{margin-top:18px}@media(max-width:700px){.page-heading{align-items:flex-start;flex-direction:column}.stats{grid-template-columns:1fr}}
</style>
