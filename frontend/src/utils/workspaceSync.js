export const ACTIVE_DATASET_KEY = 'river_active_dataset_id'
export const DATASET_SYNC_EVENT = 'river:dataset-sync'
export const DATASET_HEALTH_EVENT = 'river:dataset-health'

export const getActiveDatasetId = () => localStorage.getItem(ACTIVE_DATASET_KEY) || ''

export const setActiveDatasetId = (datasetId) => {
  const value = datasetId == null ? '' : String(datasetId)
  if (value) localStorage.setItem(ACTIVE_DATASET_KEY, value)
  else localStorage.removeItem(ACTIVE_DATASET_KEY)
  window.dispatchEvent(new CustomEvent(DATASET_SYNC_EVENT, { detail: { datasetId: value } }))
}

export const clearActiveDatasetId = () => setActiveDatasetId('')

export const onDatasetSync = (handler) => {
  const syncHandler = (event) => {
    handler(event?.detail?.datasetId || getActiveDatasetId())
  }
  const storageHandler = (event) => {
    if (event.key === ACTIVE_DATASET_KEY) {
      handler(event.newValue || '')
    }
  }
  window.addEventListener(DATASET_SYNC_EVENT, syncHandler)
  window.addEventListener('storage', storageHandler)
  return () => {
    window.removeEventListener(DATASET_SYNC_EVENT, syncHandler)
    window.removeEventListener('storage', storageHandler)
  }
}

export const setDatasetHealthScore = (datasetId, score) => {
  if (datasetId == null || score == null) return
  const value = String(score)
  localStorage.setItem(`river_dataset_health_${datasetId}`, value)
  window.dispatchEvent(new CustomEvent(DATASET_HEALTH_EVENT, { detail: { datasetId: String(datasetId), score: Number(value) } }))
}

export const getDatasetHealthScore = (datasetId) => {
  if (datasetId == null) return null
  const value = localStorage.getItem(`river_dataset_health_${datasetId}`)
  return value == null || Number.isNaN(Number(value)) ? null : Number(value)
}

export const onDatasetHealth = (handler) => {
  const syncHandler = event => handler(event?.detail || {})
  window.addEventListener(DATASET_HEALTH_EVENT, syncHandler)
  return () => window.removeEventListener(DATASET_HEALTH_EVENT, syncHandler)
}
