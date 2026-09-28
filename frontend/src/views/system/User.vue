<template>
  <CrudPage title="用户" :api="system.user" :columns="columns" :option-sources="{ roles }" />
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import CrudPage from '../../components/CrudPage.vue'
import { system } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'username', label: '用户名', required: true, readonlyOnEdit: true, width: 140 },
  { prop: 'realName', label: '姓名', minWidth: 120 },
  {
    prop: 'role', label: '角色', type: 'select', required: true, filter: true, default: 'OPERATOR', width: 100,
    options: 'roles'
  },
  { prop: 'password', label: '密码', type: 'password', hideInTable: true, placeholder: '至少 6 位；编辑时留空表示不修改' },
  { prop: 'lastLoginAt', label: '最近登录', hideInForm: true, width: 160 },
  statusCol
]

const roles = reactive([])
onMounted(async () => {
  const list = await system.role.list()
  roles.splice(0, roles.length, ...list.map((r) => ({ value: r.code, label: `${r.name} (${r.code})` })))
})
</script>
