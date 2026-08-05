#!/usr/bin/env node
/** Authenticated backup, permission, security scan and audit smoke acceptance. */
const base = process.env.JAVA_BASE_URL || 'http://127.0.0.1:8080'
const result = { generatedAt: new Date().toISOString(), checks: [] }
async function call(path, options = {}) {
  const response = await fetch(`${base}${path}`, options)
  const text = await response.text()
  let body; try { body = JSON.parse(text) } catch { body = text }
  return { status: response.status, body }
}
function add(name, response, passed, detail) {
  result.checks.push({ name, status: response.status, passed, detail })
}
const login = await call('/api/v1/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' }) })
const token = login.body?.data?.token
if (!token) { console.log(JSON.stringify({ ...result, passed: false, error: 'login failed' }, null, 2)); process.exit(1) }
const headers = { Authorization: `Bearer ${token}` }
const get = path => call(path, { headers })
const post = path => call(path, { method: 'POST', headers })
const put = (path, body) => call(path, { method: 'PUT', headers: { ...headers, 'content-type': 'application/json' }, body: JSON.stringify(body) })

const datasets = await get('/api/v1/datasets?page=1&size=5')
const datasetId = datasets.body?.data?.records?.[0]?.id
const scan = datasetId ? await post(`/api/v1/security/datasets/${datasetId}/scan`) : { status: 0, body: {} }
add('安全扫描', scan, scan.status === 200 && scan.body?.data?.status === 'COMPLETED', scan.body?.data?.status)
const risks = datasetId ? await get(`/api/v1/security/datasets/${datasetId}/risks`) : { status: 0, body: {} }
add('风险查看', risks, risks.status === 200 && Array.isArray(risks.body?.data), `count=${risks.body?.data?.length || 0}`)
const roles = await get('/api/v1/security-admin/roles')
const permissions = await get('/api/v1/security-admin/permissions')
const role = (roles.body?.data || []).find(item => item.code === 'USER')
const rolePermissions = role ? await get(`/api/v1/security-admin/roles/${role.id}/permissions`) : { status: 0, body: {} }
const saved = role ? await put(`/api/v1/security-admin/roles/${role.id}/permissions`, { permissionIds: rolePermissions.body?.data || [] }) : { status: 0, body: {} }
add('角色权限授权保存', saved, saved.status === 200 && Array.isArray(saved.body?.data), `roles=${roles.body?.data?.length || 0}, permissions=${permissions.body?.data?.length || 0}`)
const created = await post('/api/v1/backups/create?type=QA')
const backupId = created.body?.data?.backupId
const verify = backupId ? await post(`/api/v1/backups/${backupId}/verify`) : { status: 0, body: {} }
add('备份创建与完整性校验', verify, verify.status === 200 && verify.body?.data?.integrityValid === true, verify.body?.data?.message)
const restored = backupId ? await post(`/api/v1/backups/${backupId}/restore`) : { status: 0, body: {} }
add('备份恢复', restored, restored.status === 200 && restored.body?.message === 'Backup restored successfully', restored.body?.message)
const audit = await get('/api/v1/audit/logs?page=1&size=10')
add('审计追溯', audit, audit.status === 200 && Array.isArray(audit.body?.data?.records), `records=${audit.body?.data?.records?.length || 0}`)
result.passed = result.checks.every(item => item.passed)
console.log(JSON.stringify(result, null, 2))
process.exit(result.passed ? 0 : 1)
