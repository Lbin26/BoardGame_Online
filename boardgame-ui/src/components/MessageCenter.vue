<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  countUnread,
  listAdminMessages,
  listMessages,
  markAdminMessageRead,
  markAllRead,
  markMessageRead,
  markOpinionHandled,
  listOpinions,
  sendMessage,
  type InboxMessage,
  type Opinion,
} from '@/api/inbox'
import { isSuperAdmin } from '@/constants/admin'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const userName = computed(() => userStore.userInfo?.userName ?? '')
const nickName = computed(() => userStore.userInfo?.nickName ?? '')
const admin = computed(() => isSuperAdmin(userName.value, nickName.value))

const open = ref(false)
const tab = ref<'inbox' | 'admin'>('inbox')
const unread = ref(0)
const messages = ref<InboxMessage[]>([])
const adminMessages = ref<InboxMessage[]>([])
const opinions = ref<Opinion[]>([])

const recipient = ref('')
const draft = ref('')
const sending = ref(false)

function refresh(): void {
  /** 普通用户看自身未读；管理员额外把待查看意见计入红点 */
  const personal = countUnread(userName.value)
  messages.value = listMessages(userName.value)
  if (admin.value) {
    adminMessages.value = listAdminMessages()
    opinions.value = listOpinions()
    unread.value = personal + adminMessages.value.filter((m) => !m.read).length
  } else {
    unread.value = personal
  }
}

onMounted(refresh)

/** 展开面板：同步未读列表；管理员默认落在通知页 */
function toggle(): void {
  open.value = !open.value
  if (open.value) refresh()
}

function openMessage(message: InboxMessage): void {
  if (!message.read) {
    if (message.kind === 'opinion' && admin.value) markAdminMessageRead(message.id)
    else markMessageRead(message.id)
    refresh()
  }
}

function readAll(): void {
  markAllRead(userName.value)
  refresh()
}

async function handleSend(): Promise<void> {
  const to = recipient.value.trim()
  const content = draft.value.trim()
  if (!to || !content) {
    ElMessage.warning('请填写收件人和内容')
    return
  }
  if (!userName.value) {
    ElMessage.warning('请先登录')
    return
  }
  sending.value = true
  try {
    sendMessage(userName.value, nickName.value, to, content)
    draft.value = ''
    ElMessage.success('消息已发送')
    refresh()
  } finally {
    sending.value = false
  }
}

function handleOpinion(opinion: Opinion): void {
  if (opinion.status === 0) {
    markOpinionHandled(opinion.id)
    refresh()
  }
}

function formatTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function kindLabel(kind: InboxMessage['kind']): string {
  return { system: '系统', chat: '私信', opinion: '意见' }[kind]
}
</script>

