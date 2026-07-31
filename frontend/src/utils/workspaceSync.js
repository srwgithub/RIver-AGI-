export const ACTIVE_DATASET_KEY = 'river_active_dataset_id'
export const DATASET_SYNC_EVENT = 'river:dataset-sync'

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
