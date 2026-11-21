<script setup lang="ts">
import { ref } from 'vue'

import type { SampleEntry } from '@/api/design'

defineProps<{ samples: SampleEntry[] }>()

const expanded = ref<string | null>(null)

function toggle(key: string) {
  expanded.value = expanded.value === key ? null : key
}

function docOf(s: SampleEntry) {
  return s.doc as {
    meta?: { name?: string; zone?: string }
    mechanisms?: string[]
    analysis?: {
      confidence?: string
      whyFun?: string
      remixable?: string[]
      pitfalls?: string
      sources?: { title: string }[]
    }
  }
}
</script>

<template>
  <div class="samples-panel">
    <div v-for="s in samples" :key="s.key" class="sample-card op rise" @click="toggle(s.key)">
      <div class="sample-head">
        <span class="sample-name">{{ docOf(s).meta?.name ?? s.key }}</span>
        <span class="sample-zone">{{ docOf(s).meta?.zone }}</span>
        <span class="sample-confidence">置信度 {{ docOf(s).analysis?.confidence }}</span>
      </div>
      <div class="sample-tags">
        <span v-for="m in docOf(s).mechanisms ?? []" :key="m" class="tag">{{ m }}</span>
      </div>
      <div v-if="expanded === s.key" class="sample-detail">
        <h4>为什么好玩</h4>
        <p>{{ docOf(s).analysis?.whyFun }}</p>
        <h4>可借鉴点</h4>
        <ul>
          <li v-for="(r, i) in docOf(s).analysis?.remixable ?? []" :key="i">{{ r }}</li>
        </ul>
        <h4>常见坑</h4>
        <p>{{ docOf(s).analysis?.pitfalls }}</p>
        <h4>来源</h4>
        <ul>
          <li v-for="(src, i) in docOf(s).analysis?.sources ?? []" :key="i">{{ src.title }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.samples-panel {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.sample-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 16px;
  cursor: pointer;
  color: var(--ink);
  background: #fff;
}
.sample-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.sample-name {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
}
.sample-zone,
.sample-confidence {
  color: var(--muted);
  font-size: 12px;
}
.sample-tags {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag {
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 1px 9px;
  font-size: 12px;
  color: var(--muted);
}
.sample-detail {
  margin-top: 12px;
  border-top: 1px dashed var(--line);
  padding-top: 10px;
  font-size: 13px;
  line-height: 1.7;
}
.sample-detail h4 {
  margin: 10px 0 4px;
  color: var(--color-primary);
  font-size: 13px;
}
.sample-detail ul {
  margin: 0;
  padding-left: 18px;
}
</style>