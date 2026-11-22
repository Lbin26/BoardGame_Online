<script setup lang="ts">
import type { Zone } from '@/constants/catalog'
import { gamesInZone } from '@/constants/catalog'
import { ZONE_SEALS } from '@/constants/zoneSeals'

defineProps<{ zones: Zone[] }>()
defineEmits<{ select: [zoneKey: string] }>()

/** 分区桌游统计:徽章诚实标注(可玩 / 敬请期待 / 规划中) */
function zoneMeta(zone: Zone): { count: number; live: number } {
  const games = gamesInZone(zone.key)
  return { count: games.length, live: games.filter((g) => g.playable).length }
}
</script>

<template>
  <div class="zone-grid">
    <button
      v-for="(zone, i) in zones"
      :key="zone.key"
      type="button"
      class="zone-card rise"
      :class="{ 'empty-zone': zoneMeta(zone).count === 0 }"
      :style="{ '--i': i + 2 }"
      @click="$emit('select', zone.key)"
    >
      <span class="zone-seal">{{ ZONE_SEALS[zone.key] ?? '桌' }}</span>
      <span class="zone-name">{{ zone.name }}</span>
      <span class="zone-tags">
        <span v-for="m in zone.mechanisms" :key="m" class="zone-tag">{{ m }}</span>
      </span>
      <span class="zone-foot">
        {{ zoneMeta(zone).count ? `${zoneMeta(zone).count} 款桌游` : '暂无桌游' }}
        <span class="zone-badge" :class="zoneMeta(zone).live ? 'badge-live' : 'badge-soon'">
          {{
            zoneMeta(zone).live
              ? `${zoneMeta(zone).live} 款可玩`
              : zoneMeta(zone).count
                ? '敬请期待'
                : '规划中'
          }}
        </span>
        <span class="zone-arrow">→</span>
      </span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.zone-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding-bottom: clamp(48px, 6vw, 80px);
}
@media (max-width: 980px) {
  .zone-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 560px) {
  .zone-grid {
    grid-template-columns: 1fr;
  }
}

.zone-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
  font: inherit;
  color: var(--ink);
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 20px;
  cursor: pointer;
  transition:
    border-color 0.25s var(--ease),
    transform 0.25s var(--ease),
    box-shadow 0.25s var(--ease);

  &:hover {
    border-color: var(--accent-hover);
    transform: translateY(-3px);
    box-shadow: var(--shadow-2);
  }
  &:active {
    transform: translateY(-1px);
  }
  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
  }
}

.zone-seal {
  width: 44px;
  height: 44px;
  border-radius: var(--radius);
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 24px;
  background: var(--accent-soft);
  color: var(--color-primary);
  border: 1px solid var(--accent-seal);
}
.empty-zone .zone-seal {
  background: transparent;
  color: var(--muted);
  border: 1px dashed var(--muted);
}

.zone-name {
  display: block;
  margin: 0;
  font-family: var(--font-display);
  font-weight: 400;
  font-size: 21px;
  line-height: 1.2;
}

.zone-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.zone-tag {
  font-size: 11px;
  color: var(--muted);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 9px;
}

.zone-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--muted);
  padding-top: 6px;
}
.zone-badge {
  font-size: 11px;
  letter-spacing: 0.08em;
  padding: 2px 9px;
  border-radius: 999px;
}
.badge-live {
  background: var(--accent-soft);
  color: var(--color-primary);
  border: 1px solid var(--accent-line);
}
.badge-soon {
  color: var(--muted);
  border: 1px dashed var(--muted);
}
.zone-arrow {
  margin-left: auto;
  color: var(--muted);
  transition:
    transform 0.25s var(--ease),
    color 0.25s var(--ease);
}
.zone-card:hover .zone-arrow {
  transform: translateX(4px);
  color: var(--color-primary);
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
  .zone-card,
  .zone-arrow {
    transition: none;
  }
}
</style>