<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-alert type="info" :closable="false" show-icon style="flex: 1">
          所有登录用户可查看数据；写操作按权限码授权。ADMIN 固定拥有全部权限，内置角色不可删除。角色权限修改后立即生效（用户重新登录后前端菜单/按钮同步刷新）。
        </el-alert>
        <el-button v-if="editable" type="success" @click="openForm()"><el-icon><Plus /></el-icon>新增角色</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="角色编码" width="130" />
        <el-table-column prop="name" label="角色名称" width="140" />
        <el-table-column label="权限" min-width="360">
          <template #default="{ row }">
            <el-tag v-if="row.perms === '*'" type="danger" size="small">全部权限</el-tag>
            <template v-else-if="row.perms">
              <el-tag v-for="p in row.perms.split(',')" :key="p" size="small" style="margin: 2px 4px 2px 0">{{ permLabels[p] || p }}</el-tag>
            </template>
            <el-tag v-else type="info" size="small">只读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内置" width="70">
          <template #default="{ row }"><el-tag :type="row.builtin ? 'warning' : 'info'" size="small">{{ row.builtin ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="70">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="editable" label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-popconfirm v-if="!row.builtin" title="确认删除该角色?" @confirm="remove(row)">
              <template #reference><el-button link type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="visible" :title="(form.id ? '编辑' : '新增') + '角色'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="!!form.builtin" placeholder="如 APPROVER, 保存后自动转大写" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="form.permList" :disabled="form.code === 'ADMIN'">
            <el-checkbox v-for="(label, code) in permLabels" :key="code" :label="code" style="width: 48%">{{ label }} <span class="perm-code">{{ code }}</span></el-checkbox>
          </el-checkbox-group>
          <div v-if="form.code === 'ADMIN'" class="hint">ADMIN 固定为全部权限</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" :disabled="form.code === 'ADMIN'" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { system } from '../../api'
import { canEditSystem } from '../../auth'

const rows = ref([])
const permLabels = ref({})
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const formRef = ref()
const form = ref({ permList: [] })
const editable = computed(() => canEditSystem())

const rules = {
  code: [{ required: true, message: '角色编码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const [list, perms] = await Promise.all([system.role.list(), system.role.perms()])
    rows.value = list
    permLabels.value = perms
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  form.value = row
    ? { ...row, permList: row.perms && row.perms !== '*' ? row.perms.split(',') : [] }
    : { status: 1, permList: [] }
  visible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const body = { ...form.value, perms: form.value.code === 'ADMIN' ? '*' : form.value.permList.join(',') }
    delete body.permList
    if (body.id) await system.role.update(body.id, body)
    else await system.role.create(body)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await system.role.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.perm-code { color: #909399; font-size: 12px; margin-left: 4px; }
.hint { color: #909399; font-size: 12px; }
</style>
