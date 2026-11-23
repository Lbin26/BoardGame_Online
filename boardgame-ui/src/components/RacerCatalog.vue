<script setup lang="ts">
import { computed, ref } from 'vue'

import type { RacerInfo } from '@/api/magicalAthlete'
import { RACERS, racerHue, typeLabel } from '@/constants/racers'

/** 图鉴：展示全部选手的能力，规则页与比赛页共用 */
const all = computed<RacerInfo[]>(() => Object.values(RACERS))
const filter = ref('')
const types = computed(() => Array.from(new Set(all.value.map((r) => r.abilityType))))
const shown = computed(() =>
  filter.value ? all.value.filter((r) => r.abilityType === filter.value) : all.value,
)
</script>

<template>
  <div class="racer-catalog">
    <div class="catalog-filter">
      <button
        type="button"
        class="filter-chip"
        :class="{ active: !filter }"
        @click="filter = ''"
      >全部 {{ all.length }}</button>
      <button
        v-for="t in types"
        :key="t"
        type="button"
        class="filter-chip"
        :class="{ active: filter === t }"
        @click="filter = t"
      >{{ typeLabel(t) }}</button>
    </div>

    <div class="catalog-grid">
      <article
        v-for="r in shown"
        :key="r.key"
        class="catalog-card"
        :style="{ '--hue': racerHue(r.key) }"
      >
        <span class="catalog-badge">{{ r.name.slice(0, 1) }}</span>
        <div class="catalog-body">
          <div class="catalog-head">
            <b>{{ r.name }}</b>
            <span class="catalog-type">{{ typeLabel(r.abilityType) }}</span>
          </div>
          <p class="catalog-skill">{{ r.abilitySummary }}</p>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped lang="scss">
.racer-catalog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.catalog-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filter-chip {
  font: inherit;
  font-size: 13px;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--muted);
  cursor: pointer;

  &.active {
    border-color: var(--color-primary);
    color: var(--color-primary);
    background: var(--accent-soft, transparent);
  }
}
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}
.catalog-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-4, transparent);
}
.catalog-badge {
  flex: none;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: hsl(var(--hue), 55%, 45%);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
}
.catalog-body {
  min-width: 0;
}
.catalog-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 15px;
}
.catalog-type {
  font-size: 12px;
  color: var(--color-primary);
}
.catalog-skill {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--muted);
}
</style>
