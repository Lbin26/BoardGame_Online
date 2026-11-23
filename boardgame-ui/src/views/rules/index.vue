<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { findRuleBook } from '@/constants/rules'
import { findGame } from '@/constants/catalog'
import RacerCatalog from '@/components/RacerCatalog.vue'
import { RACERS } from '@/constants/racers'
import { goBackOrLobby } from '@/utils/navigation'

const route = useRoute()
const router = useRouter()

const gameKey = computed(() => route.params.gameKey as string)
const ruleBook = computed(() => findRuleBook(gameKey.value))
const game = computed(() => findGame(gameKey.value))
const heading = computed(
  () => ruleBook.value?.title ?? game.value?.name ?? '规则介绍',
)
/** 胡闹运动会：规则末尾追加选手图鉴 */
const showCatalog = computed(() => gameKey.value === 'magicalAthlete')
</script>

<template>
  <div class="rules-page">
    <header class="rules-head">
      <button type="button" class="back-btn" @click="goBackOrLobby(router)">⬅ 返回</button>
      <div class="head-text">
        <h1>{{ heading }}</h1>
        <p v-if="ruleBook" class="subtitle">{{ ruleBook.subtitle }}</p>
      </div>
    </header>

    <div v-if="ruleBook" class="rules-body">
      <nav class="toc" aria-label="规则目录">
        <div class="toc-title">目录</div>
        <a
          v-for="(section, i) in ruleBook.sections"
          :key="section.title"
          :href="`#sec-${i}`"
          class="toc-link"
        >
          {{ i + 1 }}. {{ section.title }}
        </a>
        <a v-if="showCatalog" href="#catalog" class="toc-link">
          {{ ruleBook!.sections.length + 1 }}. 选手图鉴
        </a>
      </nav>

      <main class="sections">
        <section
          v-for="(section, i) in ruleBook.sections"
          :id="`sec-${i}`"
          :key="section.title"
          class="rule-section"
        >
          <h2><span class="sec-no">{{ i + 1 }}</span>{{ section.title }}</h2>
          <p v-for="(para, j) in section.paragraphs ?? []" :key="j" class="para">{{ para }}</p>
          <ul v-if="section.bullets" class="bullets">
            <li v-for="(item, j) in section.bullets" :key="j">{{ item }}</li>
          </ul>
        </section>

        <section v-if="showCatalog" id="catalog" class="rule-section">
          <h2><span class="sec-no">图</span>选手图鉴</h2>
          <p class="para">共 {{ Object.keys(RACERS).length }} 名选手，可按能力类型筛选查看。</p>
          <RacerCatalog />
        </section>
      </main>
    </div>

    <div v-else class="not-found">
      <div class="not-found-icon">📖</div>
      <p>暂无「{{ heading }}」的规则介绍</p>
      <button type="button" class="btn btn-primary" @click="goBackOrLobby(router)">
        返回
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.rules-page {
  min-height: 100vh;
  padding: 24px 20px 60px;
  color: var(--ink);
}

.rules-head {
  display: flex;
  align-items: center;
  gap: 18px;
  max-width: 980px;
  margin: 0 auto 24px;
}
.back-btn {
  flex: none;
  font: inherit;
  font-size: 13px;
  color: var(--muted);
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s;

  &:hover {
    border-color: var(--accent-hover);
    color: var(--ink);
  }
}
.head-text h1 {
  font-family: var(--font-display);
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 400;
  margin: 0;
}
.subtitle {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.rules-body {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 32px;
  max-width: 980px;
  margin: 0 auto;
}

.toc {
  position: sticky;
  top: 24px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-4);
}
.toc-title {
  font-family: var(--font-display);
  font-size: 15px;
  margin-bottom: 6px;
  color: var(--color-primary);
}
.toc-link {
  color: var(--muted);
  font-size: 13px;
  text-decoration: none;
  padding: 4px 6px;
  border-radius: 6px;

  &:hover {
    color: var(--ink);
    background: var(--accent-soft);
  }
}

.sections {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.rule-section {
  scroll-margin-top: 24px;
  padding: 20px 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-4);
}
.rule-section h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 400;
  margin: 0 0 14px;
}
.sec-no {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--color-primary);
  font-size: 13px;
  font-family: var(--font-body);
}
.para {
  margin: 0 0 10px;
  line-height: 1.8;
  font-size: 14px;

  &:last-child {
    margin-bottom: 0;
  }
}
.bullets {
  margin: 8px 0 0;
  padding-left: 20px;
}
.bullets li {
  line-height: 1.8;
  font-size: 14px;
  margin-bottom: 6px;

  &::marker {
    color: var(--color-primary);
  }
}

.not-found {
  max-width: 980px;
  margin: 60px auto;
  text-align: center;
  color: var(--muted);
}
.not-found-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

@media (max-width: 760px) {
  .rules-body {
    grid-template-columns: 1fr;
  }
  .toc {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .toc-title {
    flex-basis: 100%;
  }
}
</style>
