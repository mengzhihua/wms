<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.warehouseCode" placeholder="仓库" clearable style="width: 160px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button v-if="canWrite()" type="success" @click="openForm">新建消耗单</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="消耗单号" width="180" />
        <el-table-column prop="warehouseCode" label="仓库" width="110" />
        <el-table-column prop="ownerCode" label="货主" width="110" />
        <el-table-column prop="supplierCode" label="供应商" width="120" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="totalQty" label="数量" width="90" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">明细</el-button>
            <el-button v-if="canWrite() && row.status === 'NEW'" link type="success" @click="post(row)">过账</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="formVisible" title="新建消耗单" width="720px" destroy-on-close>
      <el-form :inline="true" :model="form">
        <el-form-item label="仓库"><el-input v-model="form.warehouseCode" /></el-form-item>
        <el-form-item label="货主"><el-input v-model="form.ownerCode" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="form.supplierCode" /></el-form-item>
      </el-form>
      <el-table :data="form.lines" size="small" border>
        <el-table-column label="物料"><template #default="{ row }"><el-input v-model="row.itemCode" /></template></el-table-column>
        <el-table-column label="数量" width="160"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0" /></template></el-table-column>
        <el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button style="margin-top: 8px" @click="form.lines.push({ qty: 1 })">添加明细</el-button>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="消耗明细" width="520px">
      <el-table :data="detail" size="small" border>
        <el-table-column prop="lineNo" label="#" width="60" />
        <el-table-column prop="itemCode" label="物料" />
        <el-table-column prop="qty" label="数量" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import StatusTag from '../../components/StatusTag.vue'
import { inventory } from '../../api'
import { hasPerm, PERM } from '../../auth'

const canWrite = () => hasPerm(PERM.INVENTORY_WRITE)
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const detailVisible = ref(false)
const detail = ref([])
const query = reactive({ warehouseCode: '' })
const form = reactive({ warehouseCode: '', ownerCode: '', supplierCode: '', lines: [{ qty: 1 }] })

async function load() {
  loading.value = true
  try {
    const page = await inventory.consumptionPage({ current: 1, size: 50, warehouseCode: query.warehouseCode || undefined })
    rows.value = page.records
  } finally {
    loading.value = false
  }
}

function openForm() {
  Object.assign(form, { warehouseCode: query.warehouseCode, ownerCode: '', supplierCode: '', lines: [{ qty: 1 }] })
  formVisible.value = true
}

async function save() {
  saving.value = true
  try {
    await inventory.consumptionCreate({ ...form, lines: form.lines.filter((l) => l.itemCode && l.qty > 0) })
    ElMessage.success('已创建')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function post(row) {
  await inventory.consumptionPost(row.id)
  ElMessage.success('已过账')
  load()
}

async function openDetail(row) {
  const doc = await inventory.consumptionGet(row.id)
  detail.value = doc.lines || []
  detailVisible.value = true
}

load()
</script>
