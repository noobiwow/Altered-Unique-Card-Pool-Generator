<template>
  <div class="analysis">
    <header class="header">
      <div>
        <h2>Pool Analysis</h2>
        <p class="subtitle">
          {{ poolName }} · {{ pool.length }} cards · {{ activeRuleCount }} active rule{{ activeRuleCount === 1 ? '' : 's' }}
        </p>
      </div>
      <div class="header-actions">
        <button type="button" class="btn" @click="dialog = { kind: 'rules' }">Rules ({{ rules.length }})</button>
        <button type="button" class="btn" @click="dialog = { kind: 'rule', card: null }">+ Flag an effect</button>
        <button type="button" class="btn primary" :disabled="loading || !pool.length" @click="dialog = { kind: 'report' }">
          Create report
        </button>
      </div>
    </header>

    <SummaryBar
      v-if="!loading"
      v-model:status="filters.status.value"
      v-model:severity="filters.severity.value"
      :total="pool.length"
      :status-counts="statusCounts"
      :severity-counts="severityCounts"
    />

    <AnalysisFilters
      v-model:search="filters.search.value"
      v-model:rule-id="filters.ruleId.value"
      v-model:faction="filters.faction.value"
      v-model:view="view"
      :rules="rules"
      :rule-hits="ruleHits"
      :active="filters.active.value"
      @reset="filters.reset"
    />

    <p v-if="error" class="error-box spaced">{{ error }}</p>
    <p v-if="notice" class="notice spaced">{{ notice }}</p>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>Analyzing pool...</p>
    </div>

    <template v-else-if="sortedCards.length">
      <p class="count">{{ sortedCards.length }} card{{ sortedCards.length === 1 ? '' : 's' }}</p>
      <AnalysisTable
        v-if="view === 'table'"
        :cards="pageItems"
        :analyses="analyses"
        :dormant="dormant"
        :rules-by-id="rulesById"
        @verdict="openVerdict"
        @replace="openReplacement"
        @flag="openRuleEditor"
      />
      <AnalysisCardGrid
        v-else
        :cards="pageItems"
        :analyses="analyses"
        :dormant="dormant"
        :rules-by-id="rulesById"
        @verdict="openVerdict"
        @replace="openReplacement"
        @flag="openRuleEditor"
      />
      <Pagination v-model="page" :pages="pages" />
    </template>

    <p v-else class="empty">
      {{ filters.active.value ? 'No cards match these filters.' : 'Nothing to review in this pool 🎉' }}
    </p>

    <RulesManager
      v-if="dialog?.kind === 'rules'"
      :rules="rules"
      :hit-counts="ruleHits"
      :pool-cards="pool"
      :categories="categories"
      @close="dialog = null"
      @changed="reloadRules"
    />
    <RuleEditor
      v-if="dialog?.kind === 'rule'"
      :source-card="dialog.card"
      :pool-cards="pool"
      :categories="categories"
      @close="dialog = null"
      @saved="onRuleSaved"
    />
    <VerdictDialog
      v-if="dialog?.kind === 'verdict'"
      :card="dialog.card"
      :analysis="analysisOf(dialog.card)"
      :initial-status="dialog.status"
      @close="dialog = null"
      @saved="onVerdictSaved"
    />
    <ReplacementDialog
      v-if="dialog?.kind === 'replace'"
      :card="dialog.card"
      :pool-references="poolReferences"
      :filter="filter"
      @close="dialog = null"
      @replace="onReplace"
    />
    <CreateReportDialog
      v-if="dialog?.kind === 'report'"
      :pool="pool"
      :pool-name="poolName"
      :replacements="replacements"
      :status-counts="statusCounts"
      @close="dialog = null"
      @created="onReportCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import Pagination from '../Pagination.vue'
import AnalysisCardGrid from './AnalysisCardGrid.vue'
import AnalysisFilters, { type AnalysisView } from './AnalysisFilters.vue'
import AnalysisTable from './AnalysisTable.vue'
import CreateReportDialog from './CreateReportDialog.vue'
import ReplacementDialog from './ReplacementDialog.vue'
import RuleEditor from './RuleEditor.vue'
import RulesManager from './RulesManager.vue'
import SummaryBar from './SummaryBar.vue'
import VerdictDialog from './VerdictDialog.vue'
import { useAnalysis } from '../../composables/useAnalysis'
import { useAnalysisFilters } from '../../composables/useAnalysisFilters'
import { usePagination } from '../../composables/usePagination'
import {
  byFactionThenUrgency,
  byUrgencyThenFaction,
  type EffectRule,
  type PoolCard,
  type Replacement,
  type Report,
  type VerdictStatus,
} from '../../analysis'

