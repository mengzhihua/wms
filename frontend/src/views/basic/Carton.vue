<template>
  <CrudPage title="箱型" :api="basic.carton" :columns="columns" :option-sources="options" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { useOptions, statusCol } from '../../composables/useOptions'

const { options } = useOptions(['owner', 'item'])
const columns = [
  { prop: 'code', label: '箱型编码', required: true, readonlyOnEdit: true, width: 100 },
  { prop: 'name', label: '名称', required: true, minWidth: 140 },
  { prop: 'lengthCm', label: '长(cm)', type: 'number', precision: 1, width: 80 },
  { prop: 'widthCm', label: '宽(cm)', type: 'number', precision: 1, width: 80 },
  { prop: 'heightCm', label: '高(cm)', type: 'number', precision: 1, width: 80 },
  { prop: 'volume', label: '容积(m³)', type: 'number', precision: 6, width: 100 },
  { prop: 'maxWeight', label: '承重(kg)', type: 'number', precision: 2, width: 90 },
  { prop: 'ownerCode', label: '包材货主', type: 'select', options: 'owner', width: 100 },
  { prop: 'itemCode', label: '包材SKU', type: 'select', options: 'item', width: 120 },
  statusCol
]
</script>
