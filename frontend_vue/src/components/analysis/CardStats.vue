<template>
  <span v-if="cost || powers.length" class="stats">
    <span v-if="cost" class="stat cost" title="Hand cost / Reserve cost">{{ cost }}</span>
    <span v-for="p in powers" :key="p.key" class="stat" :class="p.key" :title="p.label">
      {{ p.short }}<b>{{ p.value }}</b>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PoolCard } from '../../analysis'

/** Hand / reserve cost and the Mountain, Ocean and Forest powers of a card. */
const props = defineProps<{ card: Pick<PoolCard, 'elements'> }>()

const POWERS = [
  { key: 'mountain', element: 'MOUNTAIN_POWER', label: 'Mountain', short: 'M' },
  { key: 'ocean', element: 'OCEAN_POWER', label: 'Ocean', short: 'O' },
  { key: 'forest', element: 'FOREST_POWER', label: 'Forest', short: 'F' },
] as const

const cost = computed(() => {
  const main = props.card.elements?.MAIN_COST
  const recall = props.card.elements?.RECALL_COST
  return main != null || recall != null ? `${main ?? '?'}/${recall ?? '?'}` : ''
})

const powers = computed(() =>
  POWERS.flatMap((p) => {
    const value = props.card.elements?.[p.element]
    return value == null ? [] : [{ ...p, value }]
  })
)
</script>

<style scoped>
.stats { display: inline-flex; flex-wrap: wrap; gap: 0.25rem; font-size: 0.7rem; }

.stat {
  display: inline-flex;
  align-items: baseline;
  gap: 0.15rem;
  padding: 0 0.3rem;
  border-radius: 3px;
  background: #222;
  color: #aaa;
  line-height: 1.5;
  white-space: nowrap;
}

.stat b { color: #fff; font-weight: 700; }
.stat.cost { color: #fff; font-weight: 700; background: #2a2a2a; }
.stat.mountain { background: #33261a; color: #d9a877; }
.stat.ocean { background: #13263a; color: #7cbcff; }
.stat.forest { background: #17301c; color: #7fcf86; }
</style>
