<template>
  <div class="summary">
    <button
      v-for="chip in statusChips"
      :key="chip.value"
      type="button"
      class="chip"
      :class="[chip.value, { on: status === chip.value }]"
      @click="status = chip.value"
    >
      <span class="count">{{ chip.count }}</span>
      <span>{{ chip.label }}</span>
    </button>
    <span class="divider"></span>
    <button
      v-for="s in SEVERITIES"
      :key="s"
      type="button"
      class="chip"
      :class="{ on: severity === s }"
      :disabled="!severityCounts[s]"
      @click="severity = severity === s ? '' : s"
    >
      <span class="sev" :class="s">{{ s }}</span>
      <span class="count">{{ severityCounts[s] }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SEVERITIES, type Severity } from '../../analysis'
import type { StatusFilter } from '../../composables/useAnalysisFilters'

const props = defineProps<{
  total: number
  statusCounts: { FLAGGED: number; CLEARED: number; REJECTED: number }
  severityCounts: Record<Severity, number>
}>()
const status = defineModel<StatusFilter>('status', { required: true })
const severity = defineModel<Severity | ''>('severity', { required: true })

const statusChips = computed(() => [
  { value: 'ATTENTION' as const, label: 'Need attention', count: props.statusCounts.FLAGGED + props.statusCounts.REJECTED },
  { value: 'FLAGGED' as const, label: 'To review', count: props.statusCounts.FLAGGED },
  { value: 'REJECTED' as const, label: 'Rejected', count: props.statusCounts.REJECTED },
  { value: 'CLEARED' as const, label: 'Approved', count: props.statusCounts.CLEARED },
  { value: 'ALL' as const, label: 'All cards', count: props.total },
])
</script>

<style scoped>
.summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.8rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.7rem;
  font-size: 0.8rem;
  background: var(--surface-raised);
  color: #aaa;
  border: 1px solid var(--border);
  border-radius: 999px;
}

.chip:hover:not(:disabled) { border-color: #444; color: #ddd; }
.chip:disabled { opacity: 0.35; cursor: default; }
.chip.on { border-color: var(--accent); color: #fff; background: var(--accent-bg); }
.count { font-weight: 700; color: #fff; }
.FLAGGED .count { color: var(--warning); }
.REJECTED .count { color: var(--danger); }
.CLEARED .count { color: var(--success); }

.divider {
  width: 1px;
  height: 1.4rem;
  background: var(--border);
  margin: 0 0.3rem;
}
</style>
