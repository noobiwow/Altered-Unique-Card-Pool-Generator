<template>
  <div v-if="matches.length" class="dormant">
    <p class="dormant-title">Already flagged, not applied here</p>
    <ul>
      <li v-for="m in matches" :key="m.ruleId" :title="tooltip(m)">
        <span class="sev" :class="m.severity">{{ m.severity[0] }}</span>
        <span class="d-label">{{ m.label }}</span>
        <span v-if="m.disabled" class="d-tag">disabled</span>
        <span v-if="m.factions.length" class="d-tag">{{ m.factions.map(factionName).join(', ') }} only</span>
        <p v-if="m.ability" class="d-match"><AbilityText :segments="highlightAbility(m.ability, [])" /></p>
        <p v-else-if="m.matchedText && m.source !== 'NAME'" class="d-match">{{ m.matchedText }}</p>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import AbilityText from './AbilityText.vue'
import { factionName, highlightAbility, type DormantMatch } from '../../analysis'

withDefaults(defineProps<{ matches?: DormantMatch[] }>(), { matches: () => [] })

const tooltip = (m: DormantMatch) => {
  const why = [
    m.disabled ? 'the rule is disabled' : '',
    m.factions.length ? `the rule only applies to ${m.factions.map(factionName).join(', ')}` : '',
  ].filter(Boolean)
  return `“${m.label}” matches this card but does not flag it: ${why.join(' and ')}.`
}
</script>

<style scoped>
.dormant {
  margin-top: 0.35rem;
  padding: 0.3rem 0.45rem;
  border: 1px dashed var(--border-strong);
  border-radius: 5px;
  opacity: 0.75;
}

.dormant-title {
  font-size: 0.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-dim);
  margin-bottom: 0.15rem;
}

ul { list-style: none; display: flex; flex-direction: column; gap: 0.2rem; }
.sev { font-size: 0.6rem; padding: 0 0.3rem; }
.d-label { font-weight: 600; margin-left: 0.3rem; font-size: 0.75rem; }

.d-tag {
  font-size: 0.58rem;
  font-weight: 700;
  text-transform: uppercase;
  margin-left: 0.3rem;
  color: var(--text-muted);
}

.d-match {
  font-size: 0.7rem;
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
