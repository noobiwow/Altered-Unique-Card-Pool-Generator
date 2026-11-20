<template>
  <div class="pattern">
    <div class="scope">
      <span class="muted">in</span>
      <select v-model="pattern.scope" aria-label="Where">
        <option value="ANY">main or echo effect</option>
        <option value="MAIN">the main effect</option>
        <option value="ECHO">the echo effect</option>
      </select>
      <span class="muted">— leave a part on “any” to ignore it</span>
    </div>

    <div v-for="part in ABILITY_PARTS" :key="part" class="part-row">
      <span class="part-name" :class="part">{{ PART_LABELS[part] }}</span>
      <select :value="modeOf(part)" :aria-label="`${PART_LABELS[part]} match`" @change="setMode(part, ($event.target as HTMLSelectElement).value)">
        <option value="">any</option>
        <option value="CONTAINS">contains</option>
        <option value="EXACT">is exactly</option>
        <option value="REGEX">matches regex</option>
        <option value="ABSENT">is empty</option>
        <option value="FRAGMENTS" title="One of the CardsData fragments chosen below, compared by id">is one of</option>
      </select>
      <FragmentPicker
        v-if="modeOf(part) === 'FRAGMENTS'"
        :model-value="pattern[part]?.fragments ?? []"
        :part="part"
        :initial-query="pattern[part]?.value ?? ''"
        @update:model-value="setFragments(part, $event)"
      />
      <input
        v-else-if="modeOf(part) && modeOf(part) !== 'ABSENT'"
        :value="pattern[part]?.value ?? ''"
        type="text"
        :placeholder="PLACEHOLDERS[part]"
        :aria-label="`${PART_LABELS[part]} value`"
        @input="setValue(part, ($event.target as HTMLInputElement).value)"
      />
      <span v-else class="muted hint">{{ modeOf(part) === 'ABSENT' ? `no ${part}` : `any ${part}` }}</span>
    </div>

    <p class="summary muted">Looks for {{ describeAbilityPattern(pattern) }}.</p>
  </div>
</template>

<script setup lang="ts">
import FragmentPicker from './FragmentPicker.vue'
import {
  ABILITY_PARTS,
  describeAbilityPattern,
  PART_LABELS,
  type AbilityPart,
  type AbilityPattern,
  type FragmentRef,
  type MatchMode,
} from '../../analysis'

/** The combination edited in place. */
const pattern = defineModel<AbilityPattern>({ required: true })

const PLACEHOLDERS: Record<AbilityPart, string> = {
  trigger: 'e.g. {J}, {H}, At Dusk, When I leave the Expedition zone',
  condition: 'e.g. If you control a token',
  effect: 'e.g. Draw a card, [Sabotage]',
}

const modeOf = (part: AbilityPart) => pattern.value[part]?.mode ?? ''

/**
 * Changes how a part is matched, keeping what can be kept: a text becomes the
 * fragment search, the first fragment becomes the text.
 */
const setMode = (part: AbilityPart, mode: string) => {
  const current = pattern.value[part]
  if (!mode) {
    pattern.value[part] = null
  } else if (mode === 'FRAGMENTS') {
    pattern.value[part] = { mode: 'FRAGMENTS', value: current?.value ?? '', fragments: current?.fragments ?? [] }
  } else {
    const value = current?.value?.trim() || current?.fragments?.[0]?.text || ''
    pattern.value[part] = { mode: mode as MatchMode, value }
  }
}

const setFragments = (part: AbilityPart, fragments: FragmentRef[]) => {
  const current = pattern.value[part]
  if (current) current.fragments = fragments
}

const setValue = (part: AbilityPart, value: string) => {
  const current = pattern.value[part]
  if (current) current.value = value
}
</script>

<style scoped>
.pattern {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.6rem 0.7rem;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface-sunken);
}

.scope, .part-row { display: flex; align-items: center; gap: 0.5rem; }
.part-row:has(.fragment-picker) { align-items: flex-start; }
.scope select { width: auto; padding: 0.3rem 0.5rem; font-size: 0.82rem; }
.part-row select { width: 9rem; flex-shrink: 0; padding: 0.4rem 0.5rem; font-size: 0.85rem; }
.part-row input { flex: 1; min-width: 0; padding: 0.4rem 0.5rem; font-size: 0.85rem; }
.part-name { width: 5.5rem; flex-shrink: 0; font-size: 0.8rem; }
.part-name.trigger { color: var(--part-trigger); font-weight: 600; }
.part-name.condition { color: var(--part-condition); font-style: italic; }
.part-name.effect { color: var(--text); }
.hint { font-style: italic; }
.summary { margin-top: 0.15rem; }
</style>
