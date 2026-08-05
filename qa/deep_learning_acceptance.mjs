#!/usr/bin/env node
/** Real Python-engine acceptance: TensorFlow MLP and PyTorch LSTM/Transformer. */
const base = process.env.DL_BASE_URL || 'http://127.0.0.1:5001'
const rows = Array.from({ length: 40 }, (_, i) => [i, Math.sin(i / 4)])
const target = rows.map((row, i) => 10 + i * 0.4 + row[1])
const report = []

async function request(path, body) {
  const started = Date.now()
  const response = await fetch(`${base}${path}`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body)
  })
  const payload = await response.json()
  return { status: response.status, elapsedMs: Date.now() - started, payload }
}

for (const [algorithm, params, framework] of [
  ['mlp', { hidden_layers: [8, 4], epochs: 2, batch_size: 8, patience: 1 }, 'TensorFlow/Keras'],
  ['lstm', { hidden_size: 8, num_layers: 1, sequence_length: 5, epochs: 2, batch_size: 8, patience: 1 }, 'PyTorch'],
  ['transformer', { d_model: 8, num_heads: 2, num_layers: 1, sequence_length: 5, epochs: 2, batch_size: 8, patience: 1 }, 'PyTorch']
]) {
  const trained = await request('/api/v1/predictions/train', {
    algorithm, task_type: 'regression', X: rows, y: target, params
  })
  const data = trained.payload?.data || {}
  const modelId = data.model_id
  let predicted = null
  if (modelId) predicted = await request('/api/v1/predictions/predict', {
    model_id: modelId, X: rows.slice(-5)
  })
  const pass = trained.status === 201 && !!modelId && predicted?.status === 200
    && predicted.payload?.count > 0
  report.push({ framework, algorithm, trainStatus: trained.status, predictStatus: predicted?.status || 0,
    modelId: modelId || null, predictionCount: predicted?.payload?.count || 0,
    trainElapsedMs: trained.elapsedMs, predictElapsedMs: predicted?.elapsedMs || 0, pass })
}

console.log(JSON.stringify({ base, generatedAt: new Date().toISOString(), report,
  passed: report.every(item => item.pass) }, null, 2))
process.exit(report.every(item => item.pass) ? 0 : 1)
