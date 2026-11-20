<script setup lang="ts">
import { computed, ref } from 'vue'

import type { MechanismDetail } from '@/api/design'

const props = defineProps<{ mechanisms: MechanismDetail[] }>()

const query = ref('')
const expanded = ref<string | null>(null)

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.mechanisms
  return props.mechanisms.filter(
    (m) =>
      m.key.toLowerCase().includes(q) ||
      m.name.toLowerCase().includes(q) ||
      m.nameEn.toLowerCase().includes(q),
  )
})

const ELEMENT_KEYS = ['说明', '代表作', '设计旋钮', '常见坑', '常见组合']

function toggle(key: string) {
  expanded.value = expanded.value === key ? null : key
}
</script>

<template>
  <div class="mechanisms-panel">
    <el-input v-model="query" class="mech-search" placeholder="搜索机制（key / 中文名 / English）" clearable />
    <div class="mech-list">
      <div v-for="m in filtered" :key="m.key" class="mech-item op rise" @click="toggle(m.key)">
        <div class="mech-head">
          <span class="mech-name">{{ m.name }}</span>
          <span class="mech-en">{{ m.nameEn }}</span>
          <span class="mech-zone">{{ m.zone }}</span>
        </div>
        <div v-if="expanded === m.key" class="mech-detail">
          <p v-for="k in ELEMENT_KEYS" :key="k"><b>{{ k }}：</b>{{ m.elements[k] }}</p>
        </div>
      </div>
      <p v-if="filtered.length === 0" class="mech-empty">没有匹配的机制</p>
    </div>
  </div>
</template>

<style scoped>
.mech-search {
  max-width: 420px;
  margin-bottom: 14px;
}
.mech-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}
.mech-item {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px 16px;
  cursor: pointer;
  color: var(--ink);
  background: #fff;
}
.mech-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.mech-name {
  font-weight: 700;
}
.mech-en,
.mech-zone {
  color: var(--muted);
  font-size: 12px;
}
.mech-detail {
  margin-top: 10px;
  border-top: 1px dashed var(--line);
  padding-top: 8px;
  font-size: 13px;
  line-height: 1.7;
}
.mech-detail b {
  color: var(--color-primary);
}
.mech-empty {
  color: var(--muted);
}
</style>