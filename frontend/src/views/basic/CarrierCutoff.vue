<template>
  <CrudPage title="承运商截单" :api="basic.carrierCutoff" :columns="columns" :option-sources="options" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { useOptions } from '../../composables/useOptions'

const { options } = useOptions(['warehouse'])
const columns = [
  { prop: 'warehouseCode', label: '仓库', type: 'select', options: 'warehouse', required: true, filter: true, width: 140 },
  { prop: 'carrier', label: '承运商', required: true, minWidth: 140 },
  { prop: 'cutoffHour', label: '截单小时', type: 'number', required: true, width: 120 }
]
</script>
