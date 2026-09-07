<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.warehouseCode" placeholder="仓库" clearable @change="load"><el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        <el-select v-model="query.ownerCode" placeholder="货主" clearable @change="load"><el-option v-for="o in options.owner" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        <el-input-number v-model="query.days" :min="1" :max="365" @change="load" /><span style="line-height: 32px">天</span>
        <el-button type="primary" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>
      <el-tabs v-model="tab" @tab-change="load">
        <el-tab-pane label="作业 KPI" name="kpi">
          <el-row :gutter="12" style="margin-bottom: 12px">
            <el-col :span="6" v-for="(v, k) in kpi.openTasks || {}" :key="k">
              <el-card shadow="never"><el-statistic :title="OPEN_LABEL[k] || k" :value="v" /></el-card>
            </el-col>
          </el-row>
          <el-table :data="kpi.daily || []" border stripe size="small" v-loading="loading">
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column v-for="t in TXN" :key="t" :label="TXN_LABEL[t]" :prop="t" width="110"><template #default="{ row }">{{ row[t] || 0 }} <span class="muted">/ {{ row[t + '_count'] || 0 }} 笔</span></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="库龄分析" name="aging">
          <el-table :data="aging.summary || []" border stripe size="small" v-loading="loading" max-height="360">
            <el-table-column prop="ownerCode" label="货主" width="90" />
            <el-table-column prop="itemCode" label="物料" width="110" />
            <el-table-column prop="itemName" label="名称" min-width="140" />
            <el-table-column prop="total" label="总量" width="90" />
            <el-table-column prop="b0_30" label="0-30天" width="90" />
            <el-table-column prop="b31_60" label="31-60天" width="90" />
            <el-table-column prop="b61_90" label="61-90天" width="90" />
            <el-table-column prop="b91_180" label="91-180天" width="100" />
            <el-table-column prop="b180p" label=">180天" width="90" />
            <el-table-column prop="maxAge" label="最长库龄" width="90" />
          </el-table>
          <h4>明细（按库龄倒序）</h4>
          <el-table :data="aging.detail || []" border stripe size="small" max-height="360">
            <el-table-column prop="itemCode" label="物料" width="110" />
            <el-table-column prop="locationCode" label="库位" width="110" />
            <el-table-column prop="lotNo" label="批次" width="120" />
            <el-table-column prop="qty" label="数量" width="90" />
            <el-table-column prop="receiveDate" label="收货日期" width="120" />
            <el-table-column prop="ageDays" label="库龄(天)" width="90" />
            <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="效期预警" name="expiry">
          <el-table :data="expiry" border stripe size="small" v-loading="loading">
            <el-table-column label="级别" width="90"><template #default="{ row }"><el-tag size="small" :type="row.level === 'EXPIRED' ? 'danger' : row.level === 'URGENT' ? 'warning' : 'info'">{{ LEVEL[row.level] }}</el-tag></template></el-table-column>
            <el-table-column prop="itemCode" label="物料" width="110" />
            <el-table-column prop="itemName" label="名称" min-width="140" />
            <el-table-column prop="locationCode" label="库位" width="110" />
            <el-table-column prop="lotNo" label="批次" width="120" />
            <el-table-column prop="qty" label="数量" width="90" />
            <el-table-column prop="expiryDate" label="效期" width="120" />
            <el-table-column prop="daysLeft" label="剩余天数" width="90" />
            <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="ABC 分析" name="abc">
          <el-alert type="info" :closable="false" style="margin-bottom: 8px">按统计周期内发运量降序累计占比划分：累计占比达 70% 前的物料为 A，90% 前为 B，其余 C。管理员可一键把建议分类写回物料主数据，用于指导库位规划与拣选路径。</el-alert>
          <el-button v-if="isAdmin()" type="primary" size="small" style="margin-bottom: 8px" :loading="saving" @click="applyAbc">应用建议分类到物料</el-button>
          <el-table :data="abc" border stripe size="small" v-loading="loading">
            <el-table-column prop="ownerCode" label="货主" width="90" />
            <el-table-column prop="itemCode" label="物料" width="110" />
            <el-table-column prop="itemName" label="名称" min-width="140" />
            <el-table-column prop="shipQty" label="发运量" width="100" />
            <el-table-column prop="shipLines" label="发运行数" width="100" />
            <el-table-column label="累计占比" width="100"><template #default="{ row }">{{ (row.cumPct * 100).toFixed(1) }}%</template></el-table-column>
            <el-table-column prop="currentClass" label="当前" width="70" />
            <el-table-column label="建议" width="70"><template #default="{ row }"><el-tag size="small" :type="row.suggestedClass === 'A' ? 'danger' : row.suggestedClass === 'B' ? 'warning' : 'info'">{{ row.suggestedClass }}</el-tag></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="计件效能" name="labor">
          <el-table :data="labor" border stripe size="small" v-loading="loading">
            <el-table-column prop="operator" label="操作员" width="120" />
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column v-for="t in TXN" :key="t" :label="TXN_LABEL[t]" width="110"><template #default="{ row }">{{ row[t] || 0 }} <span class="muted">/ {{ row[t + '_count'] || 0 }} 笔</span></template></el-table-column>
            <el-table-column prop="totalCount" label="合计笔数" width="100" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { report, basic } from '../../api'
import { useOptions } from '../../composables/useOptions'
import { isAdmin } from '../../auth'
import StatusTag from '../../components/StatusTag.vue'

const TXN = ['RECEIVE', 'PUTAWAY', 'PICK', 'SHIP', 'REPLENISH', 'QC_REJECT']
const TXN_LABEL = { RECEIVE: '收货', PUTAWAY: '上架', PICK: '拣货', SHIP: '发运', REPLENISH: '补货', QC_REJECT: '质检拒收' }
const OPEN_LABEL = { putaway: '待上架任务', qc: '待质检任务', pick: '待拣货任务', replenish: '待补货任务' }
const LEVEL = { EXPIRED: '已过期', URGENT: '≤7天', WARNING: '预警' }

const { options } = useOptions(['warehouse', 'owner'])
const tab = ref('kpi')
const loading = ref(false)
const saving = ref(false)
const query = reactive({ warehouseCode: '', ownerCode: '', days: 30 })
const kpi = ref({})
const aging = ref({})
const expiry = ref([])
const abc = ref([])
const labor = ref([])

async function load() {
  loading.value = true
  try {
    if (tab.value === 'kpi') kpi.value = await report.kpi({ days: query.days })
    else if (tab.value === 'aging') aging.value = await report.aging(query)
    else if (tab.value === 'expiry') expiry.value = await report.expiry(query)
    else if (tab.value === 'abc') abc.value = await report.abc(query)
    else if (tab.value === 'labor') labor.value = await report.labor({ days: query.days })
  } finally {
    loading.value = false
  }
}

async function applyAbc() {
  saving.value = true
  try {
    const r = await basic.itemAbcApply({ ownerCode: query.ownerCode, days: query.days })
    ElMessage.success(`已更新 ${r.updated} 个物料的 ABC 分类`)
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; }
</style>
