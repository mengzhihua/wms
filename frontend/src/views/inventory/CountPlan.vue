<template>
  <div class="page">
    <div class="card">
      <el-tabs v-model="tab" @tab-change="loadTab">
        <el-tab-pane label="盘点计划" name="plan" />
        <el-tab-pane label="盘点任务" name="task" />
        <el-tab-pane label="库存调整单" name="adjust" />
      </el-tabs>

      <!-- ============ 计划 ============ -->
      <template v-if="tab === 'plan'">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="计划号 / 名称" clearable @keyup.enter="load" @clear="load" />
          <el-select v-model="query.status" placeholder="状态" clearable @change="load" style="width: 130px">
            <el-option v-for="s in PLAN_STATUSES" :key="s" :value="s"><StatusTag :value="s" /></el-option>
          </el-select>
          <el-select v-model="query.type" placeholder="盘点类型" clearable @change="load" style="width: 130px">
            <el-option v-for="(t, k) in TYPES" :key="k" :label="t" :value="k" />
          </el-select>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button v-if="canWrite()" type="success" @click="openForm()"><el-icon><Plus /></el-icon>新建计划</el-button>
        </div>
        <el-table :data="rows" v-loading="loading" border stripe size="small">
          <el-table-column prop="code" label="计划号" width="190" />
          <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          <el-table-column label="类型" width="90"><template #default="{ row }">{{ TYPES[row.type] || row.type }}</template></el-table-column>
          <el-table-column prop="warehouseCode" label="仓库" width="80" />
          <el-table-column label="范围" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ scopeText(row) }}</template>
          </el-table-column>
          <el-table-column label="进度" width="140">
            <template #default="{ row }">
              <el-progress v-if="row.taskCount" :percentage="Math.round(((row.doneCount || 0) * 100) / row.taskCount)" :stroke-width="10" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="diffCount" label="差异" width="60" />
          <el-table-column prop="approver" label="审批人" width="90" />
          <el-table-column prop="createdAt" label="创建时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              <template v-if="canWrite()">
                <el-button v-if="row.status === 'DRAFT'" link size="small" @click="openForm(row)">编辑</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="act(inventory.planSubmit, row.id, '已提交审批')">提交审批</el-button>
                <el-button v-if="row.status === 'PENDING'" link type="warning" size="small" @click="openApprove(row)">审批</el-button>
                <el-button v-if="row.status === 'APPROVED'" link type="success" size="small" @click="act(inventory.planGenerate, row.id, '盘点任务已生成，库存已锁定')">生成任务</el-button>
                <el-button v-if="row.status === 'EXECUTING'" link type="success" size="small" @click="act(inventory.planComplete, row.id, '盘点计划已完成，锁定已释放')">完成</el-button>
                <el-popconfirm v-if="!['COMPLETED', 'CANCELLED'].includes(row.status)" title="取消计划并释放锁定库存?" @confirm="act(inventory.planCancel, row.id, '已取消')">
                  <template #reference><el-button link type="danger" size="small">取消</el-button></template>
                </el-popconfirm>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
        </div>
      </template>

      <!-- ============ 任务 ============ -->
      <template v-if="tab === 'task'">
        <div class="toolbar">
          <el-input v-model="taskQuery.keyword" placeholder="计划号 / 物料 / 库位" clearable @keyup.enter="loadTasks" @clear="loadTasks" />
          <el-select v-model="taskQuery.status" placeholder="状态" clearable @change="loadTasks" style="width: 130px">
            <el-option v-for="s in TASK_STATUSES" :key="s" :value="s"><StatusTag :value="s" /></el-option>
          </el-select>
          <el-input v-model="taskQuery.assignee" placeholder="盘点人" clearable style="width: 130px" @keyup.enter="loadTasks" @clear="loadTasks" />
          <el-button type="primary" @click="loadTasks"><el-icon><Search /></el-icon>查询</el-button>
        </div>
        <el-table :data="taskRows" v-loading="loading" border stripe size="small">
          <el-table-column prop="planCode" label="计划号" width="190" />
          <el-table-column prop="locationCode" label="库位" width="110" />
          <el-table-column prop="ownerCode" label="货主" width="80" />
          <el-table-column prop="itemCode" label="物料" width="100" />
          <el-table-column prop="lotNo" label="批次" width="110" />
          <el-table-column prop="systemQty" label="账面" width="80" />
          <el-table-column prop="countQty" label="实盘" width="80" />
          <el-table-column label="差异" width="80"><template #default="{ row }"><span :class="diffClass(row.diffQty)">{{ row.diffQty ?? '-' }}</span></template></el-table-column>
          <el-table-column prop="assignee" label="盘点人" width="90" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }"><TaskActions :row="row" @done="loadTasks" /></template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="taskQuery.current" v-model:page-size="taskQuery.size" :total="taskTotal" layout="total, sizes, prev, pager, next" @change="loadTasks" />
        </div>
      </template>

      <!-- ============ 调整单 ============ -->
      <template v-if="tab === 'adjust'">
        <div class="toolbar">
          <el-input v-model="adjQuery.keyword" placeholder="调整单号 / 计划号" clearable @keyup.enter="loadAdjusts" @clear="loadAdjusts" />
          <el-select v-model="adjQuery.status" placeholder="状态" clearable @change="loadAdjusts" style="width: 130px">
            <el-option v-for="s in ['PENDING', 'APPROVED', 'REJECTED']" :key="s" :value="s"><StatusTag :value="s" /></el-option>
          </el-select>
          <el-button type="primary" @click="loadAdjusts"><el-icon><Search /></el-icon>查询</el-button>
        </div>
        <el-table :data="adjRows" v-loading="loading" border stripe size="small">
          <el-table-column prop="code" label="调整单号" width="190" />
          <el-table-column prop="planCode" label="盘点计划" width="190" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          <el-table-column prop="warehouseCode" label="仓库" width="80" />
          <el-table-column prop="lineCount" label="行数" width="70" />
          <el-table-column prop="gainQty" label="盘盈" width="80" />
          <el-table-column prop="lossQty" label="盘亏" width="80" />
          <el-table-column prop="approver" label="审核人" width="90" />
          <el-table-column prop="approveOpinion" label="审核意见" min-width="140" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openAdjust(row)">明细</el-button>
              <el-button v-if="canWrite() && row.status === 'PENDING'" link type="warning" size="small" @click="openApproveAdjust(row)">审核</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="adjQuery.current" v-model:page-size="adjQuery.size" :total="adjTotal" layout="total, sizes, prev, pager, next" @change="loadAdjusts" />
        </div>
      </template>
    </div>

    <!-- 新建 / 编辑计划 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑盘点计划' : '新建盘点计划'" width="640px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="计划名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="仓库" required>
          <el-select v-model="form.warehouseCode" style="width: 100%">
            <el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点类型" required>
          <el-radio-group v-model="form.type">
            <el-radio-button v-for="(t, k) in TYPES" :key="k" :value="k">{{ t }}</el-radio-button>
          </el-radio-group>
          <div class="hint">{{ TYPE_HINT[form.type] }}</div>
        </el-form-item>
        <el-form-item label="盘点范围">
          <el-radio-group v-model="form.scopeType">
            <el-radio-button value="ALL">全仓</el-radio-button>
            <el-radio-button value="ZONE">按库区</el-radio-button>
            <el-radio-button value="ITEM">按物料</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'ZONE'" label="库区" required>
          <el-select v-model="form.zoneCode" style="width: 100%">
            <el-option v-for="o in zoneOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'ITEM'" label="物料编码" required>
          <el-input v-model="form.itemCodes" placeholder="多个用逗号分隔，如 SKU001,SKU002" />
        </el-form-item>
        <el-form-item v-if="form.type === 'CYCLE'" label="ABC 等级">
          <el-checkbox-group v-model="abcList">
            <el-checkbox value="A" label="A" /><el-checkbox value="B" label="B" /><el-checkbox value="C" label="C" />
          </el-checkbox-group>
          <div class="hint">循环盘点仅盘选中等级的物料；不选则不按 ABC 过滤</div>
        </el-form-item>
        <el-form-item v-if="['MOVEMENT', 'ABNORMAL'].includes(form.type)" label="异动起始日">
          <el-date-picker v-model="form.sinceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="默认最近 7 天" />
        </el-form-item>
        <el-form-item v-if="form.type === 'RANDOM'" label="抽盘比例 %">
          <el-input-number v-model="form.samplePercent" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审批 -->
    <el-dialog v-model="approveVisible" :title="approveTarget.kind === 'plan' ? '审批盘点计划' : '审核库存调整单'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="单号">{{ approveTarget.code }}</el-form-item>
        <el-form-item label="结果">
          <el-radio-group v-model="approveForm.pass">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见"><el-input v-model="approveForm.opinion" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <el-alert v-if="approveTarget.kind === 'adjust' && approveForm.pass" type="warning" :closable="false">通过后立即按调整单过账库存并写入流水。</el-alert>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doApprove">确定</el-button>
      </template>
    </el-dialog>

    <!-- 计划详情 -->
    <el-drawer v-model="detailVisible" :title="`盘点计划 ${plan.code || ''}`" size="75%">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="状态"><StatusTag :value="plan.status" /></el-descriptions-item>
        <el-descriptions-item label="名称">{{ plan.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ TYPES[plan.type] }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ plan.warehouseCode }}</el-descriptions-item>
        <el-descriptions-item label="范围" :span="2">{{ scopeText(plan) }}</el-descriptions-item>
        <el-descriptions-item label="审批">{{ plan.approver || '-' }} {{ plan.approveOpinion || '' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ plan.createdBy }}</el-descriptions-item>
      </el-descriptions>
      <el-steps :active="stepIndex" finish-status="success" simple style="margin: 14px 0">
        <el-step title="草稿" /><el-step title="待审批" /><el-step title="已审批" /><el-step title="执行中" /><el-step title="完成" />
      </el-steps>

      <template v-if="report.taskCount !== undefined">
        <el-row :gutter="12" style="margin-bottom: 12px">
          <el-col :span="4"><el-statistic title="任务数" :value="report.taskCount" /></el-col>
          <el-col :span="4"><el-statistic title="已完成" :value="report.doneCount" /></el-col>
          <el-col :span="4"><el-statistic title="进度 %" :value="Number(report.progressPercent)" /></el-col>
          <el-col :span="4"><el-statistic title="准确率 %" :value="Number(report.accuracyPercent)" /></el-col>
          <el-col :span="4"><el-statistic title="盘盈" :value="Number(report.gainQty)" /></el-col>
          <el-col :span="4"><el-statistic title="盘亏" :value="Number(report.lossQty)" /></el-col>
        </el-row>
        <div class="hint">初盘差异 {{ report.firstDiffCount }} 条，最终不符 {{ report.finalMismatchCount }} 条，最多复盘 {{ report.maxRecountRound }} 轮，账面合计 {{ report.systemQty }}</div>
      </template>

      <div class="toolbar" style="margin-top: 10px" v-if="canWrite() && plan.status === 'EXECUTING'">
        <el-button type="primary" size="small" @click="genAdjust">生成库存调整单</el-button>
        <el-button type="success" size="small" @click="act(inventory.planComplete, plan.id, '盘点计划已完成', true)">完成计划</el-button>
      </div>

      <h4>盘点任务 ({{ planTasks.length }})</h4>
      <el-table :data="planTasks" size="small" border max-height="320">
        <el-table-column prop="locationCode" label="库位" width="110" />
        <el-table-column prop="itemCode" label="物料" width="100" />
        <el-table-column prop="lotNo" label="批次" width="110" />
        <el-table-column prop="systemQty" label="账面" width="80" />
        <el-table-column prop="countQty" label="实盘" width="80" />
        <el-table-column label="差异" width="80"><template #default="{ row }"><span :class="diffClass(row.diffQty)">{{ row.diffQty ?? '-' }}</span></template></el-table-column>
        <el-table-column prop="assignee" label="盘点人" width="90" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }"><TaskActions :row="row" @done="refreshDetail" /></template>
        </el-table-column>
      </el-table>

      <h4>差异复盘 ({{ recounts.length }})</h4>
      <el-table :data="recounts" size="small" border max-height="300">
        <el-table-column prop="roundNo" label="轮次" width="60" />
        <el-table-column prop="locationCode" label="库位" width="110" />
        <el-table-column prop="itemCode" label="物料" width="100" />
        <el-table-column prop="lotNo" label="批次" width="110" />
        <el-table-column prop="systemQty" label="账面" width="80" />
        <el-table-column prop="firstQty" label="初盘" width="80" />
        <el-table-column prop="recountQty" label="复盘" width="80" />
        <el-table-column prop="finalQty" label="最终" width="80" />
        <el-table-column label="结论" width="90"><template #default="{ row }"><StatusTag v-if="row.finalResult" :value="row.finalResult" /><span v-else>-</span></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <template v-if="canWrite()">
              <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="promptQty('复盘数量', row.systemQty, (q) => inventory.planRecountCount(row.id, q), refreshDetail)">录入复盘</el-button>
              <el-button v-if="row.status === 'RECOUNTED'" link type="success" size="small" @click="act(inventory.planRecountConfirm, row.id, '复盘已确认', true)">确认</el-button>
              <el-button v-if="row.status === 'RECOUNTED'" link type="warning" size="small" @click="act(inventory.planRecountNext, row.id, '已生成下一轮复盘', true)">再复盘一轮</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <h4 v-if="(report.adjusts || []).length">库存调整单</h4>
      <el-table v-if="(report.adjusts || []).length" :data="report.adjusts" size="small" border>
        <el-table-column prop="code" label="调整单号" width="190" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="lineCount" label="行数" width="70" />
        <el-table-column prop="gainQty" label="盘盈" width="80" />
        <el-table-column prop="lossQty" label="盘亏" width="80" />
        <el-table-column prop="approver" label="审核人" width="90" />
        <el-table-column prop="approveOpinion" label="意见" min-width="120" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openAdjust(row)">明细</el-button>
            <el-button v-if="canWrite() && row.status === 'PENDING'" link type="warning" size="small" @click="openApproveAdjust(row)">审核</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 调整单明细 -->
    <el-dialog v-model="adjustVisible" :title="`调整单 ${adjust.code || ''}`" width="860px">
      <el-descriptions :column="4" border size="small" style="margin-bottom: 10px">
        <el-descriptions-item label="状态"><StatusTag :value="adjust.status" /></el-descriptions-item>
        <el-descriptions-item label="盘点计划">{{ adjust.planCode }}</el-descriptions-item>
        <el-descriptions-item label="盘盈 / 盘亏">{{ adjust.gainQty }} / {{ adjust.lossQty }}</el-descriptions-item>
        <el-descriptions-item label="审核">{{ adjust.approver || '-' }} {{ adjust.approveOpinion || '' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="adjustLines" size="small" border max-height="400">
        <el-table-column prop="locationCode" label="库位" width="110" />
        <el-table-column prop="ownerCode" label="货主" width="80" />
        <el-table-column prop="itemCode" label="物料" width="100" />
        <el-table-column prop="lotNo" label="批次" width="110" />
        <el-table-column prop="fromQty" label="账面" width="80" />
        <el-table-column prop="toQty" label="调整为" width="80" />
        <el-table-column label="差异" width="80"><template #default="{ row }"><span :class="diffClass(row.diffQty)">{{ row.diffQty }}</span></template></el-table-column>
        <el-table-column prop="reason" label="原因" min-width="120" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="qtyVisible" :title="qtyDialog.title" width="380px">
      <el-input-number v-model="qtyDialog.value" :min="0" :precision="3" style="width: 100%" />
      <template #footer>
        <el-button @click="qtyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmQty">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox, ElPopconfirm } from 'element-plus'
import { canWrite } from '../../auth'
import { inventory } from '../../api'
import StatusTag from '../../components/StatusTag.vue'
import { useOptions } from '../../composables/useOptions'
import { fmt } from '../../utils'

const PLAN_STATUSES = ['DRAFT', 'PENDING', 'APPROVED', 'EXECUTING', 'COMPLETED', 'CANCELLED']
const TASK_STATUSES = ['PENDING', 'CLAIMED', 'DONE', 'CANCELLED']
const TYPES = { CYCLE: '循环盘点', MOVEMENT: '动碰盘点', RANDOM: '随机抽盘', ABNORMAL: '异动盘点' }
const TYPE_HINT = {
  CYCLE: '按 ABC 等级周期性盘点，A 类高频、C 类低频',
  MOVEMENT: '只盘最近发生过收发/移库的库存',
  RANDOM: '在范围内按比例随机抽取库存盘点',
  ABNORMAL: '只盘最近发生过调整/冻结等异常变动的库存'
}
const STEP = { DRAFT: 0, PENDING: 1, APPROVED: 2, EXECUTING: 3, COMPLETED: 5, CANCELLED: 0 }

const { options } = useOptions(['warehouse', 'zone'])
const tab = ref('plan')
const loading = ref(false)
const saving = ref(false)

const rows = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, keyword: '', status: '', type: '' })
const taskRows = ref([])
const taskTotal = ref(0)
const taskQuery = reactive({ current: 1, size: 20, keyword: '', status: '', assignee: '' })
const adjRows = ref([])
const adjTotal = ref(0)
const adjQuery = reactive({ current: 1, size: 20, keyword: '', status: '' })

