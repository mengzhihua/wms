<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col :span="4" v-for="s in stats" :key="s.label">
        <div class="stat">
          <div class="label">{{ s.label }}</div>
          <div class="value" :style="{ color: s.color }">{{ s.value }}</div>
        </div>
      </el-col>
    </el-row>
    <div v-if="profiles.length" class="card" style="margin-top: 12px">
      <h3 style="margin: 0 0 12px">仓型作业</h3>
      <div v-for="w in profiles" :key="w.code" style="margin-bottom: 8px">
        <strong>{{ w.code }}</strong> {{ w.name }} · {{ PROFILE[w.profile] || w.profile }}
        <router-link v-if="w.profile === 'FMCG'" to="/outbound/wave-strategy" style="margin-left: 12px">波次策略</router-link>
        <router-link v-if="w.profile === 'FMCG'" to="/basic/pick-slot" style="margin-left: 12px">固定拣货位</router-link>
        <router-link v-if="w.profile === 'BULKY'" to="/inbound/appointment" style="margin-left: 12px">月台预约</router-link>
        <router-link v-if="w.profile === 'BULKY'" to="/basic/lpn" style="margin-left: 12px">容器</router-link>
        <router-link v-if="w.profile === 'VMI'" to="/basic/vmi-agreement" style="margin-left: 12px">VMI 协议</router-link>
        <router-link v-if="w.profile === 'VMI'" to="/inventory/consumption" style="margin-left: 12px">消耗单</router-link>
      </div>
    </div>
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="14">
        <div class="card">
          <h3 style="margin: 0 0 12px">最近库存流水</h3>
          <el-table :data="data.recentTxns || []" size="small" stripe>
            <el-table-column label="类型" width="80"><template #default="{ row }"><StatusTag :value="row.txnType" /></template></el-table-column>
            <el-table-column prop="itemCode" label="物料" width="100" />
            <el-table-column prop="lotNo" label="批次" width="110" />
            <el-table-column prop="fromLocation" label="源库位" width="110" />
            <el-table-column prop="toLocation" label="目标库位" width="110" />
            <el-table-column prop="qty" label="数量" width="80" />
            <el-table-column prop="refNo" label="单据号" />
            <el-table-column prop="createdAt" label="时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="card">
          <h3 style="margin: 0 0 12px">安全库存预警</h3>
          <el-table :data="data.lowStock || []" size="small" stripe>
            <el-table-column prop="itemCode" label="物料编码" width="110" />
            <el-table-column prop="itemName" label="物料名称" />
            <el-table-column prop="qty" label="现有" width="80" />
            <el-table-column prop="minStock" label="安全库存" width="90" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { basic, dashboard } from '../api'
import StatusTag from '../components/StatusTag.vue'
import { fmt } from '../utils'

const PROFILE = { FMCG: '快消', BULKY: '大件', VMI: 'VMI' }
const data = ref({})
const profiles = ref([])
const stats = computed(() => [
  { label: '物料数', value: data.value.itemCount ?? '-' },
  { label: '存储库位使用率', value: data.value.locationTotal ? `${data.value.locationUsed}/${data.value.locationTotal}` : '-' },
  { label: '库存总量', value: data.value.inventoryQty ?? '-', color: '#409eff' },
  { label: '待处理入库单', value: data.value.asnOpen ?? '-', color: '#e6a23c' },
  { label: '待上架任务', value: data.value.putawayOpen ?? '-', color: '#e6a23c' },
  { label: '待处理出库单 / 拣货', value: `${data.value.orderOpen ?? '-'} / ${data.value.pickOpen ?? '-'}`, color: '#f56c6c' }
])
onMounted(async () => {
  data.value = await dashboard()
  const rows = await basic.warehouse.list()
  profiles.value = rows.filter((w) => w.profile && w.profile !== 'GENERAL')
})
</script>
