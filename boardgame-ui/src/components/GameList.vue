<script setup lang="ts">
import type { Game, Zone } from '@/constants/catalog'
import { POSTER_GAME_KEYS } from '@/constants/catalog'
import { ZONE_SEALS } from '@/constants/zoneSeals'
import { findRuleBook } from '@/constants/rules'

defineProps<{ zone: Zone; games: Game[] }>()
defineEmits<{ select: [gameKey: string]; rules: [gameKey: string] }>()

/** 海报剪影：文件名直接用 game.key，未登记的游戏不铺背景 */
function entryStyle(game: Game, i: number): Record<string, string | number> {
  return POSTER_GAME_KEYS.has(game.key)
    ? { '--i': i + 1, '--poster': `url('/posters/${game.key}.webp')` }
    : { '--i': i + 1 }
}
</script>

<template>
  <div class="game-list">
    <div class="zone-header rise" style="--i: 0">
      <span class="zone-seal">{{ ZONE_SEALS[zone.key] ?? '桌' }}</span>
      <div class="zone-head-text">
        <h2 class="zone-title">{{ zone.name }}</h2>
        <div class="zone-tags">
          <span v-for="m in zone.mechanisms" :key="m" class="zone-tag">{{ m }}</span>
        </div>
      </div>
    </div>

    <div v-if="games.length > 0" class="entries">
      <component
        :is="game.playable ? 'button' : 'div'"
        v-for="(game, i) in games"
        :key="game.key"
        :type="game.playable ? 'button' : undefined"
        class="game-entry rise"
        :class="{ 'is-disabled': !game.playable, 'has-poster': POSTER_GAME_KEYS.has(game.key) }"
        :style="entryStyle(game, i)"
        @click="game.playable && $emit('select', game.key)"
      >
        <span class="g-name">{{ game.name }}</span>
        <span class="g-desc">{{ game.desc }}</span>
        <span class="g-meta">
          <span v-if="game.players" class="g-players">{{ game.players }} 人</span>
          <span class="entry-badge" :class="game.playable ? 'badge-playable' : 'badge-soon'">
            {{ game.playable ? '可玩' : '敬请期待' }}
          </span>
          <span
            v-if="findRuleBook(game.key)"
            class="rules-link"
            role="button"
            tabindex="0"
            @click.stop="$emit('rules', game.key)"
            @keydown.enter.stop.prevent="$emit('rules', game.key)"
          >
            📖 规则
          </span>
          <span v-if="game.playable" class="go">进入房间大厅 ➜</span>
        </span>
        <span v-if="game.mechanisms" class="entry-tags">
          <span v-for="m in game.mechanisms" :key="m" class="game-tag">{{ m }}</span>
        </span>
      </component>
    </div>

    <div v-else class="zone-empty rise">
      该分区游戏筹备中，敬请期待
      <div class="zone-tags">
        <span v-for="m in zone.mechanisms" :key="m" class="zone-tag">{{ m }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.zone-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: clamp(28px, 4vw, 44px) 0 24px;
}
.zone-seal {
  width: 52px;
  height: 52px;
  border-radius: var(--radius);
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 28px;
  background: var(--accent-soft);
  color: var(--color-primary);
  border: 1px solid var(--accent-seal);
  flex: none;
}
.zone-title {
  margin: 0;
  font-family: var(--font-display);
  font-weight: 400;
  font-size: clamp(28px, 3.4vw, 40px);
  line-height: 1.2;
  color: var(--ink);
}
.zone-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.zone-tag {
  font-size: 11px;
  color: var(--muted);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 9px;
}

.entries {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: clamp(48px, 6vw, 80px);
}
.game-entry {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  text-align: left;
  font: inherit;
  color: var(--ink);
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 18px 20px;
  transition: border-color 0.25s var(--ease);

  /* 彩色海报剪影：贴左侧铺开，双层遮罩让右侧彻底渐隐到卡片底色 */
  &.has-poster::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: var(--poster);
    background-repeat: no-repeat;
    background-position: left center;
    background-size: auto 155%;
    filter: saturate(1.15) brightness(0.78) contrast(1.05);
    opacity: 0.62;
    -webkit-mask-image: linear-gradient(90deg, #000 0%, rgba(0, 0, 0, 0.5) 38%, transparent 70%);
    mask-image: linear-gradient(90deg, #000 0%, rgba(0, 0, 0, 0.5) 38%, transparent 70%);
    pointer-events: none;
    transition: opacity 0.25s var(--ease);
  }
  &.has-poster::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(90deg, transparent 12%, var(--surface-1) 72%);
    pointer-events: none;
  }
  &.has-poster > * {
    position: relative;
    z-index: 1;
  }
  /* 海报剪影上的小标签改为实底白字：半透明描边在深色背景上会糊掉 */
  &.has-poster .game-tag,
  &.has-poster .badge-soon,
  &.has-poster .rules-link {
    color: #fff;
    background: rgba(16, 22, 34, 0.82);
    border-color: rgba(255, 255, 255, 0.5);
  }
  &.has-poster .badge-playable {
    color: #fff;
    background: var(--color-primary);
    border-color: var(--color-primary);
  }
  &.has-poster:hover:not(.is-disabled)::before {
    opacity: 0.72;
  }
  &.has-poster.is-disabled::before {
    opacity: 0.22;
  }

  &:hover:not(.is-disabled) {
    border-color: var(--accent-hover);
  }
  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
  }
}
.is-disabled {
  cursor: not-allowed;
  opacity: 0.68;
}
button.game-entry {
  cursor: pointer;
}
.g-name {
  font-family: var(--font-display);
  font-size: 22px;
  min-width: 9em;
}
.g-desc {
  color: var(--muted);
  font-size: 14px;
  flex: 1;
}
.g-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--muted);
}
.g-players {
  font-size: 12px;
}
.entry-badge {
  font-size: 11px;
  letter-spacing: 0.08em;
  padding: 2px 9px;
  border-radius: 999px;
}
.badge-playable {
  background: var(--accent-soft);
  color: var(--color-primary);
  border: 1px solid var(--accent-line);
}
.badge-soon {
  color: var(--muted);
  border: 1px dashed var(--muted);
}
.rules-link {
  font-size: 12px;
  color: var(--muted);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 9px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s;

  &:hover,
  &:focus-visible {
    border-color: var(--accent-hover);
    color: var(--color-primary);
    outline: none;
  }
}
.go {
  color: var(--color-primary);
  font-size: 14px;
}
.entry-tags {
  flex-basis: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.game-tag {
  font-size: 11px;
  color: var(--muted);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 9px;
}
@media (max-width: 700px) {
  .g-name {
    min-width: 0;
  }
}

.zone-empty {
  padding: 48px 20px;
  text-align: center;
  color: var(--muted);
  background: var(--surface-4);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
  font-family: var(--font-display);
  font-size: 24px;

  .zone-tags {
    justify-content: center;
    margin-top: 0;
    font-family: var(--font-body);
    font-size: 11px;
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
  .game-entry {
    transition: none;
  }
}
</style>