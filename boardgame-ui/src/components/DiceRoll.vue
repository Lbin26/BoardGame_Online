<script setup lang="ts">
import { ref, watch } from 'vue'

import { useDiceRoll } from '@/composables/useDiceRoll'
import { useGameSfx } from '@/composables/useGameSfx'

/**
 * 骰子翻滚展示组件（服务端权威）：
 * 父组件把 DICE_RESULT 的 value 传给 `value` prop 并把 `trigger` 自增一次，
 * 组件即播放「翻滚 → 定格」动画 + 掷骰音效。翻滚帧全在本地生成，
 * 定格点数恒为服务端 value。
 */
const props = withDefaults(
  defineProps<{
    /** 服务端真实点数 1-6；0 表示未掷（隐藏） */
    value: number
    /** 每次自增触发一次翻滚，避免相同点数连续到达时不动画 */
    trigger: number
    size?: number
  }>(),
  { size: 64 },
)

const { face, rolling, settleVersion, rollTo, stop } = useDiceRoll()
const sfx = useGameSfx()

/** 骰面朝上时的旋转角度：让对应点数那一面正对镜头 */
const FACE_ROTATION: Record<number, string> = {
  1: 'rotateX(0deg) rotateY(0deg)',
  2: 'rotateY(-90deg)',
  3: 'rotateX(-90deg)',
  4: 'rotateX(90deg)',
  5: 'rotateY(90deg)',
  6: 'rotateY(180deg)',
}

const settledOnce = ref(false)

watch(
  () => props.trigger,
  (next, prev) => {
    if (next <= (prev ?? 0) || props.value < 1) return
    settledOnce.value = false
    sfx.play('dice')
    rollTo(props.value)
  },
)

watch(settleVersion, () => {
  settledOnce.value = true
})

defineExpose({ stop })
</script>

<template>
  <div class="dice-roll" :style="{ width: `${size}px`, height: `${size}px`, '--d': `${size / 2}px` }"
       role="img" :aria-label="`骰子点数 ${face}`">
    <div class="dice-scene" :class="{ rolling }">
      <div class="dice-cube" :style="{ transform: FACE_ROTATION[face] }">
        <div v-for="n in 6" :key="n" class="dice-face" :class="`face-${n}`">
          <span class="pips" :class="`pips-${n}`">
            <i v-for="p in n" :key="p" class="pip"></i>
          </span>
        </div>
      </div>
    </div>
    <Transition name="settle-pop">
      <span v-if="settledOnce && !rolling" class="dice-result">{{ face }}</span>
    </Transition>
  </div>
</template>

<style scoped lang="scss">
.dice-roll {
  position: relative;
  display: inline-grid;
  place-items: center;
  flex: none;
}
.dice-scene {
  width: 100%;
  height: 100%;
  perspective: 520px;
}
.dice-cube {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transition: transform 0.28s cubic-bezier(0.2, 0.8, 0.2, 1);
}
.dice-scene.rolling .dice-cube {
  animation: dice-tumble 0.24s linear infinite;
  transition: none;
}
@keyframes dice-tumble {
  from { transform: rotateX(0deg) rotateY(0deg); }
  to   { transform: rotateX(360deg) rotateY(360deg); }
}
.dice-face {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: 14%;
  background: var(--surface-1, #fff);
  border: 1px solid var(--line, rgba(0, 0, 0, 0.15));
  backface-visibility: hidden;
}
.face-1 { transform: translateZ(calc(var(--d, 32px))); }
.face-2 { transform: rotateY(90deg) translateZ(calc(var(--d, 32px))); }
.face-3 { transform: rotateX(90deg) translateZ(calc(var(--d, 32px))); }
.face-4 { transform: rotateX(-90deg) translateZ(calc(var(--d, 32px))); }
.face-5 { transform: rotateY(-90deg) translateZ(calc(var(--d, 32px))); }
.face-6 { transform: rotateY(180deg) translateZ(calc(var(--d, 32px))); }

.pips {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  gap: 8%;
  width: 62%;
  height: 62%;
}
.pip {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--color-primary, #333);
}
.pips-1 { .pip { grid-area: 2 / 2; } }
.pips-2 { .pip:nth-child(1) { grid-area: 1 / 1; } .pip:nth-child(2) { grid-area: 3 / 3; } }
.pips-3 {
  .pip:nth-child(1) { grid-area: 1 / 1; }
  .pip:nth-child(2) { grid-area: 2 / 2; }
  .pip:nth-child(3) { grid-area: 3 / 3; }
}
.pips-4 {
  .pip:nth-child(1) { grid-area: 1 / 1; }
  .pip:nth-child(2) { grid-area: 1 / 3; }
  .pip:nth-child(3) { grid-area: 3 / 1; }
  .pip:nth-child(4) { grid-area: 3 / 3; }
}
.pips-5 {
  .pip:nth-child(1) { grid-area: 1 / 1; }
  .pip:nth-child(2) { grid-area: 1 / 3; }
  .pip:nth-child(3) { grid-area: 2 / 2; }
  .pip:nth-child(4) { grid-area: 3 / 1; }
  .pip:nth-child(5) { grid-area: 3 / 3; }
}
.pips-6 {
  .pip:nth-child(1) { grid-area: 1 / 1; }
  .pip:nth-child(2) { grid-area: 1 / 3; }
  .pip:nth-child(3) { grid-area: 2 / 1; }
  .pip:nth-child(4) { grid-area: 2 / 3; }
  .pip:nth-child(5) { grid-area: 3 / 1; }
  .pip:nth-child(6) { grid-area: 3 / 3; }
}

.dice-result {
  position: absolute;
  right: -8px;
  bottom: -8px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  background: var(--color-primary);
  color: #fff;
  font: 700 12px/20px ui-monospace, monospace;
  text-align: center;
}
.settle-pop-enter-active { animation: settle-pop 0.28s ease-out; }
@keyframes settle-pop {
  0% { transform: scale(0.4); opacity: 0; }
  60% { transform: scale(1.25); }
  100% { transform: scale(1); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .dice-scene.rolling .dice-cube { animation: none; }
  .settle-pop-enter-active { animation: none; }
}
</style>
