<template>
  <div class="page">
    <div class="card">
      <el-alert type="info" :closable="false" style="margin-bottom: 10px">
        策略按优先级从小到大依次扫描仓库内已分配且未入波的出库单；匹配的订单按分组键(SKU / 货主 / 承运商)分组，超过最大订单数、SKU 品项数或总件数上限时自动拆成多个波次。尾单策略(截止小时)在「执行全部」时仅在到点后触发。
      </el-alert>
      <div class="toolbar">
        <el-select v-model="runWarehouse" placeholder="仓库" style="width: 160px">
          <el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <template v-if="canWrite()">
          <el-button type="primary" :loading="running" @click="run(null, true)">预览匹配</el-button>
          <el-button type="success" :loading="running" @click="run(null, false)">执行全部启用策略</el-button>
          <el-button type="primary" plain @click="openForm()"><el-icon><Plus /></el-icon>新增策略</el-button>
        </template>
        <el-button @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="priority" label="优先级" width="70" sortable />
        <el-table-column prop="code" label="编码" width="90" />
        <el-table-column prop="name" label="名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="启用" width="70">
          <template #default="{ row }">
            <el-switch :model-value="!!row.enabled" :disabled="!canWrite()" size="small" @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="90"><template #default="{ row }">{{ range(row.minOrders, row.maxOrders) }}</template></el-table-column>
        <el-table-column label="单内 SKU" width="90"><template #default="{ row }">{{ range(row.minSkuPerOrder, row.maxSkuPerOrder) }}</template></el-table-column>
        <el-table-column label="单内件数" width="90"><template #default="{ row }">{{ range(row.minQtyPerOrder, row.maxQtyPerOrder) }}</template></el-table-column>
        <el-table-column label="波次上限" width="120"><template #default="{ row }">SKU {{ row.maxSkuItems || '∞' }} / 件 {{ row.maxTotalQty || '∞' }}</template></el-table-column>
        <el-table-column label="分组" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.groupByItem" size="small">SKU</el-tag>
            <el-tag v-if="row.groupByOwner" size="small" type="success">货主</el-tag>
            <el-tag v-if="row.groupByCarrier" size="small" type="warning">承运商</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="zoneCode" label="储区" width="70" />
        <el-table-column label="截止" width="70"><template #default="{ row }">{{ row.cutoffHour != null ? row.cutoffHour + ':00' : '-' }}</template></el-table-column>
        <el-table-column label="打包" width="110"><template #default="{ row }">{{ PACK[row.packStrategy] || row.packStrategy }}<span v-if="row.maxPackageWeight"> ≤{{ row.maxPackageWeight }}kg</span></template></el-table-column>
        <el-table-column prop="remark" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <template v-if="canWrite()">
              <el-button link type="primary" size="small" @click="run(row.id, false)">执行</el-button>
              <el-button link size="small" @click="openForm(row)">编辑</el-button>
              <el-popconfirm title="删除该策略?" @confirm="remove(row)">
                <template #reference><el-button link type="danger" size="small">删除</el-button></template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑策略' : '新增策略'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item></el-col>
          <el-col :span="10"><el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="优先级"><el-input-number v-model="form.priority" :min="1" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">订单筛选条件 (0 或空 = 不限)</el-divider>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="单内 SKU 数"><el-input-number v-model="form.minSkuPerOrder" :min="0" /> ~ <el-input-number v-model="form.maxSkuPerOrder" :min="0" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单内件数"><el-input-number v-model="form.minQtyPerOrder" :min="0" /> ~ <el-input-number v-model="form.maxQtyPerOrder" :min="0" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="限定储区"><el-input v-model="form.zoneCode" placeholder="订单全部待拣库位须在该库区" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="截止小时(尾单)"><el-input-number v-model="form.cutoffHour" :min="0" :max="23" placeholder="留空不限" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">成波 / 拆波</el-divider>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="每波订单数"><el-input-number v-model="form.minOrders" :min="1" /> ~ <el-input-number v-model="form.maxOrders" :min="1" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="波次上限"><el-input-number v-model="form.maxSkuItems" :min="0" placeholder="SKU" /> SKU / <el-input-number v-model="form.maxTotalQty" :min="0" placeholder="件" /> 件</el-form-item></el-col>
        </el-row>
        <el-form-item label="分组">
          <el-checkbox v-model="form.groupByItem">按 SKU 组合</el-checkbox>
          <el-checkbox v-model="form.groupByOwner">按货主</el-checkbox>
          <el-checkbox v-model="form.groupByCarrier">按承运商</el-checkbox>
        </el-form-item>
        <el-divider content-position="left">打包策略</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="打包策略">
              <el-select v-model="form.packStrategy" style="width: 100%">
                <el-option v-for="(v, k) in PACK" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="单箱最大重量 kg"><el-input-number v-model="form.maxPackageWeight" :min="0" :precision="2" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="说明"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" :title="resultDry ? '策略匹配预览' : '策略执行结果'" width="640px">
      <el-table :data="results" size="small" border>
        <el-table-column prop="strategyCode" label="策略" width="90" />
        <el-table-column prop="strategyName" label="名称" min-width="120" />
        <el-table-column prop="matchedOrders" label="匹配订单" width="90" />
        <el-table-column label="生成波次" min-width="220">
          <template #default="{ row }">
            <template v-if="row.waveCodes.length">
              <el-tag v-for="c in row.waveCodes" :key="c" size="small" style="margin: 2px">{{ c }}</el-tag>
            </template>
            <span v-else class="muted">{{ resultDry && row.matchedOrders ? '预览: 匹配 ' + row.matchedOrders + ' 单，执行后成波' : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">关闭</el-button>
        <el-button v-if="!resultDry && results.some((r) => r.waveIds.length)" @click="$router.push('/outbound/wave')">去波次页面作业</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { canWrite } from '../../auth'
import { outbound } from '../../api'
import { useOptions } from '../../composables/useOptions'

const PACK = { ONE_ORDER_ONE_PACKAGE: '一单一包', SPLIT_BY_WEIGHT: '按重量拆箱' }
const { options } = useOptions(['warehouse'])
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const running = ref(false)
const runWarehouse = ref('')
const formVisible = ref(false)
const form = ref({})
const resultVisible = ref(false)
const resultDry = ref(false)
const results = ref([])

watch(options, (o) => { if (!runWarehouse.value && o.warehouse?.length) runWarehouse.value = o.warehouse[0].value })

const range = (a, b) => (!a && !b ? '不限' : `${a || 0} ~ ${b || '∞'}`)

async function load() {
  loading.value = true
  try {
    rows.value = await outbound.strategyList()
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  form.value = row ? { ...row } : { priority: 100, minOrders: 1, maxOrders: 50, packStrategy: 'ONE_ORDER_ONE_PACKAGE', enabled: true, groupByItem: false, groupByOwner: false, groupByCarrier: false }
  formVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (form.value.id) await outbound.strategyUpdate(form.value.id, form.value)
    else await outbound.strategyCreate(form.value)
    ElMessage.success('已保存')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  await outbound.strategyToggle(row.id)
  await load()
}

async function remove(row) {
  await outbound.strategyRemove(row.id)
  ElMessage.success('已删除')
  await load()
}

async function run(strategyId, dryRun) {
  if (!runWarehouse.value) return ElMessage.warning('请选择仓库')
  running.value = true
  try {
    results.value = await outbound.strategyRun({ warehouseCode: runWarehouse.value, strategyId, dryRun })
    resultDry.value = dryRun
    resultVisible.value = true
    const waves = results.value.reduce((n, r) => n + r.waveIds.length, 0)
    if (!dryRun) ElMessage.success(waves ? `共生成 ${waves} 个波次` : '没有匹配到可成波的出库单')
  } finally {
    running.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted { color: #909399; }
</style>
