<script setup lang="ts">
import type { Zone } from '@/constants/catalog'
import { gamesInZone } from '@/constants/catalog'
import { ZONE_SEALS } from '@/constants/zoneSeals'

defineProps<{ zones: Zone[] }>()
defineEmits<{ select: [zoneKey: string] }>()

/** 状态列：只报本区收录了几款游戏，可玩与否由卡片上的徽章表达 */
function zoneStatus(zone: Zone): string {
  const count = gamesInZone(zone.key).length
  return count > 0 ? `${count}款` : '规划中'
}
</script>

<template>
  <section class="zone-table rise" style="--i: 2">
    <div class="zone-table-head" aria-hidden="true">
      <span>分区</span>
      <span>定位</span>
      <span>状态统一</span>
    </div>
    <button
      v-for="(zone, i) in zones"
      :key="zone.key"
      type="button"
      class="zone-card rise"
      :style="{ '--i': i + 3 }"
      @click="$emit('select', zone.key)"
    >
      <span class="zone-name">
        <span class="zone-seal">{{ ZONE_SEALS[zone.key] ?? '桌' }}</span>
        {{ zone.name }}
      </span>
      <span class="zone-tags">
        <span v-for="m in zone.mechanisms" :key="m" class="zone-tag">{{ m }}</span>
      </span>
      <span class="zone-status">{{ zoneStatus(zone) }}</span>
    </button>
  </section>
</template>

<style scoped lang="scss">
.zone-table {
  display: flex;
  flex-direction: column;
  max-width: 860px;
  margin: 0 auto clamp(48px, 6vw, 80px);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--surface-2);
  box-shadow: var(--shadow-1);
}
.zone-table-head,
.zone-card {
  display: grid;
  grid-template-columns: 1fr 1.7fr 1fr;
  align-items: center;
  gap: 16px;
  padding: 14px clamp(16px, 3vw, 28px);
  text-align: left;
}
.zone-table-head {
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.06em;
  background: var(--surface-3);
  border-bottom: 1px solid var(--line);
}
.zone-card {
  width: 100%;
  font: inherit;
  color: var(--ink);
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  cursor: pointer;
  transition: background 0.2s var(--ease);
  &:last-child {
    border-bottom: 0;
  }
  &:hover {
    background: var(--accent-soft);
  }
  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: -2px;
  }
}
.zone-name {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-display);
  font-size: 20px;
  line-height: 1.2;
}
.zone-seal {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: var(--radius);
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 20px;
  background: var(--accent-soft);
  color: var(--color-primary);
  border: 1px solid var(--accent-seal);
}
.zone-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.zone-tag {
  font-size: 12px;
  color: var(--muted);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 3px 12px;
}
.zone-status {
  font-size: 13px;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 720px) {
  .zone-table-head {
    display: none;
  }
  .zone-card {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.rise {
  animation: rise 0.5s var(--ease) both;
  animation-delay: calc(var(--i, 0) * 55ms);
}
@media (prefers-reduced-motion: reduce) {
  .rise {
    animation: none;
  }
  .zone-card {
    transition: none;
  }
}
</style>
