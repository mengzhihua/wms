<template>
  <el-container style="height: 100%">
    <el-aside width="220px" style="background: #1f2d3d; color: #fff">
      <div style="height: 56px; display: flex; align-items: center; padding: 0 16px; font-size: 18px; font-weight: 600">
        <el-icon style="margin-right: 8px"><Box /></el-icon> WMS 仓储管理
      </div>
      <el-menu :default-active="route.path" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#409eff" :default-openeds="sideMenus.filter(m => m.children).map(m => m.path)">
        <template v-for="m in sideMenus" :key="m.path">
          <el-sub-menu v-if="m.children" :index="m.path">
            <template #title><el-icon><component :is="m.icon" /></el-icon><span>{{ m.name }}</span></template>
            <el-menu-item v-for="c in m.children" :key="c.path" :index="`${m.path}/${c.path}`">{{ c.name }}</el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.path"><el-icon><component :is="m.icon" /></el-icon><span>{{ m.name }}</span></el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e4e7ed; display: flex; align-items: center; justify-content: space-between; height: 56px">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="b in crumbs" :key="b">{{ b }}</el-breadcrumb-item>
        </el-breadcrumb>
        <el-dropdown @command="onCommand">
          <span style="color: #606266; cursor: pointer; display: flex; align-items: center; gap: 4px">
            <el-icon><User /></el-icon>{{ auth.user?.realName || auth.user?.username }}
            <el-tag size="small" type="info">{{ ROLE_LABEL[auth.user?.role] || auth.user?.role }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main style="padding: 0; overflow: auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="pwdVisible" title="修改密码" width="420px" destroy-on-close>
    <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="90px">
      <el-form-item label="原密码" prop="oldPassword"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码" prop="newPassword"><el-input v-model="pwd.newPassword" type="password" show-password /></el-form-item>
      <el-form-item label="确认新密码" prop="confirm"><el-input v-model="pwd.confirm" type="password" show-password /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdSaving" @click="changePassword">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { menus, visibleMenus } from '../router'
import { authApi } from '../api'
import { auth, clearAuth, ROLE_LABEL } from '../auth'

const route = useRoute()
const router = useRouter()
const sideMenus = computed(() => visibleMenus())

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdRef = ref()
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '新密码至少 6 位', trigger: 'blur' }],
  confirm: [{ validator: (_, v, cb) => cb(v === pwd.newPassword ? undefined : new Error('两次输入不一致')), trigger: 'blur' }]
}

async function onCommand(cmd) {
  if (cmd === 'password') {
    Object.assign(pwd, { oldPassword: '', newPassword: '', confirm: '' })
    pwdVisible.value = true
  } else if (cmd === 'logout') {
    try { await authApi.logout() } catch (e) { /* 令牌已失效也允许本地退出 */ }
    clearAuth()
    router.replace('/login')
  }
}

async function changePassword() {
  await pwdRef.value.validate()
  pwdSaving.value = true
  try {
    await authApi.changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    clearAuth()
    router.replace('/login')
  } finally {
    pwdSaving.value = false
  }
}
const crumbs = computed(() => {
  for (const m of menus) {
    if (m.children) {
      const c = m.children.find((c) => `${m.path}/${c.path}` === route.path)
      if (c) return [m.name, c.name]
    } else if (m.path === route.path) return [m.name]
  }
  return []
})
</script>
