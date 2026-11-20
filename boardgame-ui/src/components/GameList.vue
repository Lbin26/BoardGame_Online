<script setup lang="ts">
import type { Game, Zone } from '@/constants/catalog'

defineProps<{ zone: Zone; games: Game[] }>()
defineEmits<{ select: [gameKey: string] }>()
</script>

<template>
  <div class="game-list">
    <div class="zone-header">
      <h2 class="zone-title">{{ zone.icon }} {{ zone.name }}</h2>
      <div class="zone-tags">
        <span v-for="m in zone.mechanisms" :key="m" class="zone-tag">{{ m }}</span>
      </div>
    </div>

    <div v-if="games.length > 0" class="entries">
      <div
        v-for="game in games"
        :key="game.key"
        class="game-entry"
        :class="{ 'is-disabled': !game.playable }"
        @click="game.playable && $emit('select', game.key)"
      >
        <div class="entry-head">
          <span class="entry-name">{{ game.name }}</span>
          <span v-if="game.players" class="entry-players">{{ game.players }} 人</span>
          <span class="entry-badge" :class="game.playable ? 'badge-playable' : 'badge-soon'">
            {{ game.playable ? '可玩' : '敬请期待' }}
          </span>
        </div>
        <p class="entry-desc">{{ game.desc }}</p>
        <div v-if="game.mechanisms" class="entry-tags">
          <span v-for="m in game.mechanisms" :key="m" class="game-tag">{{ m }}</span>
        </div>
      </div>
    </div>

    <div v-else class="zone-empty">
      该分区游戏筹备中，敬请期待
      <div class="zone-tags">
        <span v-for="m in zone.mechanisms" :key="m" class="zone-tag">{{ m }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.zone-header {
  margin-bottom: 12px;
}

.zone-title {
  margin: 0 0 8px;
  font-size: 18px;
  color: #eee;
}

.zone-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.zone-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #aaa;
}

.entries {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.game-entry {
  padding: 14px;
  background: #16213e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s ease;

  &:hover:not(.is-disabled) {
    border-color: var(--color-primary);
  }
}

.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.entry-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.entry-name {
  font-size: 16px;
  font-weight: 600;
  color: #eee;
}

.entry-players {
  font-size: 12px;
  color: #888;
}

.entry-badge {
  margin-left: auto;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
}

.badge-playable {
  background: rgba(46, 213, 115, 0.2);
  color: #2ed573;
}

.badge-soon {
  background: rgba(136, 136, 136, 0.2);
  color: #888;
}

.entry-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #aaa;
}

.entry-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.game-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(233, 69, 96, 0.12);
  color: #f4a2b0;
}

.zone-empty {
  padding: 32px;
  text-align: center;
  color: #666;
  background: #16213e;
  border-radius: 8px;

  .zone-tags {
    justify-content: center;
    margin-top: 12px;
  }
}
</style>
