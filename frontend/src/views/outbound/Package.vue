<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="包裹号 / 出库单号 / 运单号" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load" style="width: 120px">
          <el-option v-for="s in ['NEW', 'SHIPPED', 'CANCELLED']" :key="s" :value="s"><StatusTag :value="s" /></el-option>
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <template v-if="canWrite()">
          <el-button type="success" @click="openBuild('wave')"><el-icon><Plus /></el-icon>按波次建包</el-button>
          <el-button type="success" plain @click="openBuild('order')"><el-icon><Plus /></el-icon>按出库单建包</el-button>
          <el-button plain @click="openManual"><el-icon><Edit /></el-icon>手工分箱</el-button>
        </template>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="包裹号" width="190" />
        <el-table-column prop="orderCode" label="出库单号" width="190" />
        <el-table-column prop="seqNo" label="箱序" width="60" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="类型" width="110"><template #default="{ row }">{{ TYPE[row.type] || row.type }}</template></el-table-column>
        <el-table-column prop="waveId" label="波次ID" width="80" />
        <el-table-column prop="cartonCode" label="箱型" width="90" />
        <el-table-column prop="weight" label="重量 kg" width="90" />
        <el-table-column prop="carrier" label="承运商" width="90" />
        <el-table-column prop="trackingNo" label="运单号" width="150" />
        <el-table-column prop="createdAt" label="创建时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">明细</el-button>
            <template v-if="canWrite() && row.status === 'NEW'">
              <el-button link size="small" @click="openTracking(row)">运单</el-button>
              <el-popconfirm title="撤销该出库单全部包裹，回到已拣货?" @confirm="unpack(row)">
                <template #reference><el-button link type="danger" size="small">撤销建包</el-button></template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <!-- 自动建包 -->
    <el-dialog v-model="buildVisible" :title="buildMode === 'wave' ? '按波次建包' : '按出库单建包'" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item v-if="buildMode === 'wave'" label="波次(已播种)" required>
          <el-select v-model="build.waveId" filterable style="width: 100%" placeholder="选择 SOWED 状态波次">
            <el-option v-for="w in waves" :key="w.id" :label="`${w.code} (${w.orderCount} 单${w.packStrategy ? ' · ' + (PACK[w.packStrategy] || w.packStrategy) : ''})`" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="出库单(已拣货)" required>
          <el-select v-model="build.orderId" filterable style="width: 100%" placeholder="选择 PICKED 状态出库单">
            <el-option v-for="o in orders" :key="o.id" :label="`${o.code} 已拣 ${o.pickedQty}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="打包策略">
          <el-select v-model="build.strategy" clearable style="width: 100%" :placeholder="buildMode === 'wave' ? '默认用波次策略绑定的打包策略' : '默认一单一包'">
            <el-option v-for="(v, k) in PACK" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="单箱最大重量 kg"><el-input-number v-model="build.maxWeight" :min="0" :precision="2" placeholder="按重量拆箱时使用，默认 15" /></el-form-item>
        <template v-if="buildMode === 'order'">
          <el-form-item label="承运商"><el-input v-model="build.carrier" /></el-form-item>
          <el-form-item label="箱型">
            <el-select v-model="build.cartonCode" clearable style="width: 100%">
              <el-option v-for="c in options.carton" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="buildVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doBuild">生成包裹</el-button>
      </template>
    </el-dialog>

    <!-- 手工分箱 -->
    <el-dialog v-model="manualVisible" title="手工分箱" width="760px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="出库单(已拣货)" required>
          <el-select v-model="manual.orderId" filterable style="width: 100%" @change="loadOrderLines">
            <el-option v-for="o in orders" :key="o.id" :label="`${o.code} 已拣 ${o.pickedQty}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="承运商"><el-input v-model="manual.carrier" style="width: 200px" /></el-form-item>
      </el-form>
      <div v-if="pickedLines.length" class="hint">待分箱：{{ pickedLines.map((l) => `${l.itemCode}${l.lotNo ? '/' + l.lotNo : ''} × ${l.pickedQty}`).join('，') }}。每箱数量之和须等于实拣。</div>
      <div v-for="(box, bi) in manual.boxes" :key="bi" class="box">
        <div class="box-head">
          <b>第 {{ bi + 1 }} 箱</b>
          <el-select v-model="box.cartonCode" clearable placeholder="箱型" size="small" style="width: 160px; margin-left: 10px">
            <el-option v-for="c in options.carton" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <el-input-number v-model="box.weight" :min="0" :precision="2" size="small" placeholder="重量" style="width: 130px; margin-left: 10px" />
          <el-button link type="danger" size="small" style="margin-left: auto" :disabled="manual.boxes.length === 1" @click="manual.boxes.splice(bi, 1)">删除箱</el-button>
        </div>
        <el-table :data="box.lines" size="small" border>
          <el-table-column label="物料" min-width="160">
            <template #default="{ row }">
              <el-select v-model="row.key" size="small" style="width: 100%" @change="(k) => applyKey(row, k)">
                <el-option v-for="l in pickedLines" :key="lineKey(l)" :label="`${l.itemCode}${l.lotNo ? ' / ' + l.lotNo : ''}`" :value="lineKey(l)" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="160"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0.001" :precision="3" size="small" style="width: 100%" /></template></el-table-column>
          <el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" size="small" @click="box.lines.splice($index, 1)">删</el-button></template></el-table-column>
        </el-table>
        <el-button link type="primary" size="small" @click="box.lines.push({ key: pickedLines[0] ? lineKey(pickedLines[0]) : '', itemCode: pickedLines[0]?.itemCode, lotNo: pickedLines[0]?.lotNo, qty: 1 })">+ 添加明细</el-button>
      </div>
      <el-button size="small" @click="manual.boxes.push({ lines: [] })" style="margin-top: 8px">+ 新增一箱</el-button>
      <div v-if="pickedLines.length" class="hint" style="margin-top: 8px">已分配：{{ remainText }}</div>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doManual">生成包裹</el-button>
      </template>
    </el-dialog>

    <!-- 运单 -->
    <el-dialog v-model="trackingVisible" :title="`包裹 ${tracking.code || ''} 运单`" width="420px">
      <el-form label-width="90px">
        <el-form-item label="承运商"><el-input v-model="tracking.carrier" /></el-form-item>
        <el-form-item label="运单号"><el-input v-model="tracking.trackingNo" /></el-form-item>
        <el-form-item label="重量 kg"><el-input-number v-model="tracking.weight" :min="0" :precision="2" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="trackingVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTracking">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明细 -->
    <el-dialog v-model="detailVisible" :title="`包裹 ${detail.code || ''}`" width="640px">
      <el-descriptions :column="3" border size="small" style="margin-bottom: 10px">
        <el-descriptions-item label="出库单">{{ detail.orderCode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="类型">{{ TYPE[detail.type] || detail.type }}</el-descriptions-item>
        <el-descriptions-item label="箱型">{{ detail.cartonCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="重量">{{ detail.weight }}</el-descriptions-item>
        <el-descriptions-item label="运单">{{ detail.carrier || '-' }} {{ detail.trackingNo || '' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines || []" size="small" border>
        <el-table-column prop="itemCode" label="物料" width="120" />
        <el-table-column prop="lotNo" label="批次" width="130" />
        <el-table-column prop="qty" label="数量" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { canWrite } from '../../auth'
import { outbound } from '../../api'
import StatusTag from '../../components/StatusTag.vue'
import { useOptions } from '../../composables/useOptions'
import { fmt } from '../../utils'

const TYPE = { ONE_ORDER_ONE_PACKAGE: '一单一包', SPLIT_BY_WEIGHT: '按重量拆箱', MANUAL: '手工分箱' }
const PACK = { ONE_ORDER_ONE_PACKAGE: '一单一包', SPLIT_BY_WEIGHT: '按重量拆箱' }
const { options } = useOptions(['carton'])

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const query = reactive({ current: 1, size: 20, keyword: '', status: '' })

const buildVisible = ref(false)
const buildMode = ref('wave')
const build = reactive({ waveId: null, orderId: null, strategy: '', maxWeight: undefined, carrier: '', cartonCode: '' })
const waves = ref([])
const orders = ref([])

const manualVisible = ref(false)
const manual = reactive({ orderId: null, carrier: '', boxes: [{ lines: [] }] })
const pickedLines = ref([])

const trackingVisible = ref(false)
const tracking = ref({})
const detailVisible = ref(false)
const detail = ref({})

const lineKey = (l) => `${l.itemCode}|${l.lotNo || ''}`
const remainText = computed(() => {
  const got = {}
  manual.boxes.forEach((b) => b.lines.forEach((l) => { got[l.key] = (got[l.key] || 0) + Number(l.qty || 0) }))
  return pickedLines.value.map((l) => `${l.itemCode} ${got[lineKey(l)] || 0}/${l.pickedQty}`).join('，')
})

async function load() {
  loading.value = true
  try {
    const p = await outbound.packagePage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function loadCandidates() {
  const [w, o] = await Promise.all([outbound.wavePage({ current: 1, size: 200, status: 'SOWED' }), outbound.page({ current: 1, size: 200, status: 'PICKED' })])
  waves.value = w.records
  orders.value = o.records
}

async function openBuild(mode) {
  buildMode.value = mode
  Object.assign(build, { waveId: null, orderId: null, strategy: '', maxWeight: undefined, carrier: '', cartonCode: '' })
  await loadCandidates()
  buildVisible.value = true
}

async function doBuild() {
  if (buildMode.value === 'wave' && !build.waveId) return ElMessage.warning('请选择波次')
  if (buildMode.value === 'order' && !build.orderId) return ElMessage.warning('请选择出库单')
  saving.value = true
  try {
    const data = buildMode.value === 'wave'
      ? { waveId: build.waveId, strategy: build.strategy || null, maxWeight: build.maxWeight }
      : { orderId: build.orderId, strategy: build.strategy || null, maxWeight: build.maxWeight, carrier: build.carrier, cartonCode: build.cartonCode }
    const pkgs = await outbound.packageBuild(data)
    ElMessage.success(`已生成 ${pkgs.length} 个包裹，出库单转为已打包`)
    buildVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function openManual() {
  Object.assign(manual, { orderId: null, carrier: '', boxes: [{ lines: [] }] })
  pickedLines.value = []
  await loadCandidates()
  manualVisible.value = true
}

async function loadOrderLines(orderId) {
  const o = await outbound.get(orderId)
  pickedLines.value = (o.lines || []).filter((l) => Number(l.pickedQty) > 0)
  manual.carrier = o.carrier || ''
  manual.boxes = [{ lines: pickedLines.value.map((l) => ({ key: lineKey(l), itemCode: l.itemCode, lotNo: l.lotNo, qty: Number(l.pickedQty) })) }]
}

function applyKey(row, key) {
  const l = pickedLines.value.find((x) => lineKey(x) === key)
  if (l) {
    row.itemCode = l.itemCode
    row.lotNo = l.lotNo
  }
}

async function doManual() {
  if (!manual.orderId) return ElMessage.warning('请选择出库单')
  saving.value = true
  try {
    const boxes = manual.boxes.map((b) => ({ cartonCode: b.cartonCode, weight: b.weight, lines: b.lines.map((l) => ({ itemCode: l.itemCode, lotNo: l.lotNo || null, qty: l.qty })) }))
    const pkgs = await outbound.packageManual({ orderId: manual.orderId, carrier: manual.carrier, boxes })
    ElMessage.success(`已生成 ${pkgs.length} 个包裹`)
    manualVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openTracking(row) {
  tracking.value = { id: row.id, code: row.code, carrier: row.carrier, trackingNo: row.trackingNo, weight: row.weight }
  trackingVisible.value = true
}
async function saveTracking() {
  saving.value = true
  try {
    await outbound.packageTracking(tracking.value.id, tracking.value)
    ElMessage.success('已保存')
    trackingVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function unpack(row) {
  await outbound.packageUnpack(row.orderId)
  ElMessage.success('已撤销建包')
  await load()
}

async function openDetail(row) {
  detail.value = await outbound.packageGet(row.id)
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.hint { color: #909399; font-size: 12px; margin-bottom: 8px; }
.box { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; margin-bottom: 8px; }
.box-head { display: flex; align-items: center; margin-bottom: 6px; }
</style>
