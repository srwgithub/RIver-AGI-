<template>
  <div class="rules-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">ANNOTATION QUALITY / RULES</span>
        <h2>质量规则配置</h2>
        <p>配置自动校验、抽检和发布门禁使用的标注质量规则。</p>
      </div>
      <el-button type="primary" @click="openRule()">新增规则</el-button>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-heading">
          <span>规则配置后台</span>
          <el-button link type="primary" :loading="loading" @click="loadRules">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="rules" stripe empty-text="暂无质量规则">
        <el-table-column prop="name" label="规则名称" min-width="180" />
        <el-table-column prop="code" label="规则编码" min-width="170" />
        <el-table-column prop="ruleType" label="规则类型" min-width="140" />
        <el-table-column label="阈值" width="100">
          <template #default="scope">{{ scope.row.threshold ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="action" label="处理动作" min-width="120" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-switch v-model="scope.row.enabled" @change="toggleRule(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openRule(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="removeRule(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="tip">启用规则会参与自动校验与纠偏，优先级数字越小越先执行。</div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑质量规则' : '新增质量规则'" width="520px">
      <el-form :model="editing" label-width="95px">
        <el-form-item label="规则名称" required><el-input v-model="editing.name" /></el-form-item>
        <el-form-item label="规则编码" required><el-input v-model="editing.code" :disabled="!!editing.id" placeholder="如 MIN_CONFIDENCE" /></el-form-item>
        <el-form-item label="规则类型"><el-select v-model="editing.ruleType" style="width: 100%"><el-option label="最低置信度" value="MIN_CONFIDENCE" /><el-option label="标签合法性" value="LABEL_IN_SCHEMA" /><el-option label="正则校验" value="REGEX" /></el-select></el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="editing.threshold" :min="0" :max="1" :step="0.05" /></el-form-item>
        <el-form-item label="处理动作"><el-select v-model="editing.action" style="width: 100%"><el-option label="进入复核" value="REVIEW" /><el-option label="阻止发布" value="BLOCK" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="editing.priority" :min="1" :max="999" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="editing.description" type="textarea" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editing.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const rules = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const emptyRule = () => ({ name: '', code: '', ruleType: 'MIN_CONFIDENCE', threshold: 0.7, action: 'REVIEW', priority: 100, enabled: true, description: '' })
const editing = ref(emptyRule())

const loadRules = async () => {
  loading.value = true
  try {
    rules.value = await request.get('/v1/annotation-quality-rules') || []
  } catch (error) {
    ElMessage.error(`加载质量规则失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

const openRule = (rule) => {
  editing.value = rule ? { ...rule } : emptyRule()
  dialogVisible.value = true
}

const saveRule = async () => {
  if (!editing.value.name || !editing.value.code) {
    ElMessage.warning('请填写规则名称和规则编码')
    return
  }
  saving.value = true
  try {
    await request.post('/v1/annotation-quality-rules', editing.value)
    dialogVisible.value = false
    await loadRules()
    ElMessage.success('质量规则已保存')
  } catch (error) {
    ElMessage.error(`保存规则失败：${error.message}`)
  } finally {
    saving.value = false
  }
}

const toggleRule = async (rule) => {
  try {
    await request.post('/v1/annotation-quality-rules', rule)
    ElMessage.success(rule.enabled ? '规则已启用' : '规则已停用')
  } catch (error) {
    rule.enabled = !rule.enabled
    ElMessage.error(`更新规则状态失败：${error.message}`)
  }
}

const removeRule = async (rule) => {
  try {
    await ElMessageBox.confirm(`确认删除规则“${rule.name}”吗？`, '删除质量规则', { type: 'warning' })
    await request.delete(`/v1/annotation-quality-rules/${rule.id}`)
    await loadRules()
    ElMessage.success('规则已删除')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.message || '删除失败')
  }
}

onMounted(loadRules)
</script>

<style scoped>
.rules-page { padding-bottom: 24px; }
.page-heading, .card-heading { display: flex; align-items: center; justify-content: space-between; }
.page-heading { margin-bottom: 22px; }
.page-heading h2 { margin: 5px 0; color: var(--river-text, #1f2937); }
.page-heading p, .tip { color: var(--river-muted, #8c98a4); font-size: 13px; }
.eyebrow { color: var(--river-brand, #0f766e); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.tip { margin-top: 14px; }
</style>