const formVisible = ref(false)
const form = ref({})
const abcList = ref([])
const zoneOptions = computed(() => (options.value.zone || []).filter((z) => !form.value.warehouseCode || z.warehouseCode === form.value.warehouseCode))

const approveVisible = ref(false)
const approveTarget = ref({})
const approveForm = reactive({ pass: true, opinion: '' })

const detailVisible = ref(false)
const plan = ref({})
const planTasks = ref([])
const recounts = ref([])
const report = ref({})
const stepIndex = computed(() => STEP[plan.value.status] ?? 0)

const adjustVisible = ref(false)
const adjust = ref({})
const adjustLines = ref([])

const qtyVisible = ref(false)
const qtyDialog = reactive({ title: '', value: 0, fn: null, after: null })

function scopeText(p) {
  if (!p) return ''
  const parts = []
  if (p.scopeType === 'ZONE') parts.push(`库区 ${p.zoneCode}`)
  else if (p.scopeType === 'ITEM') parts.push(`物料 ${p.itemCodes}`)
  else parts.push('全仓')
  if (p.abcClasses) parts.push(`ABC:${p.abcClasses}`)
  if (p.sinceDate) parts.push(`自 ${p.sinceDate}`)
  if (p.type === 'RANDOM' && p.samplePercent) parts.push(`抽 ${p.samplePercent}%`)
  return parts.join(' · ')
}
const diffClass = (d) => (d == null || Number(d) === 0 ? '' : Number(d) > 0 ? 'gain' : 'loss')

