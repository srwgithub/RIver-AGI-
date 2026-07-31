<template>
  <div class="security-admin">
    <div class="page-heading"><div><span class="eyebrow">RIver AGI / GOVERNANCE</span><h2>安全管理后台</h2><p>统一管理安全策略、数据分级、权限和备份恢复。</p></div><el-button type="primary" @click="createBackup">立即备份</el-button></div>
    <el-row :gutter="18">
      <el-col :span="12"><el-card><template #header><div class="card-heading"><span>安全策略与数据分级</span><el-button link type="primary" @click="openPolicy()">新增策略</el-button></div></template><el-table :data="policies" stripe><el-table-column prop="name" label="策略名称" /><el-table-column prop="policyType" label="类型" /><el-table-column prop="classification" label="数据级别" /><el-table-column label="状态"><template #default="s"><el-switch v-model="s.row.enabled" @change="savePolicy(s.row)" /></template></el-table-column><el-table-column label="操作"><template #default="s"><el-button link @click="openPolicy(s.row)">编辑</el-button><el-button link type="danger" @click="removePolicy(s.row)">删除</el-button></template></el-table-column></el-table></el-card></el-col>
      <el-col :span="12"><el-card><template #header>角色与权限</template><el-table :data="roles" stripe><el-table-column prop="name" label="角色" /><el-table-column prop="code" label="编码" /><el-table-column prop="description" label="说明" /></el-table><el-divider /><el-table :data="permissions" size="small"><el-table-column prop="name" label="权限" /><el-table-column prop="code" label="权限码" /><el-table-column prop="resourcePath" label="资源路径" /></el-table></el-card></el-col>
    </el-row>
    <el-card id="backup-section" ref="backupSectionRef" class="backup-card"><template #header><div class="card-heading"><span>数据备份与恢复</span><span class="muted">定时备份：每日 03:00，最多保留 10 份</span></div></template><el-table :data="backups" stripe><el-table-column prop="backupId" label="备份编号" /><el-table-column prop="type" label="类型" /><el-table-column prop="status" label="状态" /><el-table-column prop="sizeBytes" label="大小" /><el-table-column prop="createdAt" label="创建时间" /><el-table-column label="操作"><template #default="s"><el-button link type="warning" :disabled="s.row.status !== 'COMPLETED'" @click="restoreBackup(s.row)">恢复</el-button></template></el-table-column></el-table></el-card>
    <el-dialog v-model="policyDialog" title="安全策略" width="500px"><el-form :model="editingPolicy" label-width="90px"><el-form-item label="策略名称"><el-input v-model="editingPolicy.name" /></el-form-item><el-form-item label="策略类型"><el-select v-model="editingPolicy.policyType"><el-option label="数据分级" value="DATA_CLASSIFICATION" /><el-option label="访问控制" value="ACCESS_CONTROL" /><el-option label="脱敏策略" value="MASKING" /><el-option label="保留期限" value="RETENTION" /></el-select></el-form-item><el-form-item label="数据级别"><el-select v-model="editingPolicy.classification"><el-option label="公开" value="PUBLIC" /><el-option label="内部" value="INTERNAL" /><el-option label="敏感" value="SENSITIVE" /><el-option label="严格受限" value="RESTRICTED" /></el-select></el-form-item><el-form-item label="规则 JSON"><el-input v-model="editingPolicy.rulesJson" type="textarea" placeholder='{"retentionDays":365,"needApproval":true}' /></el-form-item><el-form-item label="启用"><el-switch v-model="editingPolicy.enabled" /></el-form-item></el-form><template #footer><el-button @click="policyDialog=false">取消</el-button><el-button type="primary" @click="savePolicy(editingPolicy); policyDialog=false">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
const roles=ref([]), permissions=ref([]), policies=ref([]), backups=ref([]), policyDialog=ref(false)
const route = useRoute()
const backupSectionRef = ref(null)
const editingPolicy=ref({name:'',policyType:'DATA_CLASSIFICATION',classification:'INTERNAL',rulesJson:'{}',enabled:true})
const load=async()=>{ try { roles.value=await request.get('/v1/security-admin/roles'); permissions.value=await request.get('/v1/security-admin/permissions'); policies.value=await request.get('/v1/security-admin/policies'); backups.value=await request.get('/v1/backups') } catch(e){ElMessage.error('加载安全管理数据失败：'+e.message)} }
const openPolicy=p=>{editingPolicy.value=p?{...p}:{name:'',policyType:'DATA_CLASSIFICATION',classification:'INTERNAL',rulesJson:'{}',enabled:true};policyDialog.value=true}
const savePolicy=async p=>{try{await request.post('/v1/security-admin/policies',p);await load();ElMessage.success('策略已保存')}catch(e){ElMessage.error(e.message)}}
const removePolicy=async p=>{try{await ElMessageBox.confirm('确认删除该安全策略？','提示',{type:'warning'});await request.delete(`/v1/security-admin/policies/${p.id}`);await load()}catch(e){if(e!=='cancel')ElMessage.error(e.message)}}
const createBackup=async()=>{try{await request.post('/v1/backups/create?type=MANUAL');await load();ElMessage.success('备份已创建')}catch(e){ElMessage.error(e.message)}}
const restoreBackup=async b=>{try{await ElMessageBox.confirm('恢复将覆盖当前部分数据，确认继续？','高风险操作',{type:'warning'});await request.post(`/v1/backups/${b.backupId}/restore`);ElMessage.success('恢复操作已完成')}catch(e){if(e!=='cancel')ElMessage.error(e.message)}}
const focusBackup = async () => {
  if (route.query.tab !== 'backup') return
  await nextTick()
  backupSectionRef.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
onMounted(async () => {
  await load()
  await focusBackup()
})
watch(() => route.query.tab, focusBackup)
</script>
<style scoped>
.page-heading,.card-heading{display:flex;align-items:center;justify-content:space-between}.page-heading{margin-bottom:22px}.page-heading h2{margin:5px 0}.page-heading p,.muted{color:var(--river-muted);font-size:13px}.eyebrow{color:var(--river-brand);font-size:12px;font-weight:700;letter-spacing:.08em}.backup-card{margin-top:18px;scroll-margin-top:82px}.card-heading{width:100%}
</style>
