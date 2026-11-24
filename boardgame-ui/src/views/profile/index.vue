<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { getRecentMatches, type RecentMatch } from '@/api/match'
import { updateProfile, uploadAvatar } from '@/api/user'
import type { UpdateProfileBody } from '@/api/user'
import { ZONES } from '@/constants/catalog'
import { adapterByGameCode } from '@/constants/gameAdapters'
import { useUserStore } from '@/store/user'
import logoWordmark from '@/assets/logo-dicego-transparent.png'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

/** 头像上传：隐藏的 file input + 上传中状态 */
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const MAX_AVATAR_SIZE = 2 * 1024 * 1024

/** 头像地址：优先后端翻译出的 avatarUrl，无则回退首字母 */
const avatarUrl = computed(() => userStore.userInfo?.avatarUrl || '')
const avatarChar = computed(() => {
  const name = userStore.userInfo?.nickName || userStore.userInfo?.userName || ''
  return name ? name.charAt(0) : '?'
})

/** 点击头像 → 触发文件选择 */
function pickAvatar(): void {
  avatarInputRef.value?.click()
}

/** 选图后校验并上传；后端会直接写入 sys_user.avatar，这里只需刷新用户信息 */
async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空 value，允许连续选同一个文件
  input.value = ''
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > MAX_AVATAR_SIZE) {
    ElMessage.error('头像不能超过 2MB')
    return
  }

  avatarUploading.value = true
  try {
    await uploadAvatar(file)
    await userStore.fetchProfile()
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error((error as Error).message || '头像上传失败')
  } finally {
    avatarUploading.value = false
  }
}

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

// ---- 最近对局（战绩，全游戏共用接口） ----

const recentMatches = ref<RecentMatch[]>([])
const recentLoading = ref(false)

async function loadRecent(): Promise<void> {
  recentLoading.value = true
  try {
    const data = await getRecentMatches(5)
    recentMatches.value = data.success ? (data.matches ?? []) : []
  } catch {
    // 战绩失败不打扰用户：按空列表展示
    recentMatches.value = []
  } finally {
    recentLoading.value = false
  }
}

/** game_code → 中文名（未登记的游戏回退原编码） */
function gameName(code: string): string {
  return adapterByGameCode(code)?.label ?? code
}

/** 结果文案：已结束看胜负/名次，未结束标注进行中 */
function resultText(match: RecentMatch): string {
  if (match.status !== '2') return '进行中'
  if (match.win) return '胜'
  return match.rankNo > 0 ? `第 ${match.rankNo} 名` : '负'
}

/** 后端时间形如 2026-11-21T20:12:00 → 11-21 20:12 */
function formatTime(value: string | null): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(5, 16)
}

onMounted(() => {
  const u = userStore.userInfo
  if (u) {
    form.nickName = u.nickName ?? ''
    form.gender = u.gender ?? '2'
    form.age = u.age ?? null
    form.favGameTypes = (u.favGameTypes ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
  }
  void loadRecent()
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
    <img class="profile-logo theme-logo" :src="logoWordmark" alt="DiceGo" />
    <el-card class="profile-card">
      <template #header>
        <div class="profile-head">
          <h2 class="profile-title">个人资料</h2>
          <button type="button" class="btn btn-ghost btn-sm" @click="router.push('/lobby')">
            ⬅ 回到大厅
          </button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="头像">
          <div class="avatar-upload">
            <button
              type="button"
              class="avatar-button"
              :disabled="avatarUploading"
              :title="avatarUploading ? '上传中…' : '点击更换头像'"
              @click="pickAvatar"
            >
              <img v-if="avatarUrl" :src="avatarUrl" alt="头像" class="avatar-image" />
              <span v-else class="avatar-fallback">{{ avatarChar }}</span>
              <span class="avatar-overlay">{{ avatarUploading ? '…' : '📷' }}</span>
            </button>
            <div class="avatar-hint">
              <div>{{ avatarUploading ? '上传中…' : '点击头像更换' }}</div>
              <div class="field-hint">支持 jpg / png / gif / webp，不超过 2MB</div>
            </div>
            <input
              ref="avatarInputRef"
              class="avatar-file-input"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              @change="onAvatarChange"
            />
          </div>
        </el-form-item>

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

    <el-card class="profile-card recent-card">
      <template #header>
        <h2 class="profile-title">最近对局</h2>
      </template>

      <div v-if="recentLoading" class="recent-empty">加载中…</div>
      <div v-else-if="recentMatches.length === 0" class="recent-empty">
        还没有对局记录，去大厅开一局吧
      </div>
      <ul v-else class="recent-list">
        <li v-for="item in recentMatches" :key="item.matchNo" class="recent-item">
          <span class="recent-game">{{ gameName(item.gameCode) }}</span>
          <span class="recent-result" :class="{ win: item.win }">{{ resultText(item) }}</span>
          <span class="recent-meta">
            {{ item.playerCount }} 人 · {{ formatTime(item.endTime || item.startTime) }}
          </span>
        </li>
      </ul>
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

/* ---- 头像上传 ---- */
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-button {
  position: relative;
  width: 76px;
  height: 76px;
  flex: none;
  padding: 0;
  border: 2px solid var(--line);
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  cursor: pointer;
  overflow: visible;
  transition: border-color 0.15s, transform 0.15s;
}

.avatar-button:hover:not(:disabled) {
  border-color: var(--color-primary);
  transform: translateY(-1px);
}

.avatar-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.avatar-image,
.avatar-fallback {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-fallback {
  display: grid;
  place-items: center;
  font-size: 30px;
  font-family: var(--font-display);
}

.avatar-overlay {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  border: 2px solid var(--bg-base);
  font-size: 12px;
}

.avatar-hint {
  font-size: 13px;
  line-height: 1.6;
}

.avatar-file-input {
  display: none;
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

/* ---- 最近对局 ---- */
.recent-card :deep(.el-card__body) {
  padding-top: 8px;
}

.recent-empty {
  padding: 12px 0;
  font-size: 13px;
  color: var(--muted);
}

.recent-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.recent-item {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-game {
  font-weight: 600;
}

.recent-result {
  font-size: 13px;
  color: var(--muted);
}

.recent-result.win {
  color: var(--color-primary);
  font-weight: 600;
}

.recent-meta {
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
}
</style>
