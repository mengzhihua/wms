<template>
  <CrudPage title="容器" :api="basic.lpn" :columns="columns" :option-sources="options" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { useOptions } from '../../composables/useOptions'

const { options } = useOptions(['warehouse', 'owner', 'location'])
const columns = [
  { prop: 'code', label: '容器编码', required: true, readonlyOnEdit: true, width: 160 },
  { prop: 'warehouseCode', label: '仓库', type: 'select', options: 'warehouse', required: true, filter: true, width: 120 },
  { prop: 'type', label: '类型', type: 'select', required: true, width: 110, default: 'PALLET', options: [
    { label: '托盘', value: 'PALLET' }, { label: '整箱', value: 'CASE' }, { label: '周转箱', value: 'TOTE' }
  ] },
  { prop: 'locationCode', label: '库位', type: 'select', options: 'location', width: 140 },
  { prop: 'ownerCode', label: '货主', type: 'select', options: 'owner', width: 120 },
  { prop: 'parentCode', label: '父容器', hideInTable: true },
  { prop: 'status', label: '状态', width: 90, default: 'OPEN' }
]
</script>
