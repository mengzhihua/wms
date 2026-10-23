<template>
  <CrudPage title="月台" :api="basic.dock" :columns="columns" :option-sources="options" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { useOptions, statusCol } from '../../composables/useOptions'

const { options } = useOptions(['warehouse'])
const columns = [
  { prop: 'warehouseCode', label: '仓库', type: 'select', options: 'warehouse', required: true, filter: true, width: 140 },
  { prop: 'code', label: '月台编码', required: true, readonlyOnEdit: true, width: 140 },
  { prop: 'name', label: '名称', required: true, minWidth: 160 },
  statusCol
]
</script>
