<template>
  <div class="security-admin page-container">
    <header class="page-heading">
      <div><span class="eyebrow">ACCESS CONTROL ADMINISTRATION</span><h1>权限管理后台</h1><p>维护角色、权限目录和角色授权关系，所有变更保存到后端。</p></div>
      <el-button @click="router.push('/security-audit')">返回审计中心</el-button>
    </header>

    <el-card shadow="never" class="panel">
      <template #header><div class="panel-title"><span>角色与权限</span><el-button type="primary" :loading="loading" @click="load">刷新</el-button></div></template>
      <div class="toolbar"><el-button type="primary" @click="openRole()">新增角色</el-button><span>已加载 {{ roles.length }} 个角色、{{ permissions.length }} 项权限</span></div>
      <el-table :data="roles" stripe empty-text="暂无角色数据，请先刷新或联系管理员初始化">
        <el-table-column prop="name" label="角色名称" min-width="150" />
        <el-table-column prop="code" label="角色编码" min-width="140" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="授权数量" width="110"><template #default="{ row }"><el-tag>{{ rolePermissionIds[row.id]?.length || 0 }} 项</el-tag></template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openPermissions(row)">配置权限</el-button><el-button link @click="openRole(row)">编辑</el-button><el-button link type="danger" @click="removeRole(row)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="roleVisible" :title="editingRole.id ? '编辑角色' : '新增角色'" width="500px">
      <el-form :model="editingRole" label-position="top"><el-form-item label="角色名称" required><el-input v-model="editingRole.name" /></el-form-item><el-form-item label="角色编码" required><el-input v-model="editingRole.code" /></el-form-item><el-form-item label="说明"><el-input v-model="editingRole.description" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="roleVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRole">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" :title="`配置权限：${activeRole.name || ''}`" width="720px">
      <el-alert title="勾选后保存，授权关系将真实写入后端。" type="info" :closable="false" class="permission-tip" />
      <el-checkbox-group v-model="selectedPermissionIds" class="permission-grid"><el-checkbox v-for="permission in permissions" :key="permission.id" :label="permission.id" border><span>{{ permission.name }}</span><small>{{ permission.code }}</small></el-checkbox></el-checkbox-group>
      <template #footer><el-button @click="permissionVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="savePermissions">保存授权</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const loading = ref(false), saving = ref(false), roles = ref([]), permissions = ref([]), rolePermissionIds = ref({})
const roleVisible = ref(false), permissionVisible = ref(false), activeRole = ref({}), selectedPermissionIds = ref([])
const editingRole = reactive({ id: null, name: '', code: '', description: '' })

async function load() {
  loading.value = true
  try {
    const [roleRows, permissionRows] = await Promise.all([request.get('/v1/security-admin/roles'), request.get('/v1/security-admin/permissions')])
    roles.value = Array.isArray(roleRows) ? roleRows : []
    permissions.value = Array.isArray(permissionRows) ? permissionRows : []
    const map = {}
    await Promise.all(roles.value.map(async role => { try { map[role.id] = await request.get(`/v1/security-admin/roles/${role.id}/permissions`) || [] } catch (_) { map[role.id] = [] } }))
    rolePermissionIds.value = map
  } catch (e) { ElMessage.error(`权限数据加载失败：${e?.response?.data?.message || e.message || '接口不可用'}`) } finally { loading.value = false }
}
function openRole(role) { Object.assign(editingRole, role ? { ...role } : { id: null, name: '', code: '', description: '' }); roleVisible.value = true }
async function saveRole() { if (!editingRole.name || !editingRole.code) return ElMessage.warning('请填写角色名称和编码'); saving.value = true; try { await request.post('/v1/security-admin/roles', { ...editingRole }); roleVisible.value = false; await load(); ElMessage.success('角色已保存') } catch (e) { ElMessage.error(`保存失败：${e.message || '接口不可用'}`) } finally { saving.value = false } }
async function removeRole(role) { try { await ElMessageBox.confirm(`确认删除角色“${role.name}”？`, '删除角色', { type: 'warning' }); await request.delete(`/v1/security-admin/roles/${role.id}`); await load(); ElMessage.success('角色已删除') } catch (e) { if (e !== 'cancel') ElMessage.error(`删除失败：${e.message || '接口不可用'}`) } }
async function openPermissions(role) { activeRole.value = role; selectedPermissionIds.value = [...(rolePermissionIds.value[role.id] || [])]; permissionVisible.value = true }
async function savePermissions() { saving.value = true; try { await request.put(`/v1/security-admin/roles/${activeRole.value.id}/permissions`, { permissionIds: selectedPermissionIds.value }); permissionVisible.value = false; await load(); ElMessage.success('权限授权已保存') } catch (e) { ElMessage.error(`授权保存失败：${e.message || '接口不可用'}`) } finally { saving.value = false } }
onMounted(load)
</script>

<style scoped>
.page-heading,.panel-title,.toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:18px}.page-heading h1{margin:6px 0;font-size:26px}.page-heading p,.toolbar{margin:0;color:#86909c;font-size:13px}.eyebrow{color:#165dff;font-size:12px;font-weight:700;letter-spacing:.08em}.toolbar{justify-content:flex-start;margin-bottom:16px}.permission-tip{margin-bottom:16px}.permission-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.permission-grid :deep(.el-checkbox){height:auto;margin:0;padding:12px;display:flex;align-items:flex-start}.permission-grid small{display:block;color:#86909c;margin-left:8px}@media(max-width:700px){.page-heading{align-items:flex-start;flex-direction:column}.permission-grid{grid-template-columns:1fr}}
</style>
