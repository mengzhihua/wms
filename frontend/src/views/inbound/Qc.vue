<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="任务号 / 入库单号 / 物料" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option value="NEW" label="待质检" /><el-option value="DONE" label="已完成" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom: 8px">需质检物料 / 退货入库收货后库存冻结在质检位；合格数量生成上架任务，拒收数量直接扣减并记录 QC_REJECT 流水。</el-alert>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="任务号" width="190" />
        <el-table-column prop="asnCode" label="入库单号" width="200" />
        <el-table-column prop="itemCode" label="物料" width="100" />
        <el-table-column prop="lotNo" label="批次" width="120" />
        <el-table-column prop="locationCode" label="质检位" width="100" />
        <el-table-column prop="qty" label="质检数量" width="90" />
        <el-table-column prop="passQty" label="合格" width="80" />
        <el-table-column prop="rejectQty" label="拒收" width="80" />
        <el-table-column prop="rejectReason" label="拒收原因" min-width="140" />
        <el-table-column prop="inspector" label="质检员" width="90" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column v-if="canWrite()" label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'NEW'" link type="primary" size="small" @click="open(row)">质检</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <el-dialog v-model="visible" title="质检放行" width="480px">
      <el-form label-width="90px">
        <el-form-item label="物料">{{ current.itemCode }} / 批次 {{ current.lotNo || '-' }} / 数量 {{ current.qty }}</el-form-item>
        <el-form-item label="合格数量"><el-input-number v-model="form.passQty" :min="0" :max="Number(current.qty)" :precision="3" @change="form.rejectQty = Number(current.qty) - form.passQty" /></el-form-item>
        <el-form-item label="拒收数量"><el-input-number v-model="form.rejectQty" :min="0" :max="Number(current.qty)" :precision="3" @change="form.passQty = Number(current.qty) - form.rejectQty" /></el-form-item>
        <el-form-item label="拒收原因" v-if="form.rejectQty > 0"><el-input v-model="form.rejectReason" placeholder="拒收必填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirm">提交质检结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { canWrite } from '../../auth'
import { ElMessage } from 'element-plus'
import { inbound } from '../../api'
import StatusTag from '../../components/StatusTag.vue'
import { fmt } from '../../utils'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const current = ref({})
const form = reactive({ passQty: 0, rejectQty: 0, rejectReason: '' })
const query = reactive({ current: 1, size: 20, keyword: '', status: 'NEW' })

async function load() {
  loading.value = true
  try {
    const p = await inbound.qcPage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function open(row) {
  current.value = row
  form.passQty = Number(row.qty)
  form.rejectQty = 0
  form.rejectReason = ''
  visible.value = true
}

async function confirm() {
  if (form.rejectQty > 0 && !form.rejectReason) return ElMessage.warning('拒收必须填写原因')
  saving.value = true
  try {
    await inbound.inspect(current.value.id, form)
    ElMessage.success('质检完成')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
