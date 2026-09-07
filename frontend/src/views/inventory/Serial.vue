<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="序列号 / 入库单 / 出库单" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
        <el-input v-model="query.itemCode" placeholder="物料编码" clearable style="width: 140px" @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="load">
          <el-option label="在库" value="IN_STOCK" />
          <el-option label="已发运" value="SHIPPED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="serialNo" label="序列号(SN/IMEI)" width="180" />
        <el-table-column prop="ownerCode" label="货主" width="90" />
        <el-table-column prop="itemCode" label="物料" width="120" />
        <el-table-column prop="lotNo" label="批次" width="110" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="locationCode" label="库位" width="110" />
        <el-table-column prop="asnCode" label="入库单" width="170" />
        <el-table-column prop="orderCode" label="出库单" width="170" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[20, 50, 100]" @change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { inventory } from '../../api'
import StatusTag from '../../components/StatusTag.vue'

const query = reactive({ current: 1, size: 20, keyword: '', itemCode: '', status: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const p = await inventory.serialPage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
