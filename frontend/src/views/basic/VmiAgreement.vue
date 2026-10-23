<template>
  <CrudPage title="VMI协议" :api="basic.vmiAgreement" :columns="columns" :option-sources="options">
    <template #toolbar>
      <el-select v-model="warehouseCode" placeholder="建议补货的仓库" clearable style="width: 180px">
        <el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="warning" :loading="suggesting" @click="suggest">生成建议 ASN</el-button>
    </template>
  </CrudPage>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { useOptions, statusCol } from '../../composables/useOptions'

const { options } = useOptions(['warehouse', 'owner', 'supplier', 'item'])
const warehouseCode = ref('')
const suggesting = ref(false)
const columns = [
  { prop: 'warehouseCode', label: '仓库', type: 'select', options: 'warehouse', required: true, filter: true, width: 110 },
  { prop: 'supplierCode', label: '供应商', type: 'select', options: 'supplier', required: true, filter: true, width: 120 },
  { prop: 'ownerCode', label: '货主', type: 'select', options: 'owner', required: true, width: 110 },
  { prop: 'itemCode', label: '物料', type: 'select', options: 'item', required: true, minWidth: 120 },
  { prop: 'minQty', label: '最小', type: 'number', precision: 3, width: 90 },
  { prop: 'maxQty', label: '最大', type: 'number', precision: 3, width: 90 },
  { prop: 'reorderPoint', label: '再订货点', type: 'number', precision: 3, width: 100 },
  { prop: 'leadTimeDays', label: '提前期(天)', type: 'number', width: 100 },
  { prop: 'transferPoint', label: '转移时点', type: 'select', required: true, width: 120, options: [
    { label: '收货买断', value: 'RECEIPT' }, { label: '发运转移', value: 'SHIP' }, { label: '消耗转移', value: 'CONSUMPTION' }
  ] },
  statusCol
]

async function suggest() {
  if (!warehouseCode.value) return ElMessage.warning('请选择仓库')
  suggesting.value = true
  try {
    const rows = await basic.vmiSuggest(warehouseCode.value)
    ElMessage.success(rows.length ? `已生成 ${rows.length} 张建议入库单` : '没有低于再订货点的协议')
  } finally {
    suggesting.value = false
  }
}
</script>
