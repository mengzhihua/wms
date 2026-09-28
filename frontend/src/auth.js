import { reactive } from 'vue'

const TOKEN_KEY = 'wms_token'
const USER_KEY = 'wms_user'
const PERMS_KEY = 'wms_perms'

/** 登录态：令牌、当前用户与角色权限码，持久化到 localStorage */
export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
  perms: JSON.parse(localStorage.getItem(PERMS_KEY) || '[]')
})

export function setAuth(token, user, perms = []) {
  auth.token = token
  auth.user = user
  auth.perms = perms
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  localStorage.setItem(PERMS_KEY, JSON.stringify(perms))
}

export function clearAuth() {
  auth.token = ''
  auth.user = null
  auth.perms = []
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(PERMS_KEY)
}

/** 权限码目录 (与后端 Permission 一致) */
export const PERM = {
  ALL: '*',
  BASIC_WRITE: 'basic:write',
  INBOUND_WRITE: 'inbound:write',
  INBOUND_APPROVE: 'inbound:approve',
  OUTBOUND_WRITE: 'outbound:write',
  OUTBOUND_APPROVE: 'outbound:approve',
  STRATEGY_RUN: 'strategy:run',
  STRATEGY_WRITE: 'strategy:write',
  INVENTORY_WRITE: 'inventory:write',
  REPORT_WRITE: 'report:write',
  SYSTEM_WRITE: 'system:write'
}

/** 是否持有权限码（"*" 全部）；未登录为 false */
export const hasPerm = (code) => !!auth.user && (auth.perms.includes(PERM.ALL) || auth.perms.includes(code))

export const isAdmin = () => hasPerm(PERM.ALL)
/** 任一写权限 */
export const canWrite = () => !!auth.user && auth.perms.length > 0
export const canEditMaster = () => hasPerm(PERM.BASIC_WRITE)
export const canEditSystem = () => hasPerm(PERM.SYSTEM_WRITE)

/** 按路由前缀判定当前页面的写权限 */
export function canWritePath(path) {
  if (path.startsWith('/basic')) return hasPerm(PERM.BASIC_WRITE)
  if (path.startsWith('/system')) return hasPerm(PERM.SYSTEM_WRITE)
  if (path.startsWith('/inbound')) return hasPerm(PERM.INBOUND_WRITE)
  if (path.startsWith('/outbound/wave-strategy')) return hasPerm(PERM.STRATEGY_WRITE)
  if (path.startsWith('/outbound')) return hasPerm(PERM.OUTBOUND_WRITE)
  if (path.startsWith('/inventory')) return hasPerm(PERM.INVENTORY_WRITE)
  if (path.startsWith('/report')) return hasPerm(PERM.REPORT_WRITE)
  return canWrite()
}

export const ROLE_LABEL = { ADMIN: '管理员', OPERATOR: '作业员', VIEWER: '只读' }

export const APPROVAL_LABEL = { NONE: '无需审核', PENDING: '待审核', APPROVED: '已审核', REJECTED: '已驳回' }
export const APPROVAL_TYPE = { NONE: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
