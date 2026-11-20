<template>
  <div>
    <template v-for="group in groups" :key="group.code">
      <h3 class="faction-header">{{ group.name }}</h3>
      <div class="grid">
        <article v-for="card in group.cards" :key="card.reference" class="cell" :class="analysisOf(card)?.status">
          <div class="cell-head">
            <span v-if="analysisOf(card)" class="status" :class="analysisOf(card)!.status">
              {{ STATUS_LABELS[analysisOf(card)!.status] }}
            </span>
            <span v-else class="status clean">No warning</span>
            <span v-if="topSeverity(analysisOf(card))" class="sev" :class="topSeverity(analysisOf(card))">
              {{ topSeverity(analysisOf(card)) }}
            </span>
          </div>

          <ul v-if="analysisOf(card)?.warnings.length" class="warnings">
            <li v-for="w in sortedWarnings(card)" :key="w.ruleId" :class="{ reviewed: w.reviewed }">
              <div class="w-title">
                <span class="sev" :class="w.severity">{{ w.severity[0] }}</span>
                <span class="w-label">{{ w.label }}</span>
                <span v-if="w.reviewed" class="tag ok" title="Already reviewed by the team">✓ reviewed</span>
                <span v-else-if="analysisOf(card)?.verdict?.status === 'APPROVED'" class="tag new"
                  title="Rule added after the card was approved">new</span>
              </div>
              <p v-if="w.reason" class="w-reason">{{ w.reason }}</p>
              <p v-if="w.ability" class="w-match" :title="w.matchedText ?? undefined">
                <span class="src">Combo</span><AbilityText :segments="highlightAbility(w.ability, [])" />
              </p>
              <p v-else-if="w.matchedText && w.source !== 'NAME'" class="w-match" :title="w.matchedText">
                <span class="src">{{ w.source === 'ECHO' ? 'Echo' : 'Main' }}</span>{{ w.matchedText }}
              </p>
            </li>
          </ul>

          <p v-if="analysisOf(card)?.verdict?.note" class="note">💬 {{ analysisOf(card)!.verdict!.note }}</p>
          <DormantMatches :matches="dormant[card.reference]" />

          <div class="identity">
            <p class="name">{{ card.name || card.reference }}</p>
            <p class="facts">
              <span v-if="card.cardType?.name?.en">{{ card.cardType.name.en }}</span>
            </p>
            <CardStats :card="card" />
            <p class="ref" :title="card.reference">{{ card.reference }}</p>
          </div>
          <CardText
            :card="card"
            :highlighters="highlightersFor(analysisOf(card)?.warnings ?? [], rulesById)"
            :outlined="openWarnings(analysisOf(card)).map((w) => w.ability)"
          />

          <CardActions
            :card="card"
            :status="analysisOf(card)?.status"
            @verdict="(c, s) => emit('verdict', c, s)"
            @replace="(c) => emit('replace', c)"
            @flag="(c) => emit('flag', c)"
          />
        </article>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AbilityText from './AbilityText.vue'
import CardActions from './CardActions.vue'
import CardStats from './CardStats.vue'
import CardText from './CardText.vue'
import DormantMatches from './DormantMatches.vue'
import {
  factionName,
  highlightAbility,
  highlightersFor,
  openWarnings,
  STATUS_LABELS,
  topSeverity,
  type CardAnalysis,
  type DormantMatch,
  type EffectRule,
  type PoolCard,
  type VerdictStatus,
} from '../../analysis'

const props = defineProps<{
  /** Cards of the current page, already sorted by faction. */
  cards: PoolCard[]
  analyses: Record<string, CardAnalysis>
  /** Disabled rules and rules of other factions matching each card. */
  dormant: Record<string, DormantMatch[]>
  rulesById: Record<string, EffectRule>
}>()
const emit = defineEmits<{
  verdict: [card: PoolCard, status: VerdictStatus]
  replace: [card: PoolCard]
  flag: [card: PoolCard]
}>()

const analysisOf = (card: PoolCard) => props.analyses[card.reference]

/** Open warnings first. */
const sortedWarnings = (card: PoolCard) =>
  [...(analysisOf(card)?.warnings ?? [])].sort((a, b) => Number(a.reviewed) - Number(b.reviewed))

/** Consecutive cards of the same faction, each group under a header. */
const groups = computed(() => {
  const result: { code: string; name: string; cards: PoolCard[] }[] = []
  for (const card of props.cards) {
    const code = card.mainFaction?.code ?? '?'
    const last = result[result.length - 1]
    if (last?.code === code) last.cards.push(card)
    else result.push({ code, name: card.mainFaction?.name ?? factionName(code), cards: [card] })
  }
  return result
})
</script>

<style scoped>
.faction-header {
  margin: 1.2rem 0 0.75rem;
  padding-bottom: 0.4rem;
  border-bottom: 1px solid var(--border);
  font-size: 1.1rem;
  color: #fff;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}

.cell {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  max-width: 320px;
  padding: 0.5rem;
  border: 1px solid #222;
  border-radius: 8px;
  background: var(--surface-sunken);
}

.cell.FLAGGED { border-color: #4a3a1a; }
.cell.REJECTED { border-color: #4a2028; }
.cell.CLEARED { border-color: #1f3a2a; }
.cell-head { display: flex; gap: 0.4rem; align-items: center; }
.status.clean { background: #222; color: #777; }

.warnings { list-style: none; display: flex; flex-direction: column; gap: 0.35rem; }
.warnings li { background: #1c1416; border: 1px solid #33232a; border-radius: 6px; padding: 0.3rem 0.45rem; }
.warnings li.reviewed { background: #141a16; border-color: #22302a; opacity: 0.75; }
.w-title { display: flex; align-items: center; gap: 0.35rem; font-size: 0.75rem; color: #eee; }
.w-label { font-weight: 600; overflow-wrap: anywhere; }
.tag { margin-left: auto; font-size: 0.65rem; white-space: nowrap; }
.tag.ok { color: var(--success); }
.tag.new { color: var(--warning); font-weight: 700; text-transform: uppercase; }
.w-reason { font-size: 0.7rem; color: var(--reason); margin-top: 0.15rem; line-height: 1.3; }

.w-match {
  font-size: 0.68rem;
  color: #999;
  margin-top: 0.15rem;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.src { font-size: 0.6rem; font-weight: 700; text-transform: uppercase; color: #8fdcff; margin-right: 0.3rem; }
.note { font-size: 0.72rem; color: #bbb; background: var(--surface-raised); border-radius: 6px; padding: 0.3rem 0.45rem; }
.identity { border-top: 1px solid #262626; padding-top: 0.4rem; }
.name { font-size: 0.9rem; font-weight: 600; color: #eee; }
.facts { display: flex; gap: 0.5rem; font-size: 0.7rem; color: #999; }
.ref { font-family: monospace; font-size: 0.65rem; color: var(--text-dim); overflow-wrap: anywhere; }
</style>
