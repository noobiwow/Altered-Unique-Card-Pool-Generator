<template>
  <div class="table-wrap">
    <table class="analysis-table">
      <thead>
        <tr>
          <th class="col-card">Card</th>
          <th class="col-effects">Effects <PartLegend class="legend" /></th>
          <th class="col-warnings">Why it is flagged</th>
          <th class="col-actions"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.card.reference" :class="row.analysis?.status ?? 'clean'">
          <td class="col-card">
            <div class="card-head">
              <span v-if="row.analysis" class="status" :class="row.analysis.status">
                {{ STATUS_LABELS[row.analysis.status] }}
              </span>
              <span v-else class="status clean">No warning</span>
              <span v-if="row.top" class="sev" :class="row.top">{{ row.top }}</span>
              <CardStats :card="row.card" class="card-stats" />
            </div>
            <div class="card-name"><AbilityText :segments="row.name" /></div>
            <div class="card-facts">
              <span>{{ row.card.mainFaction?.name ?? factionName(row.card.mainFaction?.code) }}</span>
              <span v-if="row.card.cardSet?.name || row.card.cardSet?.code">{{ row.card.cardSet?.name ?? row.card.cardSet?.code }}</span>
              <span v-if="row.type">{{ row.type }}</span>
              <button type="button" class="copy" :title="`Copy ${row.card.reference}`" @click="copy(row.card.reference)">
                {{ copied === row.card.reference ? '✓ copied' : row.card.reference }}
              </button>
            </div>
          </td>

          <td class="col-effects">
            <ul class="abilities">
              <li
                v-for="(ability, i) in row.abilities"
                :key="i"
                :class="[ability.slot === 'ECHO' ? 'echo' : 'main', ability.flag, { flagged: !!ability.flag, combo: ability.combo }]"
              >
                <span v-if="ability.slot === 'ECHO'" class="ability-tag">Echo</span>
                <span v-if="ability.combo" class="combo-tag" title="This ability matches a combination rule">combo</span>
                <AbilityText :segments="ability.segments" />
              </li>
              <li v-if="!row.abilities.length" class="none">No effect</li>
            </ul>
          </td>

          <td class="col-warnings">
            <ul v-if="row.analysis?.warnings.length" class="warnings">
              <li v-for="w in row.analysis.warnings" :key="w.ruleId" :class="{ reviewed: w.reviewed }" :title="w.reason || undefined">
                <span class="sev" :class="w.severity">{{ w.severity[0] }}</span>
                <span class="w-label">{{ w.label }}</span>
                <span v-if="w.ability" class="w-tag combo">combo</span>
                <span v-if="w.reviewed" class="w-tag ok">reviewed</span>
                <span v-else-if="row.analysis.verdict?.status === 'APPROVED'" class="w-tag new">new</span>
                <p v-if="w.reason" class="w-reason">{{ w.reason }}</p>
              </li>
            </ul>
            <p v-if="row.analysis?.verdict?.note" class="note" :title="row.analysis.verdict.note">💬 {{ row.analysis.verdict.note }}</p>
            <DormantMatches :matches="dormant[row.card.reference]" />
          </td>

          <td class="col-actions">
            <CardActions
              :card="row.card"
              :status="row.analysis?.status"
              layout="compact"
              @verdict="(c, st) => $emit('verdict', c, st)"
              @replace="(c) => $emit('replace', c)"
              @flag="(c) => $emit('flag', c)"
            />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AbilityText from './AbilityText.vue'
import CardActions from './CardActions.vue'
import CardStats from './CardStats.vue'
import DormantMatches from './DormantMatches.vue'
import PartLegend from './PartLegend.vue'
import {
  abilityKey,
  abilityText,
  cardAbilities,
  factionName,
  highlight,
  highlightAbility,
  highlightersFor,
  matchedCombinations,
  maxSeverity,
  openWarnings,
  STATUS_LABELS,
  topSeverity,
  type Ability,
  type AbilitySlot,
  type CardAnalysis,
  type DormantMatch,
  type EffectRule,
  type PoolCard,
  type Severity,
  type TextSegment,
  type VerdictStatus,
} from '../../analysis'

const props = defineProps<{
  cards: PoolCard[]
  analyses: Record<string, CardAnalysis>
  /** Disabled rules and rules of other factions matching each card. */
  dormant: Record<string, DormantMatch[]>
  rulesById: Record<string, EffectRule>
}>()
defineEmits<{
  verdict: [card: PoolCard, status: VerdictStatus]
  replace: [card: PoolCard]
  flag: [card: PoolCard]
}>()

interface AbilityRow {
  slot: AbilitySlot
  segments: TextSegment[]
  /** Strongest unreviewed severity found in the ability. */
  flag?: Severity
  /** The ability matched a combination (ABILITY) rule that is still open. */
  combo: boolean
}

