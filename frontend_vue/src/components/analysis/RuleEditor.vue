<template>
  <Modal :title="isNew ? 'Flag a dangerous effect' : 'Edit rule'" size="large" @close="$emit('close')">
    <div class="editor">
      <section v-if="sourceCard" class="source">
        <p class="section-title">Pick what to flag on <strong>{{ sourceCard.name || sourceCard.reference }}</strong></p>
        <p class="muted">
          Use a whole <strong>line</strong> as text to look for, or its <strong>combination</strong> of
          <PartLegend /> to match each part separately, then loosen what doesn't matter.
        </p>
        <ul class="ability-list">
          <li v-if="sourceCard.name" class="ability">
            <span class="ability-tag">Name</span>
            <span class="ability-body">{{ sourceCard.name }}</span>
            <button type="button" class="btn small" @click="useLine('NAME', sourceCard.name, 'EXACT')">Use name</button>
          </li>
          <li v-for="(a, i) in sourceAbilities" :key="i" class="ability">
            <span class="ability-tag" :class="{ echo: a.ability.slot === 'ECHO' }">{{ a.ability.slot === 'ECHO' ? 'Echo' : 'Main' }}</span>
            <span class="ability-body"><AbilityText :segments="a.segments" /></span>
            <span class="ability-actions">
              <button type="button" class="btn small" title="Look for this text" @click="useLine(a.ability.slot === 'ECHO' ? 'ECHO_EFFECT' : 'MAIN_EFFECT', a.text)">
                Line
              </button>
              <button type="button" class="btn small primary" title="Match trigger, condition and effect separately" @click="useCombination(a.ability)">
                Combination
              </button>
            </span>
          </li>
        </ul>
      </section>

      <section class="grid">
        <div class="field wide">
          <label for="rule-label">Label</label>
          <input id="rule-label" type="text" v-model="draft.label" placeholder="e.g. Reserve recursion" />
        </div>
        <div class="field">
          <label for="rule-severity">Severity</label>
          <select id="rule-severity" v-model="draft.severity">
            <option v-for="s in SEVERITIES" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
        <div class="field">
          <label for="rule-category">Category</label>
          <input id="rule-category" type="text" v-model="draft.category" list="rule-categories" placeholder="e.g. Reserve" />
          <datalist id="rule-categories">
            <option v-for="c in categories" :key="c" :value="c" />
          </datalist>
        </div>
        <div class="field full">
          <label for="rule-reason">Why is it dangerous?</label>
          <textarea id="rule-reason" v-model="draft.reason" rows="2" placeholder="Shown to the team in the analysis and in reports" />
        </div>
      </section>

      <section>
        <p class="section-title">Conditions <span class="muted">(all must match)</span></p>
        <div v-for="(c, i) in draft.conditions" :key="i" class="condition" :class="{ 'is-ability': c.field === 'ABILITY' }">
          <select v-model="c.field" class="cond-field" aria-label="Field" @change="onFieldChange(c)">
            <option v-for="(label, f) in FIELD_LABELS" :key="f" :value="f">{{ label }}</option>
          </select>
          <select v-if="!isCostField(c.field)" v-model="c.negate" class="cond-negate" aria-label="Negate">
            <option :value="false">{{ c.field === 'ABILITY' ? 'must have' : 'must' }}</option>
            <option :value="true">{{ c.field === 'ABILITY' ? 'must not have' : 'must not' }}</option>
          </select>
          <AbilityPatternEditor v-if="c.field === 'ABILITY' && c.ability" v-model="c.ability" class="cond-ability" />
          <template v-else>
            <select v-if="!isCostField(c.field)" v-model="c.mode" class="cond-mode" aria-label="Match mode">
              <option v-for="(label, m) in MODE_LABELS_MUST" :key="m" :value="m">{{ label }}</option>
            </select>
            <input
              type="text"
              v-model="c.value"
              class="cond-value"
              :placeholder="placeholderFor(c)"
              aria-label="Value"
            />
          </template>
          <button
            type="button"
            class="btn small"
            :disabled="draft.conditions.length === 1"
            title="Remove condition"
            @click="draft.conditions.splice(i, 1)"
          >
            ✕
          </button>
        </div>
        <button type="button" class="btn small" @click="addCondition">+ Add condition</button>
      </section>

      <section class="grid">
        <div class="field full">
          <label>Only for factions <span class="muted">(none = all)</span></label>
          <div class="factions">
            <label v-for="f in FACTIONS" :key="f.code" class="chip" :class="{ on: draft.factions.includes(f.code) }">
              <input type="checkbox" :value="f.code" v-model="draft.factions" />
              {{ f.name }}
            </label>
          </div>
        </div>
        <div class="field full">
          <label class="inline-check">
            <input type="checkbox" v-model="draft.enabled" />
            Enabled
          </label>
        </div>
      </section>

      <section class="preview">
        <div class="preview-head">
          <button type="button" class="btn" :disabled="previewing || !poolCards.length" @click="runPreview">
            {{ previewing ? 'Testing…' : 'Test on this pool' }}
          </button>
          <span v-if="preview" class="muted">
            {{ preview.length }} card{{ preview.length === 1 ? '' : 's' }} of {{ poolCards.length }} would be flagged
          </span>
        </div>
        <ul v-if="preview && preview.length" class="preview-list">
          <li v-for="p in preview.slice(0, 30)" :key="p.reference">
            <strong>{{ p.name || p.reference }}</strong>
            <span class="muted"> · {{ factionName(p.factionCode) }}</span>
            <span v-if="p.warnings[0]?.ability" class="matched"><AbilityText :segments="highlightAbility(p.warnings[0].ability, [])" /></span>
            <span v-else-if="p.warnings[0]?.matchedText" class="matched">“{{ p.warnings[0].matchedText }}”</span>
          </li>
          <li v-if="preview.length > 30" class="muted">… and {{ preview.length - 30 }} more</li>
        </ul>
      </section>

      <p v-if="error" class="error-box">{{ error }}</p>
    </div>

    <template #footer>
      <button type="button" class="btn" @click="$emit('close')">Cancel</button>
      <button type="button" class="btn primary" :disabled="saving || !canSave" @click="save">
        {{ saving ? 'Saving…' : isNew ? 'Save rule' : 'Save changes' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import Modal from '../Modal.vue'
import AbilityPatternEditor from './AbilityPatternEditor.vue'
import AbilityText from './AbilityText.vue'
import PartLegend from './PartLegend.vue'
import { api, errorMessage } from '../../api'
import {
  abilityText,
  cardAbilities,
  emptyRule,
  factionName,
  highlightAbility,
  isCompletePattern,
  isCostField,
  patternFromAbility,
  withoutStaticMarker,
  FACTIONS,
  FIELD_LABELS,
  SEVERITIES,
  type Ability,
  type CardAnalysis,
  type EffectRule,
  type MatchMode,
  type PoolCard,
  type RuleCondition,
  type RuleField,
} from '../../analysis'

const props = defineProps<{
  /** Rule to edit; omitted to create a new one. */
  rule?: EffectRule | null
  /** Card the new rule is created from, to pick its abilities. */
  sourceCard?: PoolCard | null
  poolCards: PoolCard[]
  categories: string[]
}>()
const emit = defineEmits<{ close: []; saved: [rule: EffectRule] }>()

/** Text match modes ("is empty" only exists for ability parts). */
const MODE_LABELS_MUST: Partial<Record<MatchMode, string>> = {
  CONTAINS: 'contain',
  EXACT: 'be exactly',
  REGEX: 'match regex',
}

const isNew = computed(() => !props.rule?.id)
const draft = reactive<EffectRule>(
  props.rule
    ? JSON.parse(JSON.stringify({ ...emptyRule(), ...props.rule, factions: props.rule.factions ?? [] }))
    : { ...emptyRule(), createdFrom: props.sourceCard?.reference ?? null }
)
const saving = ref(false)
const previewing = ref(false)
const preview = ref<CardAnalysis[] | null>(null)
const error = ref('')

/** Abilities of the source card, with their parts colored. */
const sourceAbilities = computed(() =>
  (props.sourceCard ? cardAbilities(props.sourceCard) : []).map((ability) => ({
    ability,
    text: abilityText(ability),
    segments: highlightAbility(ability, []),
  }))
)

const isComplete = (c: RuleCondition) => (c.field === 'ABILITY' ? isCompletePattern(c.ability) : !!c.value?.trim())
const canSave = computed(() => draft.conditions.length > 0 && draft.conditions.every(isComplete))

/** Switching to "Ability" needs a pattern; leaving it drops it. */
const onFieldChange = (c: RuleCondition) => {
  if (c.field === 'ABILITY') {
    c.ability ??= { scope: 'ANY', trigger: null, condition: null, effect: null }
  } else {
    c.ability = null
  }
}

watch(
  () => JSON.stringify(draft.conditions) + draft.factions.join(),
  () => {
    preview.value = null
  }
)

const defaultLabel = (text: string) => {
  if (!draft.label) draft.label = text.length > 60 ? text.slice(0, 57) + '…' : text
}

/** First condition = the text of a line (static marker removed so it matches more reliably). */
const useLine = (field: RuleField, line: string, mode: MatchMode = 'CONTAINS') => {
  const value = field === 'NAME' ? line : withoutStaticMarker(line)
  draft.conditions[0] = { field, mode, value, negate: false, ability: null }
  defaultLabel(value)
}

/** First condition = this ability's exact combination of trigger, condition and effect. */
const useCombination = (ability: Ability) => {
  draft.conditions[0] = { field: 'ABILITY', mode: 'CONTAINS', value: '', negate: false, ability: patternFromAbility(ability) }
  defaultLabel([ability.trigger, ability.condition].filter(Boolean).join(' ') + ' → ' + withoutStaticMarker(ability.effect ?? ''))
}

const addCondition = () => {
  draft.conditions.push({ field: 'HAND_COST', mode: 'CONTAINS', value: '', negate: false })
}

const placeholderFor = (c: RuleCondition) => {
  if (isCostField(c.field)) return 'e.g. 3- (3 or less), 4+ or 2'
  if (c.mode === 'REGEX') return 'e.g. two cards? of your deck'
  switch (c.field) {
    case 'KEYWORD':
      return 'e.g. Anchored, Sabotage'
    case 'TRIGGER':
      return "e.g. When I'm played, At Dusk, {J}"
    case 'CARD_TYPE':
      return 'e.g. Character, Spell, Permanent'
    case 'SET':
      return 'e.g. CYCLONE'
    default:
      return 'Text to look for'
  }
}

const payload = (): EffectRule => ({
  ...draft,
  label: draft.label?.trim() ?? '',
  conditions: draft.conditions.map((c) =>
    c.field === 'ABILITY'
      ? { ...c, value: '', ability: c.ability && trimPattern(c.ability) }
      : { ...c, value: c.value.trim(), ability: null }
  ),
})

const trimPattern = (p: NonNullable<RuleCondition['ability']>) => ({
  scope: p.scope,
  trigger: p.trigger?.mode ? { ...p.trigger, value: p.trigger.value?.trim() } : null,
  condition: p.condition?.mode ? { ...p.condition, value: p.condition.value?.trim() } : null,
  effect: p.effect?.mode ? { ...p.effect, value: p.effect.value?.trim() } : null,
})

const runPreview = async () => {
  error.value = ''
  previewing.value = true
  try {
    preview.value = await api.previewRule(payload(), props.poolCards)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    previewing.value = false
  }
}

const save = async () => {
  error.value = ''
  saving.value = true
  try {
    const saved = isNew.value ? await api.createRule(payload()) : await api.updateRule(props.rule!.id!, payload())
    emit('saved', saved)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>

.editor {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
}

.section-title {
  color: #ddd;
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 0.4rem;
}

.ability-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin-top: 0.5rem;
}

.ability {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  width: 100%;
  text-align: left;
  background: var(--surface-sunken);
  border: 1px solid var(--border);
  color: #ddd;
  font-size: 0.82rem;
  line-height: 1.35;
  padding: 0.45rem 0.6rem;
}

.ability-body { flex: 1; min-width: 0; }
.ability-actions { display: flex; gap: 0.3rem; flex-shrink: 0; }
.condition.is-ability { align-items: flex-start; }
.cond-ability { flex: 1; min-width: 0; }

.ability-tag {
  display: inline-block;
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  background: #263540;
  color: #8fdcff;
  border-radius: 4px;
  padding: 0.05rem 0.35rem;
  margin-right: 0.5rem;
}

.ability-tag.echo {
  background: #2e2640;
  color: #c3a8ff;
}

.grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 0.8rem;
}

.field.full {
  grid-column: 1 / -1;
}

.field label {
  margin-bottom: 0.3rem;
}

.condition {
  display: flex;
  gap: 0.4rem;
  align-items: center;
  margin-bottom: 0.4rem;
}

.condition select,
.condition input {
  padding: 0.45rem 0.5rem;
  font-size: 0.85rem;
}

.cond-field { width: 11rem; flex-shrink: 0; }
.cond-negate { width: 6.5rem; flex-shrink: 0; }
.cond-mode { width: 9rem; flex-shrink: 0; }
.cond-value { flex: 1; min-width: 0; }

.factions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  text-transform: none;
  letter-spacing: 0;
  font-size: 0.82rem;
  color: #bbb;
  border: 1px solid #333;
  border-radius: 999px;
  padding: 0.25rem 0.7rem;
  margin: 0;
  cursor: pointer;
}

.chip input { display: none; }
.chip.on { border-color: var(--accent); color: var(--accent); background: var(--accent-bg); }

.inline-check {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  text-transform: none;
  letter-spacing: 0;
  font-size: 0.9rem;
  color: #ccc;
  cursor: pointer;
}

.preview-head {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.preview-list {
  margin-top: 0.6rem;
  list-style: none;
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 0.4rem 0.6rem;
  font-size: 0.8rem;
  color: #ccc;
}

.preview-list li + li { margin-top: 0.3rem; }

.matched {
  display: block;
  color: #999;
  font-style: italic;
  font-size: 0.75rem;
}

@media (max-width: 700px) {
  .grid { grid-template-columns: 1fr; }
  .condition { flex-wrap: wrap; }
  .cond-field, .cond-negate, .cond-mode { width: auto; flex: 1; }
  .cond-value { flex-basis: 100%; }
}
</style>
