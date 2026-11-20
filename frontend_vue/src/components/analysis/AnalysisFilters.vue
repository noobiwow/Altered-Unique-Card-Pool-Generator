<template>
  <div class="filters">
    <input v-model="search" type="text" placeholder="Search name, ID or effect…" class="search" aria-label="Search cards" />
    <select v-model="ruleId" aria-label="Filter by rule">
      <option value="">All rules</option>
      <optgroup v-for="group in ruleGroups" :key="group.category" :label="group.category">
        <option v-for="r in group.rules" :key="r.id" :value="r.id">{{ r.label }} ({{ ruleHits[r.id!] ?? 0 }})</option>
      </optgroup>
    </select>
    <select v-model="faction" aria-label="Filter by faction">
      <option value="">All factions</option>
      <option v-for="f in FACTIONS" :key="f.code" :value="f.code">{{ f.name }}</option>
    </select>
    <button v-if="active" type="button" class="btn small" @click="$emit('reset')">Reset filters</button>
    <div class="view-toggle" role="group" aria-label="View">
      <button type="button" :class="{ on: view === 'table' }" @click="view = 'table'">Table</button>
      <button type="button" :class="{ on: view === 'cards' }" @click="view = 'cards'">Cards</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FACTIONS, type EffectRule } from '../../analysis'

export type AnalysisView = 'table' | 'cards'

const props = defineProps<{
  rules: EffectRule[]
  ruleHits: Record<string, number>
  /** True when a filter differs from the default. */
  active: boolean
}>()
defineEmits<{ reset: [] }>()

const search = defineModel<string>('search', { required: true })
const ruleId = defineModel<string>('ruleId', { required: true })
const faction = defineModel<string>('faction', { required: true })
const view = defineModel<AnalysisView>('view', { required: true })

/** Enabled rules grouped by category, both sorted by name. */
const ruleGroups = computed(() => {
  const groups = new Map<string, EffectRule[]>()
  for (const rule of props.rules.filter((r) => r.enabled)) {
    const key = rule.category || 'Uncategorized'
    groups.set(key, [...(groups.get(key) ?? []), rule])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([category, rules]) => ({ category, rules: rules.sort((a, b) => a.label.localeCompare(b.label)) }))
})
</script>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.filters input,
.filters select {
  padding: 0.45rem 0.6rem;
  font-size: 0.85rem;
}

.search { flex: 1; min-width: 220px; }
select { width: auto; max-width: 340px; }

.view-toggle {
  display: inline-flex;
  margin-left: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
}

.view-toggle button {
  padding: 0.4rem 0.8rem;
  font-size: 0.8rem;
  border-radius: 0;
  background: var(--surface-sunken);
  color: #999;
}

.view-toggle button.on { background: var(--accent-bg); color: var(--accent); }
</style>
