<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { CLIENT_ID, getCaptcha } from '@/api/auth'
import type { LoginBody } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  code: '',
  uuid: '',
})

const captchaEnabled = ref(true)
const captchaImg = ref('')
const loading = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
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

/** 提交登录 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const body: LoginBody = {
    clientId: CLIENT_ID,
    grantType: 'password',
    username: form.username,
    password: form.password,
  }
  if (captchaEnabled.value) {
    body.code = form.code
    body.uuid = form.uuid
  }

  loading.value = true
  try {
    await userStore.login(body)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (error) {
    ElMessage.error((error as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <template #header>
        <h2 class="auth-title">登录</h2>
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
          登录
        </el-button>
        <div class="auth-link">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
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
