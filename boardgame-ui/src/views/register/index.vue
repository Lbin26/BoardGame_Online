<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { CLIENT_ID, getCaptcha, register } from '@/api/auth'
import type { RegisterBody } from '@/api/auth'

const router = useRouter()

const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  code: '',
  uuid: '',
})

const captchaEnabled = ref(true)
const captchaImg = ref('')
const loading = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请再次输入密码', trigger: 'blur' }],
}

/** 获取/刷新图形验证码 */
async function refreshCaptcha(): Promise<void> {
  const data = await getCaptcha()
  captchaEnabled.value = data.captchaEnabled
  if (data.captchaEnabled) {
    captchaImg.value = data.img
    form.uuid = data.uuid
  }
}

onMounted(refreshCaptcha)

/** 提交注册 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  if (form.password !== form.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const body: RegisterBody = {
    clientId: CLIENT_ID,
    grantType: 'password',
    username: form.username,
    password: form.password,
    userType: 'sys_user',
  }
  if (captchaEnabled.value) {
    body.code = form.code
    body.uuid = form.uuid
  }

  loading.value = true
  try {
    await register(body)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    ElMessage.error((error as Error).message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <template #header>
        <h2 class="auth-title">注册</h2>
      </template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item v-if="captchaEnabled" label="验证码" prop="code">
          <div class="captcha-row">
            <el-input v-model="form.code" placeholder="请输入验证码" />
            <img
              v-if="captchaImg"
              class="captcha-img"
              :src="captchaImg"
              alt="验证码"
              title="点击刷新"
              @click="refreshCaptcha"
            />
          </div>
        </el-form-item>
        <el-button
          type="primary"
          class="auth-submit"
          :loading="loading"
          @click="handleSubmit"
        >
          注册
        </el-button>
        <div class="auth-link">
          已有账号？
          <router-link to="/login">去登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
}

.auth-title {
  margin: 0;
  text-align: center;
}

.auth-submit {
  width: 100%;
}

.auth-link {
  margin-top: 16px;
  text-align: center;
  font-size: 14px;
}

.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.captcha-img {
  height: 40px;
  width: 120px;
  cursor: pointer;
  border-radius: 4px;
}
</style>
