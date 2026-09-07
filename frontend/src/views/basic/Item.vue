<template>
  <CrudPage ref="pageRef" title="物料" :api="basic.item" :columns="columns" :option-sources="options">
    <template #toolbar>
      <el-button @click="exportCsv">导出 CSV</el-button>
      <el-upload v-if="isAdmin()" :show-file-list="false" :auto-upload="false" accept=".csv" :on-change="onImport">
        <el-button :loading="importing">导入 CSV</el-button>
      </el-upload>
    </template>
  </CrudPage>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import CrudPage from '../../components/CrudPage.vue'
import { basic, downloadCsv, importCsv } from '../../api'
import { useOptions, statusCol } from '../../composables/useOptions'
import { isAdmin } from '../../auth'

const { options } = useOptions(['owner'])
const pageRef = ref()
const importing = ref(false)
const columns = [
  { prop: 'ownerCode', label: '货主', type: 'select', options: 'owner', required: true, filter: true, readonlyOnEdit: true, width: 110 },
  { prop: 'code', label: '物料编码', required: true, readonlyOnEdit: true, width: 110 },
  { prop: 'name', label: '物料名称', required: true, minWidth: 160 },
  { prop: 'spec', label: '规格' },
  { prop: 'unit', label: '单位', width: 70, default: 'EA' },
  { prop: 'packQty', label: '箱规', type: 'number', width: 70 },
  { prop: 'barcode', label: '条码', width: 130 },
  { prop: 'category', label: '分类', width: 100 },
  { prop: 'lotControl', label: '批次管理', type: 'bool', width: 90, default: false },
  { prop: 'snControl', label: '序列号管理', type: 'bool', width: 100, default: false },
  { prop: 'qcRequired', label: '收货质检', type: 'bool', width: 90, default: false },
  { prop: 'shelfLifeDays', label: '保质期(天)', type: 'number', width: 90 },
  { prop: 'abcClass', label: 'ABC', type: 'select', width: 60, options: [{ label: 'A', value: 'A' }, { label: 'B', value: 'B' }, { label: 'C', value: 'C' }] },
  { prop: 'weight', label: '重量(kg)', type: 'number', precision: 3, hideInTable: true },
  { prop: 'volume', label: '体积(m³)', type: 'number', precision: 4, hideInTable: true },
  { prop: 'minStock', label: '安全库存', type: 'number', width: 90 },
  { prop: 'maxStock', label: '最大库存', type: 'number', hideInTable: true },
  statusCol
]

function exportCsv() {
  downloadCsv('/basic/item/export', {}, 'items.csv')
}

async function onImport(file) {
  importing.value = true
  try {
    const r = await importCsv('/basic/item/import', file.raw)
    ElMessage.success(`导入完成：新增 ${r.inserted}，更新 ${r.updated}`)
    pageRef.value?.load()
  } finally {
    importing.value = false
  }
}
</script>
