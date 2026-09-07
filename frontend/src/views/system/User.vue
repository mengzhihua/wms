<template>
  <CrudPage title="用户" :api="system.user" :columns="columns" :option-sources="{}" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { system } from '../../api'
import { statusCol } from '../../composables/useOptions'
import { ROLE_LABEL } from '../../auth'

const columns = [
  { prop: 'username', label: '用户名', required: true, readonlyOnEdit: true, width: 140 },
  { prop: 'realName', label: '姓名', minWidth: 120 },
  {
    prop: 'role', label: '角色', type: 'select', required: true, filter: true, default: 'OPERATOR', width: 100,
    options: Object.entries(ROLE_LABEL).map(([value, label]) => ({ value, label }))
  },
  { prop: 'password', label: '密码', type: 'password', hideInTable: true, placeholder: '至少 6 位；编辑时留空表示不修改' },
  { prop: 'lastLoginAt', label: '最近登录', hideInForm: true, width: 160 },
  statusCol
]
</script>
