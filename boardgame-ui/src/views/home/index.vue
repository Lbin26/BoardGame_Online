<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { useUserStore } from '@/store/user'
import logoWordmark from '@/assets/logo-wordmark.png'

const router = useRouter()
const userStore = useUserStore()

/** 首页挂载时：已登录但无用户信息（仅水合了 token），补拉用户信息以在顶栏展示昵称 */
onMounted(async () => {
  if (userStore.isLogin && !userStore.userInfo) {
    await userStore.fetchProfile()
  }
})

/** 游戏入口卡片定义：可玩的跳转路由，其余占位禁用 */
interface GameCard {
  key: string
  name: string
  desc: string
  icon: string
  playable: boolean
  route: string
}

const games: GameCard[] = [
  {
    key: 'xiangqi',
    name: '象棋',
    desc: '楚河汉界，运筹帷幄',
    icon: '♜',
    playable: true,
    route: '/lobby?zone=abstract&game=xiangqi',
  },
  {
    key: 'go',
    name: '围棋',
    desc: '敬请期待',
    icon: '⚫',
    playable: false,
    route: '',
  },
  {
    key: 'gomoku',
    name: '五子棋',
    desc: '敬请期待',
    icon: '⚪',
    playable: false,
    route: '',
  },
]

/** 点击可玩的游戏卡片，跳转对应路由 */
function playGame(route: string): void {
  router.push(route)
}
</script>

<template>
  <div class="home-page">
    <header class="top-bar">
      <div class="logo">
        <img class="logo-img" :src="logoWordmark" alt="DiceGo" />
      </div>

      <div class="top-actions">
        <template v-if="userStore.isLogin">
          <span class="nickname">{{ userStore.userInfo?.nickName }}</span>
        </template>
        <template v-else>
          <el-button plain @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </header>

    <main class="game-grid">
      <div
        v-for="game in games"
        :key="game.key"
        class="game-card"
        :class="{ 'is-disabled': !game.playable }"
        @click="game.playable && playGame(game.route)"
      >
        <div class="game-icon">{{ game.icon }}</div>
        <h3 class="game-name">{{ game.name }}</h3>
        <p class="game-desc">{{ game.desc }}</p>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-img {
  height: 36px;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nickname {
  font-size: 15px;
  color: #f4a2b0;
}

.game-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 24px;
  padding: 48px 32px;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
}

.game-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;

  &:hover:not(.is-disabled) {
    transform: translateY(-4px);
    border-color: var(--color-primary);
    background: rgba(233, 69, 96, 0.08);
  }
}

.game-icon {
  font-size: 48px;
  line-height: 1;
}

.game-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.game-desc {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;

  .game-icon {
    filter: grayscale(1);
  }
}
</style>