const props = withDefaults(
  defineProps<{
    pool: PoolCard[]
    poolName?: string
    /** Filters the pool was generated with, used to pick replacements. */
    filter?: Record<string, unknown> | null
    replacements?: Replacement[]
  }>(),
  { poolName: 'Pool', filter: null, replacements: () => [] }
)
const emit = defineEmits<{ replace: [oldCard: PoolCard, newCard: PoolCard] }>()

/** The dialog currently open, if any. */
type Dialog =
  | { kind: 'rules' }
  | { kind: 'rule'; card: PoolCard | null }
  | { kind: 'verdict'; card: PoolCard; status: VerdictStatus }
  | { kind: 'replace'; card: PoolCard }
  | { kind: 'report' }

const PAGE_SIZE = { table: 50, cards: 24 } as const
const VIEW_STORAGE_KEY = 'cardpool_analysis_view'

const router = useRouter()
const pool = toRef(props, 'pool')
const {
  rules,
  analyses,
  dormant,
  loading,
  error,
  notice,
  analysisOf,
  rulesById,
  activeRuleCount,
  categories,
  ruleHits,
  statusCounts,
  severityCounts,
  fetchRules,
  analyze,
  refreshCards,
  forget,
  flash,
} = useAnalysis(pool)
const filters = useAnalysisFilters(pool, analysisOf)

const dialog = ref<Dialog | null>(null)
const view = ref<AnalysisView>(readView())
watch(view, (v) => {
  try {
    localStorage.setItem(VIEW_STORAGE_KEY, v)
  } catch {}
})

const poolReferences = computed(() => props.pool.map((c) => c.reference))

/** Table: most urgent first. Cards: grouped by faction. */
const sortedCards = computed(() =>
  [...filters.visibleCards.value].sort(
    view.value === 'table' ? byUrgencyThenFaction(analysisOf) : byFactionThenUrgency(analysisOf)
  )
)
// The cards view shows fewer items per page: images are heavy.
const { page, pages, pageItems } = usePagination(sortedCards, () => PAGE_SIZE[view.value])

function readView(): AnalysisView {
  try {
    return localStorage.getItem(VIEW_STORAGE_KEY) === 'cards' ? 'cards' : 'table'
  } catch {
    return 'table'
  }
}

// Replacements change the pool; they are re-analysed card by card instead.
let skipNextAnalyze = false
watch(pool, () => {
  if (skipNextAnalyze) {
    skipNextAnalyze = false
    return
  }
  analyze()
})

const openRuleEditor = (card: PoolCard | null) => (dialog.value = { kind: 'rule', card })
const openVerdict = (card: PoolCard, status: VerdictStatus) => (dialog.value = { kind: 'verdict', card, status })
const openReplacement = (card: PoolCard) => (dialog.value = { kind: 'replace', card })

const reloadRules = async () => {
  await fetchRules()
  await analyze()
}

const onRuleSaved = async (rule: EffectRule) => {
  dialog.value = null
  await reloadRules()
  flash(`Rule “${rule.label}” saved: ${ruleHits.value[rule.id!] ?? 0} card(s) flagged in this pool.`)
}

const onVerdictSaved = async () => {
  const current = dialog.value
  dialog.value = null
  if (current?.kind === 'verdict') await refreshCards([current.card])
}

const onReplace = async (newCard: PoolCard) => {
  const current = dialog.value
  dialog.value = null
  if (current?.kind !== 'replace') return
  const oldCard = current.card
  skipNextAnalyze = true
  emit('replace', oldCard, newCard)
  forget(oldCard)
  await refreshCards([newCard])
  flash(`Replaced ${oldCard.name ?? oldCard.reference} with ${newCard.name ?? newCard.reference}.`)
}

const onReportCreated = (report: Report) => {
  dialog.value = null
  window.open(router.resolve({ name: 'report', params: { id: report.id } }).href, '_blank')
  flash(`Report “${report.title}” created.`)
}

fetchRules()
analyze()
</script>

<style scoped>
.analysis {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding-top: 3rem;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.header h2 { margin-bottom: 0.25rem; }
.subtitle { color: var(--text-muted); font-size: 0.85rem; }
.header-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.spaced { margin-bottom: 1rem; }
.count { font-size: 0.8rem; color: #777; margin-bottom: 0.5rem; }
.empty { text-align: center; color: var(--text-dim); padding: 3rem 0; }
</style>