async function load() {
  loading.value = true
  try {
    const p = await inventory.planPage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}
async function loadTasks() {
  loading.value = true
  try {
    const p = await inventory.planTaskPage(taskQuery)
    taskRows.value = p.records
    taskTotal.value = p.total
  } finally {
    loading.value = false
  }
}
async function loadAdjusts() {
  loading.value = true
  try {
    const p = await inventory.planAdjustPage(adjQuery)
    adjRows.value = p.records
    adjTotal.value = p.total
  } finally {
    loading.value = false
  }
}
function loadTab() {
  if (tab.value === 'plan') load()
  else if (tab.value === 'task') loadTasks()
  else loadAdjusts()
}

function openForm(row) {
  form.value = row
    ? { ...row }
    : { type: 'CYCLE', scopeType: 'ALL', warehouseCode: options.value.warehouse?.[0]?.value, samplePercent: 20 }
  abcList.value = row?.abcClasses ? row.abcClasses.split(',').filter(Boolean) : []
  formVisible.value = true
}
async function saveForm() {
  saving.value = true
  try {
    const data = { ...form.value, abcClasses: form.value.type === 'CYCLE' ? abcList.value.join(',') : null }
    if (data.scopeType !== 'ZONE') data.zoneCode = null
    if (data.scopeType !== 'ITEM') data.itemCodes = null
    if (data.id) await inventory.planUpdate(data.id, data)
    else await inventory.planCreate(data)
    ElMessage.success('已保存')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function act(fn, id, msg, inDetail) {
  saving.value = true
  try {
    await fn(id)
    ElMessage.success(msg)
    await load()
    if (inDetail || detailVisible.value) await refreshDetail()
  } finally {
    saving.value = false
  }
}

function openApprove(row) {
  approveTarget.value = { kind: 'plan', id: row.id, code: row.code }
  approveForm.pass = true
  approveForm.opinion = ''
  approveVisible.value = true
}
function openApproveAdjust(row) {
  approveTarget.value = { kind: 'adjust', id: row.id, code: row.code }
  approveForm.pass = true
  approveForm.opinion = ''
  approveVisible.value = true
}
async function doApprove() {
  saving.value = true
  try {
    if (approveTarget.value.kind === 'plan') await inventory.planApprove(approveTarget.value.id, { ...approveForm })
    else await inventory.planAdjustApprove(approveTarget.value.id, { ...approveForm })
    ElMessage.success(approveForm.pass ? '已通过' : '已驳回')
    approveVisible.value = false
    loadTab()
    if (detailVisible.value) await refreshDetail()
  } finally {
    saving.value = false
  }
}

async function openDetail(row) {
  plan.value = row
  detailVisible.value = true
  await refreshDetail()
}
async function refreshDetail() {
  const id = plan.value.id
  const [p, t, r, rep] = await Promise.all([inventory.planGet(id), inventory.planTasks(id), inventory.planRecounts(id), inventory.planReport(id)])
  plan.value = p
  planTasks.value = t
  recounts.value = r
  report.value = rep
}
async function genAdjust() {
  saving.value = true
  try {
    const a = await inventory.planAdjustGenerate(plan.value.id)
    ElMessage.success(`调整单 ${a.code} 已生成，${a.lineCount} 行，待审核`)
    await refreshDetail()
  } finally {
    saving.value = false
  }
}

async function openAdjust(row) {
  const [a, lines] = await Promise.all([inventory.planAdjustGet(row.id), inventory.planAdjustLines(row.id)])
  adjust.value = a
  adjustLines.value = lines
  adjustVisible.value = true
}

function promptQty(title, initial, fn, after) {
  qtyDialog.title = title
  qtyDialog.value = Number(initial ?? 0)
  qtyDialog.fn = fn
  qtyDialog.after = after
  qtyVisible.value = true
}
async function confirmQty() {
  saving.value = true
  try {
    await qtyDialog.fn(qtyDialog.value)
    ElMessage.success('已提交')
    qtyVisible.value = false
    await qtyDialog.after?.()
  } finally {
    saving.value = false
  }
}

/** 任务行操作（列表页与详情页共用） */
const TaskActions = defineComponent({
  props: { row: Object },
  emits: ['done'],
  setup(props, { emit }) {
    const run = async (fn, msg) => {
      await fn()
      ElMessage.success(msg)
      emit('done')
    }
    const assign = async () => {
      const { value } = await ElMessageBox.prompt('指派盘点人(用户名)', '指派', { inputValue: props.row.assignee || '' })
      await run(() => inventory.planTaskAssign(props.row.id, value), '已指派')
    }
    return () => {
      if (!canWrite()) return null
      const r = props.row
      const btns = []
      if (r.status === 'PENDING') {
        btns.push(h(ElButton, { link: true, type: 'primary', size: 'small', onClick: () => run(() => inventory.planTaskClaim(r.id), '已领取') }, () => '领取'))
        btns.push(h(ElButton, { link: true, size: 'small', onClick: assign }, () => '指派'))
        btns.push(
          h(ElPopconfirm, { title: '删除任务并释放该库存锁定?', onConfirm: () => run(() => inventory.planTaskDelete(r.id), '已删除') }, {
            reference: () => h(ElButton, { link: true, type: 'danger', size: 'small' }, () => '删除')
          })
        )
      }
      if (r.status === 'PENDING' || r.status === 'CLAIMED') {
        btns.push(
          h(ElButton, { link: true, type: 'success', size: 'small', onClick: () => promptQty(`实盘数量 (${r.locationCode} ${r.itemCode})`, r.systemQty, (q) => inventory.planTaskCount(r.id, q), () => emit('done')) }, () => '提交实盘')
        )
      }
      return btns
    }
  }
})

onMounted(load)
</script>

<style scoped>
.hint { color: #909399; font-size: 12px; line-height: 1.6; }
.gain { color: #67c23a; font-weight: 600; }
.loss { color: #f56c6c; font-weight: 600; }
h4 { margin: 14px 0 6px; }
</style>
