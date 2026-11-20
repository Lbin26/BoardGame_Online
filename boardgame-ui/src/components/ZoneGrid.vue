<script setup lang="ts">
import type { Zone } from '@/constants/catalog'

defineProps<{ zones: Zone[] }>()
defineEmits<{ select: [zoneKey: string] }>()
</script>

<template>
  <div class="zone-grid">
    <div
      v-for="zone in zones"
      :key="zone.key"
      class="zone-card"
      @click="$emit('select', zone.key)"
    >
      <div class="zone-head">
        <span class="zone-icon">{{ zone.icon }}</span>
        <span class="zone-name">{{ zone.name }}</span>
      </div>
      <div class="zone-tags">
        <span v-for="m in zone.mechanisms" :key="m" class="zone-tag">{{ m }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.zone-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.zone-card {
  padding: 14px;
  background: #16213e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
    transform: translateY(-2px);
  }
}

.zone-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.zone-icon {
  font-size: 22px;
  line-height: 1;
}

.zone-name {
  font-size: 16px;
  font-weight: 600;
  color: #eee;
}

.zone-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.zone-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #aaa;
}
</style>
