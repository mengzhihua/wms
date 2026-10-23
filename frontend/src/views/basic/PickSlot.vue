<template>
  <CrudPage title="固定拣货位" :api="basic.pickSlot" :columns="columns" :option-sources="options" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { useOptions } from '../../composables/useOptions'

const { options } = useOptions(['warehouse', 'owner', 'item', 'location'])
const columns = [
  { prop: 'warehouseCode', label: '仓库', type: 'select', options: 'warehouse', required: true, filter: true, width: 120 },
  { prop: 'ownerCode', label: '货主', type: 'select', options: 'owner', required: true, filter: true, width: 120 },
  { prop: 'itemCode', label: '物料', type: 'select', options: 'item', required: true, filter: true, minWidth: 140 },
  { prop: 'locationCode', label: '拣货位', type: 'select', options: 'location', required: true, width: 140 },
  { prop: 'minQty', label: '下限', type: 'number', precision: 3, width: 100 },
  { prop: 'maxQty', label: '上限', type: 'number', precision: 3, width: 100 }
]
</script>
