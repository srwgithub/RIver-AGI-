import { computed, onMounted, ref, watch } from 'vue'
import request from '../utils/request'
import { getActiveDatasetId } from '../utils/workspaceSync'

const SELECTED_TASK_KEY = 'river_evaluation_selected_prediction_task'

export function useEvaluationTasks() {
  const loading = ref(false)
  const tasks = ref([])
  const savedSelectedId = Number(localStorage.getItem(SELECTED_TASK_KEY))
  const selectedId = ref(Number.isFinite(savedSelectedId) && savedSelectedId > 0 ? savedSelectedId : null)
  const keyword = ref('')
  const visibleTasks = computed(() => {
    const source = tasks.value
    const key = keyword.value.trim().toLowerCase()
    return source.filter(item => !key || `${item.name} ${item.model} ${item.version}`.toLowerCase().includes(key))
  })
  const currentTask = computed(() => tasks.value.find(item => item.id === selectedId.value))

  async function loadTasks() {
    loading.value = true
    try {
      const [taskPage, models] = await Promise.all([request.get('/v1/predictions?page=1&size=50'), request.get('/v1/predictions/models')])
      const records = taskPage?.records || (Array.isArray(taskPage) ? taskPage : [])
      const modelRows = Array.isArray(models) ? models : []
      tasks.value = records.map((task, index) => {
        const model = modelRows.find(item => item.predictionTaskId === task.id) || modelRows[index]
        const mape = model?.mape == null ? null : Number(model.mape)
        return {
          id: task.id,
          datasetId: task.datasetId,
          modelVersionId: model?.id,
          name: task.name || task.taskName || `预测任务 #${task.id}`,
          model: model?.algorithmType || model?.modelType || '预测模型',
          version: `v${model?.versionNumber || 1}`,
          status: mape == null ? '待评估' : mape > 10 ? '待优化' : '已完成',
          accuracy: mape == null ? null : Number((100 - mape).toFixed(1)),
          mape: mape == null ? null : Number(mape.toFixed(1)),
          updatedAt: task.updatedAt || task.createdAt || '最近更新'
        }
      })
    } catch (error) {
      tasks.value = []
    } finally {
      const activeId = getActiveDatasetId()
      const scoped = activeId ? tasks.value.filter(item => String(item.datasetId) === String(activeId)) : []
      const pool = scoped.length ? scoped : tasks.value
      const usable = pool.find(item => ['已完成', '运行中', '处理中'].includes(item.status) || item.mape != null)
      if (!tasks.value.some(item => item.id === selectedId.value)) selectedId.value = usable?.id || pool[0]?.id || null
      loading.value = false
    }
  }

  onMounted(loadTasks)
  watch(selectedId, value => {
    if (value == null) localStorage.removeItem(SELECTED_TASK_KEY)
    else localStorage.setItem(SELECTED_TASK_KEY, String(value))
  })
  return { loading, tasks, selectedId, keyword, visibleTasks, currentTask, loadTasks }
}
