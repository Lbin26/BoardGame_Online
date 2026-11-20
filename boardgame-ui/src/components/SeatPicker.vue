<script setup lang="ts">
import type { RoomInfo } from '@/api/chess'

const props = defineProps<{
  room: RoomInfo
  myName: string
}>()

const emit = defineEmits<{
  select: [color: 'RED' | 'BLACK']
  spectate: []
}>()

/** 他人占用的座位禁用；空位与自己的座位可点（同座幂等/换座） */
function isDisabled(occupant: string | null): boolean {
  return occupant !== null && occupant !== props.myName
}

function seatLabel(occupant: string | null): string {
  if (occupant === null) return '空位'
  return occupant === props.myName ? `${occupant}（我）` : occupant
}

function pick(color: 'RED' | 'BLACK'): void {
  const occupant = color === 'RED' ? props.room.redPlayerName : props.room.blackPlayerName
  if (isDisabled(occupant)) return
  emit('select', color)
}
</script>

<template>
  <div class="seat-picker">
    <div class="picker-title">选择你的座位</div>
    <div class="seats">
      <div
        data-testid="seat-red"
        class="seat seat-red"
        :class="{ disabled: isDisabled(room.redPlayerName) }"
        @click="pick('RED')"
      >
        <div class="seat-icon">🔴</div>
        <div class="seat-name">红方</div>
        <div class="seat-occupant">{{ seatLabel(room.redPlayerName) }}</div>
      </div>
      <div
        data-testid="seat-black"
        class="seat seat-black"
        :class="{ disabled: isDisabled(room.blackPlayerName) }"
        @click="pick('BLACK')"
      >
        <div class="seat-icon">⚫</div>
        <div class="seat-name">黑方</div>
        <div class="seat-occupant">{{ seatLabel(room.blackPlayerName) }}</div>
      </div>
    </div>
    <el-button data-testid="spectate" class="spectate-btn" @click="emit('spectate')">
      👀 仅观战
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.seat-picker {
  width: 100%;
  max-width: 420px;
  padding: 20px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  text-align: center;
}

.picker-title {
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 600;
}

.seats {
  display: flex;
  gap: 14px;
  justify-content: center;
}

.seat {
  flex: 1;
  padding: 16px 10px;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.15);
  transition: transform 0.15s, border-color 0.15s;

  &:hover:not(.disabled) {
    transform: translateY(-2px);
    border-color: rgba(255, 255, 255, 0.45);
  }

  &.disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

.seat-red {
  background: rgba(233, 69, 96, 0.12);
}

.seat-black {
  background: rgba(255, 255, 255, 0.06);
}

.seat-icon {
  font-size: 26px;
}

.seat-name {
  margin-top: 6px;
  font-weight: 600;
}

.seat-occupant {
  margin-top: 4px;
  font-size: 12px;
  color: #aaa;
  word-break: break-all;
}

.spectate-btn {
  margin-top: 14px;
}
</style>
