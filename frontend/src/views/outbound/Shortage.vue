<template>
  <div class="page">
    <div class="card">
      <el-alert type="info" :closable="false" style="margin-bottom: 10px">
        缺货在「拣货任务」或「波次总拣」确认时登记（缺货 + 实拣 = 计划），任务按实拣完成并释放缺口预留；这里查询与关闭缺货记录。
      </el-alert>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="出库单号 / 物料 / 库位" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load" style="width: 120px">
          <el-option value="OPEN"><StatusTag value="OPEN" /></el-option>
          <el-option value="CLOSED"><StatusTag value="CLOSED" /></el-option>
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="orderCode" label="出库单号" width="190" />
        <el-table-column prop="waveId" label="波次ID" width="80" />
        <el-table-column prop="warehouseCode" label="仓库" width="80" />
        <el-table-column prop="ownerCode" label="货主" width="80" />
        <el-table-column prop="itemCode" label="物料" width="100" />
        <el-table-column prop="lotNo" label="批次" width="110" />
        <el-table-column prop="locationCode" label="库位" width="110" />
        <el-table-column prop="qty" label="缺货数量" width="90" />
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operator" label="登记人" width="90" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="createdAt" label="登记时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canWrite() && row.status === 'OPEN'" link type="success" size="small" @click="close(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { canWrite } from '../../auth'
import { outbound } from '../../api'
import StatusTag from '../../components/StatusTag.vue'
import { fmt } from '../../utils'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const p = await outbound.shortagePage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function close(row) {
  const { value } = await ElMessageBox.prompt('处理说明（如：已补货 / 客户取消）', '关闭缺货记录', { inputValue: '' })
  await outbound.shortageClose(row.id, value)
  ElMessage.success('已关闭')
  await load()
}

onMounted(load)
</script>
