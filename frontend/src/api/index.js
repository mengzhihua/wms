import http from './request'

export const crud = (base) => ({
  page: (params) => http.get(`${base}/page`, { params }),
  list: (params) => http.get(`${base}/list`, { params }),
  get: (id) => http.get(`${base}/${id}`),
  create: (data) => http.post(base, data),
  update: (id, data) => http.put(`${base}/${id}`, data),
  remove: (id) => http.delete(`${base}/${id}`)
})

export const basic = {
  warehouse: crud('/basic/warehouse'),
  zone: crud('/basic/zone'),
  location: crud('/basic/location'),
  owner: crud('/basic/owner'),
  item: crud('/basic/item'),
  supplier: crud('/basic/supplier'),
  customer: crud('/basic/customer'),
  carton: crud('/basic/carton'),
  itemAbcApply: (data) => http.post('/basic/item/abc-apply', data)
}

export const inbound = {
  page: (params) => http.get('/inbound/asn/page', { params }),
  get: (id) => http.get(`/inbound/asn/${id}`),
  create: (data) => http.post('/inbound/asn', data),
  update: (id, data) => http.put(`/inbound/asn/${id}`, data),
  cancel: (id) => http.post(`/inbound/asn/${id}/cancel`),
  receive: (id, lines) => http.post(`/inbound/asn/${id}/receive`, lines),
  closeReceiving: (id) => http.post(`/inbound/asn/${id}/close-receiving`),
  tasks: (id) => http.get(`/inbound/asn/${id}/tasks`),
  putawayPage: (params) => http.get('/inbound/putaway/page', { params }),
  putaway: (taskId, toLocation) => http.post(`/inbound/putaway/${taskId}/confirm`, { toLocation }),
  qcTasks: (id) => http.get(`/inbound/asn/${id}/qc-tasks`),
  qcPage: (params) => http.get('/inbound/qc/page', { params }),
  inspect: (taskId, data) => http.post(`/inbound/qc/${taskId}/inspect`, data)
}

export const outbound = {
  page: (params) => http.get('/outbound/order/page', { params }),
  get: (id) => http.get(`/outbound/order/${id}`),
  create: (data) => http.post('/outbound/order', data),
  update: (id, data) => http.put(`/outbound/order/${id}`, data),
  allocate: (id) => http.post(`/outbound/order/${id}/allocate`),
  deallocate: (id) => http.post(`/outbound/order/${id}/deallocate`),
  ship: (id, data) => http.post(`/outbound/order/${id}/ship`, data || {}),
  pack: (id, data) => http.post(`/outbound/order/${id}/pack`, data),
  cartonSuggest: (id) => http.get(`/outbound/order/${id}/carton-suggest`),
  cancel: (id) => http.post(`/outbound/order/${id}/cancel`),
  tasks: (id) => http.get(`/outbound/order/${id}/tasks`),
  pickPage: (params) => http.get('/outbound/pick/page', { params }),
  wavePage: (params) => http.get('/outbound/wave/page', { params }),
  waveGet: (id) => http.get(`/outbound/wave/${id}`),
  waveCreate: (data) => http.post('/outbound/wave', data),
  wavePick: (taskId, qty) => http.post(`/outbound/wave/pick/${taskId}/confirm`, { qty }),
  waveSow: (taskId, qty) => http.post(`/outbound/wave/sow/${taskId}/confirm`, { qty }),
  waveShip: (id) => http.post(`/outbound/wave/${id}/ship`),
  waveCancel: (id) => http.post(`/outbound/wave/${id}/cancel`),
  pick: (taskId, qty) => http.post(`/outbound/pick/${taskId}/confirm`, { qty })
}

export const inventory = {
  page: (params) => http.get('/inventory/page', { params }),
  summary: () => http.get('/inventory/summary'),
  txnPage: (params) => http.get('/inventory/txn/page', { params }),
  move: (data) => http.post('/inventory/move', data),
  adjust: (data) => http.post('/inventory/adjust', data),
  freeze: (data) => http.post('/inventory/freeze', data),
  countPage: (params) => http.get('/inventory/count/page', { params }),
  countLines: (id) => http.get(`/inventory/count/${id}/lines`),
  countCreate: (data) => http.post('/inventory/count', data),
  countSubmit: (id, counts) => http.post(`/inventory/count/${id}/submit`, counts),
  countPost: (id) => http.post(`/inventory/count/${id}/post`),
  countCancel: (id) => http.post(`/inventory/count/${id}/cancel`),
  replenishPage: (params) => http.get('/inventory/replenish/page', { params }),
  replenishGenerate: (data) => http.post('/inventory/replenish/generate', data),
  replenishCreate: (data) => http.post('/inventory/replenish', data),
  replenishConfirm: (id, toLocation) => http.post(`/inventory/replenish/${id}/confirm`, { toLocation }),
  replenishCancel: (id) => http.post(`/inventory/replenish/${id}/cancel`),
  serialPage: (params) => http.get('/inventory/serial/page', { params }),
  serialTrace: (serialNo, params) => http.get(`/inventory/serial/${encodeURIComponent(serialNo)}`, { params })
}

export const report = {
  aging: (params) => http.get('/report/aging', { params }),
  expiry: (params) => http.get('/report/expiry', { params }),
  kpi: (params) => http.get('/report/kpi', { params }),
  abc: (params) => http.get('/report/abc', { params }),
  labor: (params) => http.get('/report/labor', { params })
}

/** 带 token 下载后端 CSV（使用 blob，避开 URL 中传 token） */
export async function downloadCsv(url, params, filename) {
  const blob = await http.get(url, { params, responseType: 'blob' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}

export function importCsv(url, file) {
  const fd = new FormData()
  fd.append('file', file)
  return http.post(url, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export const dashboard = () => http.get('/dashboard')

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),
  changePassword: (data) => http.post('/auth/password', data)
}

export const system = {
  user: crud('/system/user'),
  oplogPage: (params) => http.get('/system/oplog/page', { params })
}