const rows = computed(() =>
  props.cards.map((card) => {
    const analysis = props.analyses[card.reference]
    const warnings = analysis?.warnings ?? []
    const open = analysis ? openWarnings(analysis) : []
    const highlighters = highlightersFor(warnings, props.rulesById)
    // Whole abilities reported by open warnings (a rule matching the ability text or a combination).
    const matchedLines = new Map(open.filter((w) => w.matchedText).map((w) => [abilityKey(w.matchedText!), w.severity]))
    const combos = matchedCombinations(open)

    const toRow = (ability: Ability): AbilityRow => {
      const segments = highlightAbility(ability, highlighters)
      const key = abilityKey(abilityText(ability))
      const marked = segments.filter((s) => s.severity && !s.reviewed).map((s) => s.severity!)
      const whole = matchedLines.get(key)
      return {
        slot: ability.slot,
        segments,
        flag: maxSeverity(whole ? [...marked, whole] : marked) ?? undefined,
        combo: combos.has(key),
      }
    }

    return {
      card,
      analysis,
      top: topSeverity(analysis),
      name: highlight(card.name ?? card.reference, highlighters, 'NAME'),
      type: card.cardType?.name?.en,
      abilities: cardAbilities(card).map(toRow),
    }
  })
)

const copied = ref('')
const copy = async (reference: string) => {
  try {
    await navigator.clipboard.writeText(reference)
    copied.value = reference
    setTimeout(() => {
      if (copied.value === reference) copied.value = ''
    }, 1500)
  } catch {
    prompt('Card ID:', reference)
  }
}
</script>

<style scoped>
.table-wrap {
  overflow-x: auto;
  border: 1px solid #242424;
  border-radius: 8px;
}

.analysis-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8rem;
  line-height: 1.35;
  min-width: 860px;
  table-layout: fixed;
}

.analysis-table th {
  position: sticky;
  top: 0;
  background: #181818;
  text-align: left;
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #777;
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid var(--border);
  z-index: 1;
}

.analysis-table td {
  padding: 0.35rem 0.5rem;
  border-bottom: 1px solid #202020;
  vertical-align: top;
  color: #ddd;
}

.analysis-table tbody tr:nth-child(even) td { background: #131313; }
.analysis-table tbody tr:hover td { background: #191919; }
tr.FLAGGED td:first-child { box-shadow: inset 3px 0 0 #a8781a; }
tr.REJECTED td:first-child { box-shadow: inset 3px 0 0 #a83a46; }
tr.CLEARED td:first-child { box-shadow: inset 3px 0 0 #2f7a4f; }

.col-card { width: 15rem; }
.col-warnings { width: 15rem; }
.col-actions { width: 4.6rem; }

.card-head { display: flex; flex-wrap: wrap; align-items: center; gap: 0.25rem; }
.card-head .status, .card-head .sev { font-size: 0.62rem; padding: 0.05rem 0.35rem; }
.card-stats { margin-left: auto; }
.status.clean { background: #222; color: #777; }
.card-name { font-weight: 600; color: #fff; font-size: 0.86rem; margin-top: 0.2rem; }

.card-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0 0.5rem;
  font-size: 0.7rem;
  color: #8a8a8a;
}

.copy {
  background: none;
  padding: 0;
  font-family: monospace;
  font-size: 0.66rem;
  color: #666;
  text-align: left;
  overflow-wrap: anywhere;
  font-weight: 400;
}

.copy:hover { color: var(--accent); }

.abilities {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.abilities li {
  padding: 0.05rem 0.4rem;
  border-left: 2px solid var(--border);
  border-radius: 0 4px 4px 0;
  color: #c4c4c4;
}

.abilities li.echo { border-left-color: #463a66; }
.abilities li.flagged { color: #eee; }
.abilities li.flagged.HIGH { border-left-color: var(--danger-strong); background: #2a1418; }
.abilities li.flagged.MEDIUM { border-left-color: #ffb547; background: #2a2010; }
.abilities li.flagged.LOW { border-left-color: #5aaeff; background: #10202e; }
.abilities li.combo { outline: 1px dashed currentColor; outline-offset: -1px; }
.abilities li.combo.HIGH { outline-color: var(--danger-strong); }
.abilities li.combo.MEDIUM { outline-color: #ffb547; }
.abilities li.combo.LOW { outline-color: #5aaeff; }
.abilities li.none { color: #555; border-left-color: transparent; }

.combo-tag, .ability-tag {
  font-size: 0.58rem;
  font-weight: 700;
  text-transform: uppercase;
  margin-right: 0.3rem;
}

.combo-tag { color: var(--warning); }
.ability-tag { color: #c3a8ff; }
.legend { margin-left: 0.6rem; }

.warnings { list-style: none; display: flex; flex-direction: column; gap: 0.2rem; }
.warnings li.reviewed { opacity: 0.55; }
.warnings .sev { font-size: 0.6rem; padding: 0 0.3rem; }
.w-label { font-weight: 600; margin-left: 0.3rem; }
.w-tag { font-size: 0.58rem; font-weight: 700; text-transform: uppercase; margin-left: 0.3rem; }
.w-tag.ok { color: var(--success); }
.w-tag.new, .w-tag.combo { color: var(--warning); }

/* Reasons and notes stay on two lines; the full text is in the tooltip. */
.w-reason, .note {
  font-size: 0.7rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.w-reason { color: var(--reason); }
.note { margin-top: 0.25rem; color: #aaa; }
</style>
