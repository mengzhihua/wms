<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="任务号 / 物料 / 库位" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option value="NEW" label="待补货" /><el-option value="DONE" label="已完成" /><el-option value="CANCELLED" label="已取消" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canWrite()" type="success" @click="genVisible = true"><el-icon><MagicStick /></el-icon>Min/Max 自动生成</el-button>
        <el-button v-if="canWrite()" @click="openManual"><el-icon><Plus /></el-icon>手工补货</el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom: 8px">拣货位现有量 + 在途补货 低于物料安全库存时，从存储位按 FEFO 生成补货任务，补到最大库存（未设置时为 2 倍安全库存）。</el-alert>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="任务号" width="190" />
        <el-table-column prop="itemCode" label="物料" width="100" />
        <el-table-column prop="lotNo" label="批次" width="120" />
        <el-table-column prop="fromLocation" label="源库位(存储)" width="120" />
        <el-table-column prop="toLocation" label="目标库位(拣货)" width="120" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column v-if="canWrite()" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'NEW'">
              <el-button link type="primary" size="small" @click="openConfirm(row)">补货确认</el-button>
              <el-button link type="danger" size="small" @click="cancel(row)">取消</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <el-dialog v-model="genVisible" title="自动生成补货任务" width="420px">
      <el-form label-width="70px">
        <el-form-item label="仓库"><el-select v-model="gen.warehouseCode" clearable style="width: 100%"><el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="货主"><el-select v-model="gen.ownerCode" clearable style="width: 100%"><el-option v-for="o in options.owner" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="generate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manualVisible" title="手工补货" width="480px">
      <el-form label-width="90px">
        <el-form-item label="源库存ID"><el-input-number v-model="manual.inventoryId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="manual.qty" :min="0.001" :precision="3" style="width: 100%" /></el-form-item>
        <el-form-item label="目标拣货位"><el-select v-model="manual.toLocation" filterable style="width: 100%"><el-option v-for="o in pickLocations" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createManual">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="confirmVisible" title="补货确认" width="480px">
      <el-form label-width="90px">
        <el-form-item label="物料">{{ current.itemCode }} / 批次 {{ current.lotNo || '-' }} / 数量 {{ current.qty }}</el-form-item>
        <el-form-item label="源库位">{{ current.fromLocation }}</el-form-item>
        <el-form-item label="目标库位"><el-select v-model="toLocation" filterable style="width: 100%"><el-option v-for="o in pickLocations" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirm">确认补货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { canWrite } from '../../auth'
import { ElMessage } from 'element-plus'
import { inventory } from '../../api'
import { useOptions } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { fmt } from '../../utils'

const { options } = useOptions(['warehouse', 'owner', 'location'])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const genVisible = ref(false)
const manualVisible = ref(false)
const confirmVisible = ref(false)
const current = ref({})
const toLocation = ref('')
const gen = reactive({ warehouseCode: '', ownerCode: '' })
const manual = reactive({ inventoryId: undefined, qty: 1, toLocation: '' })
const query = reactive({ current: 1, size: 20, keyword: '', status: 'NEW' })

const pickLocations = computed(() => (options.value.location || []).filter((l) => l.type === 'PICKING'))

async function load() {
  loading.value = true
  try {
    const p = await inventory.replenishPage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function generate() {
  saving.value = true
  try {
    const tasks = await inventory.replenishGenerate(gen)
    ElMessage.success(`生成 ${tasks.length} 个补货任务`)
    genVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openManual() {
  manual.inventoryId = undefined
  manual.qty = 1
  manual.toLocation = ''
  manualVisible.value = true
}

async function createManual() {
  if (!manual.inventoryId || !manual.toLocation) return ElMessage.warning('请填写源库存与目标库位')
  saving.value = true
  try {
    await inventory.replenishCreate(manual)
    ElMessage.success('已创建')
    manualVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openConfirm(row) {
  current.value = row
  toLocation.value = row.toLocation
  confirmVisible.value = true
}

async function confirm() {
  saving.value = true
  try {
    await inventory.replenishConfirm(current.value.id, toLocation.value)
    ElMessage.success('补货完成')
    confirmVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function cancel(row) {
  await inventory.replenishCancel(row.id)
  ElMessage.success('已取消')
  load()
}

onMounted(load)
</script>
