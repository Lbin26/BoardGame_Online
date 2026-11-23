<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { sendOpinion } from '@/api/inbox'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const userName = computed(() => userStore.userInfo?.userName ?? '')
const nickName = computed(() => userStore.userInfo?.nickName ?? '')

const open = ref(false)
const content = ref('')
const submitting = ref(false)

async function submit(): Promise<void> {
  const text = content.value.trim()
  if (!text) {
    ElMessage.warning('请先写下你的意见')
    return
  }
  if (!userName.value) {
    ElMessage.warning('请先登录再提交意见')
    return
  }
  submitting.value = true
  try {
    sendOpinion(userName.value, nickName.value, text)
    content.value = ''
    open.value = false
    ElMessage.success('感谢反馈，管理员会尽快查看')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="feedback-fab" @keydown.esc="open = false">
    <transition name="fab">
      <form v-if="open" class="feedback-card" @submit.prevent="submit">
        <header class="feedback-head">
          <span class="feedback-title">发送意见</span>
          <button type="button" class="feedback-close" aria-label="收起" @click="open = false">×</button>
        </header>
        <p class="feedback-desc">告诉我们你想改进的地方，平台超级管理员会看到每一条意见。</p>
        <textarea
          v-model="content"
          placeholder="例如：希望增加某个桌游、优化房间匹配…"
          maxlength="300"
          rows="3"
          autofocus
        ></textarea>
        <div class="feedback-foot">
          <span class="feedback-count">{{ content.length }}/300</span>
          <button type="submit" class="feedback-submit" :disabled="submitting">提交意见</button>
        </div>
      </form>
    </transition>

    <button
      type="button"
      class="feedback-trigger"
      :aria-expanded="open"
      :title="open ? '收起意见窗口' : '发送意见'"
      @click="open = !open"
    >
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
        <path
          d="M20 15a3 3 0 0 1-3 3H8l-4 3V6a3 3 0 0 1 3-3h10a3 3 0 0 1 3 3v9Z"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linejoin="round"
        />
        <path d="M8 9h8M8 13h5" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </svg>
      <span>发送意见</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.feedback-fab {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 45;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.feedback-card {
  width: min(320px, calc(100vw - 40px));
  box-sizing: border-box;
  background: var(--surface-3);
  border: 1px solid var(--accent-line, var(--line));
  border-radius: 12px;
  box-shadow: var(--shadow-2);
  padding: 14px 16px;
}
.feedback-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.feedback-title {
  font-family: var(--font-display);
  font-size: 18px;
}
.feedback-close {
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 22px;
  cursor: pointer;
}
.feedback-desc {
  margin: 6px 0 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}
.feedback-card textarea {
  width: 100%;
  box-sizing: border-box;
  background: var(--input-bg);
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  padding: 10px;
  resize: vertical;
  &::placeholder {
    color: var(--muted);
  }
  &:focus {
    border-color: var(--color-primary);
    outline: none;
  }
}
.feedback-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.feedback-count {
  font-size: 11px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.feedback-submit {
  border: 0;
  border-radius: 8px;
  background: var(--color-primary);
  color: #fff;
  font: inherit;
  font-size: 13px;
  padding: 7px 18px;
  cursor: pointer;
  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}
.feedback-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border: 1px solid var(--color-primary);
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  box-shadow: var(--shadow-1);
  cursor: pointer;
  transition: transform 0.2s var(--ease), box-shadow 0.2s var(--ease);
  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-2);
  }
}
.fab-enter-active,
.fab-leave-active {
  transition: opacity 0.2s var(--ease), transform 0.2s var(--ease);
}
.fab-enter-from,
.fab-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
@media (prefers-reduced-motion: reduce) {
  .feedback-trigger,
  .fab-enter-active,
  .fab-leave-active {
    transition: none;
  }
}
</style>
