<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { CLIENT_ID, getCaptcha, register } from '@/api/auth'
import type { RegisterBody } from '@/api/auth'
import logoWordmark from '@/assets/logo-wordmark.png'

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
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 30, message: '用户名长度需为 2-30 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 5, max: 30, message: '密码长度需为 5-30 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

/** 获取/刷新图形验证码 */
async function refreshCaptcha(): Promise<void> {
  const data = await getCaptcha()
  captchaEnabled.value = data.captchaEnabled
  if (data.captchaEnabled) {
    captchaImg.value = 'data:image/png;base64,' + data.img
    form.uuid = data.uuid
  }
}

onMounted(() => {
  refreshCaptcha().catch(() => ElMessage.error('验证码加载失败，请重试'))
})

/** 手动点击刷新验证码：失败仅提示，避免未处理 rejection */
function onCaptchaClick(): void {
  refreshCaptcha().catch(() => ElMessage.error('验证码刷新失败，请重试'))
}

/** 提交注册 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
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
    // 验证码一次性：失败后必须换新，否则重试必报"验证码已失效"
    // 刷新自身也可能失败（如网络中断）：兜底提示，避免未处理 rejection
    try {
      await refreshCaptcha()
    } catch {
      ElMessage.error('验证码刷新失败，请重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <img class="auth-logo" :src="logoWordmark" alt="DiceGo" />
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
              @click="onCaptchaClick"
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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  min-height: 100vh;
  padding: 24px;
}

.auth-logo {
  height: 44px;
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