<template>
  <div class="message-center" @keydown.esc="open = false">
    <button
      type="button"
      class="mail-trigger"
      :aria-expanded="open"
      aria-label="消息通知"
      :title="admin ? '消息通知 · 管理员' : '消息通知'"
      @click="toggle"
    >
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
        <path
          d="M3 6.5A2.5 2.5 0 0 1 5.5 4h13A2.5 2.5 0 0 1 21 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-11Z"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linejoin="round"
        />
        <path
          d="m3.6 6.2 8.4 6.4 8.4-6.4"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <span v-if="unread > 0" class="mail-badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <div v-if="open" class="mail-panel" role="dialog" aria-label="消息中心">
      <header class="mail-head">
        <div class="mail-tabs">
          <button
            type="button"
            :class="{ active: tab === 'inbox' }"
            @click="tab = 'inbox'"
          >
            通知 <span v-if="unread" class="tab-dot"></span>
          </button>
          <button
            v-if="admin"
            type="button"
            :class="{ active: tab === 'admin' }"
            @click="tab = 'admin'"
          >
            意见管理
          </button>
        </div>
        <button type="button" class="mail-close" aria-label="关闭" @click="open = false">×</button>
      </header>

      <!-- 通知 / 发送 -->
      <template v-if="tab === 'inbox'">
        <div class="mail-list">
          <button
            v-for="message in messages"
            :key="message.id"
            type="button"
            class="mail-item"
            :class="{ unread: !message.read }"
            @click="openMessage(message)"
          >
            <span class="mail-item-top">
              <span class="mail-kind" :class="`kind-${message.kind}`">{{ kindLabel(message.kind) }}</span>
              <b>{{ message.title }}</b>
              <time>{{ formatTime(message.createdAt) }}</time>
            </span>
            <span class="mail-item-body">{{ message.content }}</span>
            <span class="mail-item-from">来自 {{ message.fromName }}</span>
          </button>
          <p v-if="messages.length === 0" class="mail-empty">暂无消息</p>
        </div>

        <div class="mail-all-read">
          <button type="button" class="link-btn" :disabled="unread === 0" @click="readAll">
            全部标为已读
          </button>
        </div>

        <form class="mail-compose" @submit.prevent="handleSend">
          <p class="compose-title">发送消息</p>
          <input v-model="recipient" type="text" placeholder="收件人用户名" maxlength="30" autocomplete="off" />
          <textarea v-model="draft" placeholder="写点什么…" maxlength="300" rows="2"></textarea>
          <button type="submit" class="compose-send" :disabled="sending">发送</button>
        </form>
      </template>

      <!-- 管理员：意见管理 -->
      <template v-else>
        <div class="mail-list">
          <button
            v-for="opinion in opinions"
            :key="opinion.id"
            type="button"
            class="mail-item"
            :class="{ unread: opinion.status === 0 }"
            @click="handleOpinion(opinion)"
          >
            <span class="mail-item-top">
              <b>{{ opinion.nickName }}</b>
              <span class="mail-kind" :class="opinion.status === 1 ? 'kind-system' : 'kind-opinion'">
                {{ opinion.status === 1 ? '已查看' : '待查看' }}
              </span>
              <time>{{ formatTime(opinion.createdAt) }}</time>
            </span>
            <span class="mail-item-body">{{ opinion.content }}</span>
            <span class="mail-item-from">@{{ opinion.userName }}</span>
          </button>
          <p v-if="opinions.length === 0" class="mail-empty">暂无用户意见</p>
        </div>
        <p class="admin-tip">管理员 {{ nickName || userName }} 可查看全部用户意见（含其他浏览器提交）。</p>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.message-center {
  position: relative;
}
.mail-trigger {
  position: relative;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--muted);
  cursor: pointer;
  transition: color 0.2s var(--ease), border-color 0.2s var(--ease);
  &:hover {
    color: var(--color-primary);
    border-color: var(--color-primary);
  }
}
.mail-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
  border: 2px solid var(--surface-3);
}
.mail-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 100;
  width: min(360px, 90vw);
  background: var(--surface-3);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: var(--shadow-2);
  overflow: hidden;
}
.mail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
}
.mail-tabs {
  display: flex;
  gap: 16px;
  button {
    border: 0;
    background: transparent;
    color: var(--muted);
    font: inherit;
    font-size: 14px;
    padding: 12px 0;
    cursor: pointer;
    border-bottom: 2px solid transparent;
    &.active {
      color: var(--color-primary);
      border-bottom-color: var(--color-primary);
    }
  }
}
.tab-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  vertical-align: middle;
}
.mail-close {
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 22px;
  cursor: pointer;
}
.mail-list {
  max-height: 320px;
  overflow-y: auto;
}
.mail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 12px 14px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
  &:hover {
    background: var(--surface-2);
  }
  &.unread {
    background: var(--accent-soft);
  }
}
.mail-item-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  b {
    font-size: 14px;
  }
  time {
    margin-left: auto;
    font-size: 11px;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
  }
}
.mail-kind {
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 999px;
  border: 1px solid var(--line);
  color: var(--muted);
  flex: none;
}
.kind-system {
  color: #6fa8dc;
  border-color: currentColor;
}
.kind-chat {
  color: #4f9d69;
  border-color: currentColor;
}
.kind-opinion {
  color: #e0a53a;
  border-color: currentColor;
}
.mail-item-body {
  font-size: 13px;
  color: var(--muted);
  line-height: 1.5;
}
.mail-item-from {
  font-size: 11px;
  color: var(--muted);
}
.mail-empty {
  margin: 0;
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}
.mail-all-read {
  display: flex;
  justify-content: flex-end;
  padding: 8px 14px;
  border-bottom: 1px solid var(--line);
}
.link-btn {
  border: 0;
  background: transparent;
  color: var(--color-primary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  &:disabled {
    color: var(--muted);
    cursor: default;
  }
}
.mail-compose {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 14px;
  background: var(--surface-2);
}
.compose-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
.mail-compose input,
.mail-compose textarea {
  width: 100%;
  box-sizing: border-box;
  background: var(--input-bg);
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  padding: 8px 10px;
  resize: vertical;
  &::placeholder {
    color: var(--muted);
  }
}
.compose-send {
  align-self: flex-end;
  border: 0;
  border-radius: 8px;
  background: var(--color-primary);
  color: #fff;
  font: inherit;
  font-size: 13px;
  padding: 6px 18px;
  cursor: pointer;
  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}
.admin-tip {
  margin: 0;
  padding: 10px 14px;
  font-size: 11px;
  color: var(--muted);
  border-top: 1px solid var(--line);
}
</style>
