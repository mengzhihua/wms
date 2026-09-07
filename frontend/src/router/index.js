import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../layout/Layout.vue'
import { auth, isAdmin } from '../auth'

export const menus = [
  { path: '/dashboard', name: '工作台', icon: 'Odometer', component: () => import('../views/Dashboard.vue') },
  {
    path: '/basic', name: '基础数据', icon: 'Setting',
    children: [
      { path: 'warehouse', name: '仓库', component: () => import('../views/basic/Warehouse.vue') },
      { path: 'zone', name: '库区', component: () => import('../views/basic/Zone.vue') },
      { path: 'location', name: '库位', component: () => import('../views/basic/Location.vue') },
      { path: 'owner', name: '货主', component: () => import('../views/basic/Owner.vue') },
      { path: 'item', name: '物料', component: () => import('../views/basic/Item.vue') },
      { path: 'supplier', name: '供应商', component: () => import('../views/basic/Supplier.vue') },
      { path: 'customer', name: '客户', component: () => import('../views/basic/Customer.vue') },
      { path: 'carton', name: '包材/箱型', component: () => import('../views/basic/Carton.vue') }
    ]
  },
  {
    path: '/inbound', name: '入库管理', icon: 'Download',
    children: [
      { path: 'asn', name: '入库单(ASN)', component: () => import('../views/inbound/Asn.vue') },
      { path: 'qc', name: '质检放行', component: () => import('../views/inbound/Qc.vue') },
      { path: 'putaway', name: '上架任务', component: () => import('../views/inbound/Putaway.vue') }
    ]
  },
  {
    path: '/outbound', name: '出库管理', icon: 'Upload',
    children: [
      { path: 'order', name: '出库单', component: () => import('../views/outbound/ShipOrder.vue') },
      { path: 'pick', name: '拣货任务', component: () => import('../views/outbound/Pick.vue') },
      { path: 'wave', name: '波次/播种', component: () => import('../views/outbound/Wave.vue') }
    ]
  },
  {
    path: '/inventory', name: '库内管理', icon: 'Box',
    children: [
      { path: 'stock', name: '库存查询', component: () => import('../views/inventory/Stock.vue') },
      { path: 'summary', name: '库存汇总', component: () => import('../views/inventory/Summary.vue') },
      { path: 'replenish', name: '补货任务', component: () => import('../views/inventory/Replenish.vue') },
      { path: 'serial', name: '序列号(SN)', component: () => import('../views/inventory/Serial.vue') },
      { path: 'count', name: '盘点管理', component: () => import('../views/inventory/Count.vue') },
      { path: 'txn', name: '库存流水', component: () => import('../views/inventory/Txn.vue') }
    ]
  },
  { path: '/report', name: '报表分析', icon: 'DataAnalysis', component: () => import('../views/report/Report.vue') },
  {
    path: '/system', name: '系统管理', icon: 'Tools', adminOnly: true,
    children: [
      { path: 'user', name: '用户管理', component: () => import('../views/system/User.vue') },
      { path: 'oplog', name: '操作日志', component: () => import('../views/system/OpLog.vue') }
    ]
  }
]

/** 当前用户可见菜单（adminOnly 菜单仅管理员可见；后端同样做了鉴权） */
export const visibleMenus = () => menus.filter((m) => !m.adminOnly || isAdmin())

const routes = [
  { path: '/login', name: '登录', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: menus.flatMap((m) =>
      m.children
        ? m.children.map((c) => ({ path: `${m.path}/${c.path}`, name: c.name, component: c.component }))
        : [{ path: m.path, name: m.name, component: m.component }]
    )
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path === '/login') return auth.token ? '/dashboard' : true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path.startsWith('/system') && !isAdmin()) return '/dashboard'
  return true
})

export default router
