<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getMechanisms, getSamples, type MechanismDetail, type SampleEntry } from '@/api/design'
import SamplesPanel from './SamplesPanel.vue'
import MechanismsPanel from './MechanismsPanel.vue'
import WorkbenchPanel from './WorkbenchPanel.vue'

const samples = ref<SampleEntry[]>([])
const mechanisms = ref<MechanismDetail[]>([])
const tab = ref('samples')

onMounted(async () => {
  try {
    const [s, m] = await Promise.all([getSamples(), getMechanisms()])
    samples.value = s
    mechanisms.value = m
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
})
</script>

<template>
  <div class="design-page">
    <header class="design-head">
      <h1>桌游设计助手</h1>
      <p class="notice">样本 · 机制 · 数值模拟工作台。对话式设计请在 Claude Code 使用 /design-boardgame</p>
    </header>
    <el-tabs v-model="tab">
      <el-tab-pane label="样本库" name="samples">
        <SamplesPanel :samples="samples" />
      </el-tab-pane>
      <el-tab-pane label="机制库" name="mechanisms">
        <MechanismsPanel :mechanisms="mechanisms" />
      </el-tab-pane>
      <el-tab-pane label="模拟工作台" name="workbench">
        <WorkbenchPanel :samples="samples" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.design-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 28px 24px 60px;
  color: var(--ink);
}
.design-head h1 {
  font-family: var(--font-display);
  font-size: 26px;
  margin: 0 0 6px;
}
.notice {
  color: var(--muted);
  font-size: 13px;
  margin: 0 0 18px;
}
</style>