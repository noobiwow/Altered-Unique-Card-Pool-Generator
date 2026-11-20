<template>
  <ul class="card-text">
    <li
      v-for="(line, i) in lines"
      :key="i"
      :class="[line.slot === 'ECHO' ? 'echo' : 'main', { combo: line.combo }]"
    >
      <span v-if="line.slot === 'ECHO'" class="ability-tag">Echo</span>
      <AbilityText :segments="line.segments" />
    </li>
    <li v-if="!lines.length" class="none">No effect</li>
  </ul>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AbilityText from './AbilityText.vue'
import {
  abilityKey,
  abilityText,
  cardAbilities,
  highlightAbility,
  type Ability,
  type Highlighter,
  type PoolCard,
} from '../../analysis'

/** The text of a card, ability by ability, parts colored (replaces the card image). */
const props = withDefaults(
  defineProps<{
    card: Pick<PoolCard, 'abilities' | 'mainEffect' | 'echoEffect'>
    highlighters?: Highlighter[]
    /** Abilities to outline, as printed (e.g. those a combination rule matched). */
    outlined?: (Ability | null | undefined)[]
  }>(),
  { highlighters: () => [], outlined: () => [] }
)

const lines = computed(() => {
  const outlined = new Set(props.outlined.filter((a): a is Ability => !!a).map((a) => abilityKey(abilityText(a))))
  return cardAbilities(props.card as PoolCard).map((ability) => ({
    slot: ability.slot,
    segments: highlightAbility(ability, props.highlighters),
    combo: outlined.has(abilityKey(abilityText(ability))),
  }))
})
</script>

<style scoped>
.card-text {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.78rem;
  line-height: 1.4;
  color: #ddd;
}

.card-text li {
  padding: 0.25rem 0.45rem;
  border-left: 2px solid #333;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.02);
}

.card-text li.echo { border-left-color: #4b3d66; }
.card-text li.combo { outline: 1px dashed var(--warning); outline-offset: 1px; }
.card-text li.none { color: var(--text-dim); font-style: italic; }

.ability-tag {
  font-size: 0.6rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--part-condition);
  margin-right: 0.35rem;
}
</style>
