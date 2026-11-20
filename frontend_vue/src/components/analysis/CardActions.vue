<template>
  <div class="actions" :class="layout">
    <button type="button" class="btn small success" title="Approve" :disabled="status === 'CLEARED'" @click="$emit('verdict', card, 'APPROVED')">
      {{ compact ? '✓' : 'Approve' }}
    </button>
    <button type="button" class="btn small danger" title="Reject" :disabled="status === 'REJECTED'" @click="$emit('verdict', card, 'REJECTED')">
      {{ compact ? '✕' : 'Reject' }}
    </button>
    <button type="button" class="btn small" title="Replace with another card" @click="$emit('replace', card)">
      {{ compact ? '⇄' : 'Replace' }}
    </button>
    <button type="button" class="btn small" title="Create a rule from this card" @click="$emit('flag', card)">
      {{ compact ? '⚑' : 'Flag…' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisStatus, PoolCard, VerdictStatus } from '../../analysis'

const props = withDefaults(
  defineProps<{ card: PoolCard; status?: AnalysisStatus; layout?: 'row' | 'grid' | 'compact' }>(),
  { layout: 'row' }
)
/** Icon buttons (labels in the tooltips), for dense table rows. */
const compact = computed(() => props.layout === 'compact')
defineEmits<{
  verdict: [card: PoolCard, status: VerdictStatus]
  replace: [card: PoolCard]
  flag: [card: PoolCard]
}>()
</script>

<style scoped>
.actions { display: grid; gap: 0.3rem; margin-top: auto; }
.actions.row { grid-template-columns: repeat(4, 1fr); }
.actions.grid { grid-template-columns: 1fr 1fr; }
.actions.compact { grid-template-columns: 1fr 1fr; gap: 0.2rem; }
.actions.compact .btn { padding: 0.1rem 0; font-size: 0.8rem; line-height: 1.4; }
</style>
