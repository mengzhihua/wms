<template>
  <div class="login-bg">
    <el-card class="login-card" shadow="always">
      <div class="login-title"><el-icon size="28"><Box /></el-icon><span>WMS 仓储管理系统</span></div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" autofocus>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password @keyup.enter="submit">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">登 录</el-button>
      </el-form>
      <div class="login-hint">初始管理员 admin / admin123，登录后请及时修改密码</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { setAuth } from '../auth'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const r = await authApi.login(form)
    setAuth(r.token, r.user)
    ElMessage.success(`欢迎，${r.user.realName || r.user.username}`)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg { height: 100%; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1f2d3d 0%, #2f4a6d 100%); }
.login-card { width: 380px; padding: 8px 12px; }
.login-title { display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 20px; font-weight: 600; margin: 8px 0 24px; color: #1f2d3d; }
.login-hint { margin-top: 16px; text-align: center; color: #909399; font-size: 12px; }
</style>
