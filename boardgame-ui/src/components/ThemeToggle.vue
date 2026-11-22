<script setup lang="ts">
import { computed, ref } from 'vue'

import { applyTheme, readTheme, type ThemeMode } from '@/composables/useTheme'

/** 初始值与 index.html 的内联脚本保持一致（默认 dark） */
const mode = ref<ThemeMode>(readTheme())

const isDark = computed(() => mode.value === 'dark')
const label = computed(() => (isDark.value ? '切换到白天模式' : '切换到黑夜模式'))

function toggle(): void {
  mode.value = isDark.value ? 'light' : 'dark'
  applyTheme(mode.value)
}
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :title="label"
    :aria-label="label"
    @click="toggle"
  >
    <span class="tt-glyph">{{ isDark ? '☀' : '☾' }}</span>
  </button>
</template>

<style scoped lang="scss">
.theme-toggle {
  position: fixed;
  right: 20px;
  top: 18px;
  z-index: 60;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: var(--panel);
  color: var(--ink);
  box-shadow: var(--shadow-1);
  cursor: pointer;
  transition:
    transform 0.2s var(--ease),
    border-color 0.2s,
    background 0.2s;

  &:hover {
    transform: translateY(-2px) rotate(-12deg);
    border-color: var(--accent-line);
  }
  &:active {
    transform: translateY(0) scale(0.94);
  }
}
.tt-glyph {
  font-size: 19px;
  line-height: 1;
}
@media (prefers-reduced-motion: reduce) {
  .theme-toggle {
    transition: none;
  }
}
</style>
