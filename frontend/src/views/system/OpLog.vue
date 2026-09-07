<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.username" placeholder="用户名" clearable @keyup.enter="load" @clear="load" />
        <el-input v-model="query.keyword" placeholder="接口路径关键字" clearable @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="createdAt" label="时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="username" label="用户" width="110" />
        <el-table-column prop="method" label="方法" width="80" />
        <el-table-column prop="path" label="接口" min-width="260" />
        <el-table-column prop="query" label="参数" min-width="160" />
        <el-table-column prop="httpStatus" label="HTTP" width="70" />
        <el-table-column prop="costMs" label="耗时(ms)" width="90" />
        <el-table-column prop="clientIp" label="IP" width="130" />
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { system } from '../../api'
import { fmt } from '../../utils'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, username: '', keyword: '' })

async function load() {
  loading.value = true
  try {
    const p = await system.oplogPage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
