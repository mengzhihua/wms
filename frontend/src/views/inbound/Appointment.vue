<template>
  <CrudPage title="月台预约" :api="inboundAppointment" :columns="columns" :option-sources="options" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { crud } from '../../api'
import { useOptions } from '../../composables/useOptions'

const inboundAppointment = crud('/inbound/appointment')
const { options } = useOptions(['warehouse'])
const columns = [
  { prop: 'code', label: '预约号', readonlyOnEdit: true, width: 170 },
  { prop: 'warehouseCode', label: '仓库', type: 'select', options: 'warehouse', required: true, filter: true, width: 110 },
  { prop: 'dockCode', label: '月台', required: true, width: 110 },
  { prop: 'refType', label: '单据类型', type: 'select', required: true, width: 110, options: [
    { label: '入库', value: 'ASN' }, { label: '出库', value: 'SHIP' }
  ] },
  { prop: 'refCode', label: '单号', required: true, minWidth: 150 },
  { prop: 'status', label: '状态', type: 'select', width: 110, default: 'BOOKED', options: [
    { label: '已预约', value: 'BOOKED' }, { label: '已靠台', value: 'ARRIVED' },
    { label: '作业中', value: 'WORKING' }, { label: '已离开', value: 'LEFT' }
  ] },
  { prop: 'remark', label: '备注', hideInTable: true }
]
</script>
