<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { runSim, runSweep, validateGame, type RunResponse, type SampleEntry, type SweepResponse } from '@/api/design'
import { PRESETS } from './presets'

const props = defineProps<{ samples: SampleEntry[] }>()

const presetKey = ref('')
const gameText = ref('')
const configText = ref('')
const busy = ref(false)

const validateResult = ref<{ ok: boolean; errors: string[] } | null>(null)
const runResult = ref<RunResponse | null>(null)
const sweepResult = ref<SweepResponse | null>(null)

const selectedPreset = computed(() => PRESETS.find((p) => p.key === presetKey.value))

watch(presetKey, (key) => {
  const preset = PRESETS.find((p) => p.key === key)
  if (!preset) return
  const sample = props.samples.find((s) => s.key === preset.sampleKey)
  gameText.value = sample?.text ?? ''
  configText.value = preset.configText
})

/** 结果标量（number/string/boolean）→ | 指标 | 值 | 表格；复杂值留给 md 的 yaml 块 */
const runScalars = computed(() => {
  if (!runResult.value) return []
  return Object.entries(runResult.value.result).filter(
    ([, v]) => typeof v === 'number' || typeof v === 'string' || typeof v === 'boolean',
  )
})

function errMsg(e: unknown): string {
  const resp = (e as { response?: { data?: { error?: string } } })?.response
  return resp?.data?.error ?? (e as Error).message
}

/** 浏览器端下载（服务端不落盘）：Blob + 临时 <a download> */
function download(content: string, filename: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function doValidate() {
  busy.value = true
  try {
    validateResult.value = await validateGame(gameText.value)
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    busy.value = false
  }
}

async function doRun() {
  busy.value = true
  try {
    sweepResult.value = null
    runResult.value = await runSim({ gameText: gameText.value, configText: configText.value })
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    busy.value = false
  }
}

async function doSweep() {
  busy.value = true
  try {
    runResult.value = null
    sweepResult.value = await runSweep({ gameText: gameText.value, configText: configText.value })
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="workbench-panel">
    <div class="preset-row">
      <el-select v-model="presetKey" placeholder="选择预置（样本 + 模拟配置）" clearable style="width: 320px">
        <el-option v-for="p in PRESETS" :key="p.key" :label="p.label" :value="p.key" />
      </el-select>
      <span v-if="selectedPreset?.demo" class="demo-warn">演示数据，非样本数值——不构成样本平衡证据</span>
    </div>

    <div class="editors">
      <label class="editor">
        <span class="editor-label">game.yaml（游戏文档）</span>
        <textarea v-model="gameText" class="yaml-area" rows="14" spellcheck="false" />
      </label>
      <label class="editor">
        <span class="editor-label">sim 配置（yaml）</span>
        <textarea v-model="configText" class="yaml-area" rows="14" spellcheck="false" />
      </label>
    </div>

    <div class="actions">
      <button class="btn btn-ghost" :disabled="busy" @click="doValidate">校验文档</button>
      <button class="btn btn-primary" :disabled="busy" @click="doRun">运行模拟</button>
      <button class="btn btn-ghost" :disabled="busy" @click="doSweep">参数扫描</button>
    </div>

    <div v-if="validateResult" class="validate-result">
      <p v-if="validateResult.ok" class="ok">校验通过</p>
      <ul v-else class="errors">
        <li v-for="(e, i) in validateResult.errors" :key="i">{{ e }}</li>
      </ul>
    </div>

    <div v-if="runResult" class="run-result">
      <table class="result-table">
        <thead>
          <tr><th>指标</th><th>值</th></tr>
        </thead>
        <tbody>
          <tr v-for="[k, v] in runScalars" :key="k"><td>{{ k }}</td><td>{{ v }}</td></tr>
        </tbody>
      </table>
      <pre class="report-md">{{ runResult.md }}</pre>
      <div class="downloads">
        <button class="btn btn-ghost btn-sm" @click="download(runResult!.md, 'design-report.md', 'text/markdown;charset=utf-8')">下载 md</button>
        <button class="btn btn-ghost btn-sm" @click="download(runResult!.json, 'design-report.json', 'application/json;charset=utf-8')">下载 json</button>
      </div>
    </div>

    <div v-if="sweepResult" class="sweep-result">
      <pre class="report-md">{{ sweepResult.md }}</pre>
      <div class="downloads">
        <button class="btn btn-ghost btn-sm" @click="download(sweepResult!.md, 'design-sweep.md', 'text/markdown;charset=utf-8')">下载 md</button>
        <button class="btn btn-ghost btn-sm" @click="download(sweepResult!.json, 'design-sweep.json', 'application/json;charset=utf-8')">下载 json</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.preset-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.demo-warn {
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fcd34d;
  border-radius: var(--radius);
  padding: 4px 12px;
  font-size: 13px;
}
.editors {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.editor-label {
  display: block;
  color: var(--muted);
  font-size: 12px;
  margin-bottom: 6px;
}
.yaml-area {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 12px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink);
  resize: vertical;
}
.yaml-area:focus {
  outline: 2px solid var(--color-primary);
  outline-offset: -1px;
}
.actions {
  display: flex;
  gap: 10px;
  margin: 14px 0;
}
.btn {
  border: 1px solid transparent;
  border-radius: var(--radius);
  padding: 8px 18px;
  font-size: 14px;
  cursor: pointer;
}
.btn-primary {
  background: var(--color-primary);
  color: #fff;
}
.btn-ghost {
  border-color: var(--line);
  color: var(--ink);
  background: #fff;
}
.btn-ghost:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-sm {
  padding: 4px 12px;
  font-size: 13px;
}
.validate-result .ok {
  color: #15803d;
}
.validate-result .errors {
  color: #b91c1c;
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}
.result-table {
  border-collapse: collapse;
  margin-bottom: 12px;
}
.result-table th,
.result-table td {
  border: 1px solid var(--line);
  padding: 5px 14px;
  font-size: 13px;
  text-align: left;
}
.result-table th {
  color: var(--muted);
  font-weight: 600;
}
.report-md {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fafaf9;
  padding: 12px 14px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 420px;
  overflow: auto;
}
.downloads {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
</style>