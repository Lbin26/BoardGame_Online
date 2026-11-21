<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { updateProfile } from '@/api/user'
import type { UpdateProfileBody } from '@/api/user'
import { ZONES } from '@/constants/catalog'
import { useUserStore } from '@/store/user'
import logoWordmark from '@/assets/logo-wordmark.png'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

/** 表单：名字（昵称）/性别/年龄/偏好游戏类型（多选，存分区 key） */
const form = reactive({
  nickName: '',
  gender: '2',
  age: null as number | null,
  favGameTypes: [] as string[],
})

const rules: FormRules = {
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    {
      // required 不拦纯空格；trim 后为空也须拒绝，否则提交空昵称
      validator: (_rule, value: string, callback: (e?: Error) => void) => {
        if (typeof value !== 'string' || value.trim().length === 0) {
          callback(new Error('请输入昵称'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
    { max: 30, message: '昵称不能超过 30 个字符', trigger: 'blur' },
  ],
  age: [
    {
      validator: (_rule, value: number | null, callback: (e?: Error) => void) => {
        if (value == null || (Number.isInteger(value) && value >= 1 && value <= 150)) {
          callback()
        } else {
          callback(new Error('年龄须为 1-150 的整数'))
        }
      },
      trigger: 'blur',
    },
  ],
}

/** 只读展示：登录账号不可修改（RuoYi 原生约束，改账号会破坏登录凭据） */
const userName = computed(() => userStore.userInfo?.userName ?? '')

onMounted(() => {
  const u = userStore.userInfo
  if (!u) return
  form.nickName = u.nickName ?? ''
  form.gender = u.gender ?? '2'
  form.age = u.age ?? null
  form.favGameTypes = (u.favGameTypes ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
})

/** 提交：把分区 key 数组拼成逗号分隔字符串，一次 PUT 写回后端 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const body: UpdateProfileBody = {
    nickName: form.nickName.trim(),
    gender: form.gender,
    age: form.age,
    favGameTypes: form.favGameTypes.join(','),
  }

  loading.value = true
  try {
    await updateProfile(body)
    await userStore.fetchProfile()
    ElMessage.success('个人资料已保存')
    router.push('/lobby')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="profile-page">
    <img class="profile-logo" :src="logoWordmark" alt="DiceGo" />
    <el-card class="profile-card">
      <template #header>
        <div class="profile-head">
          <h2 class="profile-title">个人资料</h2>
          <button type="button" class="btn btn-ghost btn-sm" @click="router.push('/lobby')">
            ← 返回大厅
          </button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="登录账号">
          <el-input :model-value="userName" disabled />
          <div class="field-hint">登录账号不可修改</div>
        </el-form-item>

        <el-form-item label="昵称" prop="nickName">
          <el-input v-model="form.nickName" placeholder="请输入昵称" maxlength="30" />
        </el-form-item>

        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="0">男</el-radio>
            <el-radio value="1">女</el-radio>
            <el-radio value="2">保密</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="年龄" prop="age">
          <el-input-number
            v-model="form.age"
            :min="1"
            :max="150"
            :controls="false"
            placeholder="选填"
          />
        </el-form-item>

        <el-form-item label="偏好游戏类型">
          <div class="zone-grid">
            <label
              v-for="zone in ZONES"
              :key="zone.key"
              class="zone-chip"
              :class="{ active: form.favGameTypes.includes(zone.key) }"
            >
              <input v-model="form.favGameTypes" type="checkbox" :value="zone.key" />
              <span class="zone-icon">{{ zone.icon }}</span>
              <span>{{ zone.name }}</span>
            </label>
          </div>
          <div class="field-hint">可多选，用于日后个性化推荐</div>
        </el-form-item>

        <div class="profile-actions">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="loading"
            @click="handleSubmit"
          >
            {{ loading ? '保存中…' : '保存资料' }}
          </button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 32px 16px;
  background: linear-gradient(160deg, var(--bg-gradient-start), var(--bg-gradient-end));
}

.profile-logo {
  width: 180px;
  height: auto;
}

.profile-card {
  width: min(560px, 100%);
}

.profile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.profile-title {
  margin: 0;
  font-size: 20px;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}

.zone-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.zone-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  transition: background 0.15s, border-color 0.15s;
}

.zone-chip.active {
  border-color: var(--color-primary);
  background: var(--accent-soft);
}

.zone-chip input {
  accent-color: var(--color-primary);
}

.zone-icon {
  font-size: 15px;
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
