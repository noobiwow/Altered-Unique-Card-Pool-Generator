import { computed, ref, type Ref } from 'vue'
import { api, errorMessage } from '../api'
import {
  topSeverity,
  type AnalyzedCard,
  type CardAnalysis,
  type DormantMatch,
  type EffectRule,
  type PoolCard,
  type Severity,
} from '../analysis'

/** Real findings (with a status) on one side, dormant matches on the other. */
const split = (result: AnalyzedCard[]) => ({
  analyses: result.filter((a): a is CardAnalysis & AnalyzedCard => a.status !== null),
  dormant: result.filter((a) => a.dormant?.length).map((a) => [a.reference, a.dormant!] as const),
})

/**
 * Rules and analysis results of a pool, with the actions refreshing them.
 * Results are keyed by card reference; clean cards have no entry.
 */
export function useAnalysis(pool: Ref<PoolCard[]>) {
  const rules = ref<EffectRule[]>([])
  const analyses = ref<Record<string, CardAnalysis>>({})
  /** Disabled rules and rules of other factions matching each card, by reference. */
  const dormant = ref<Record<string, DormantMatch[]>>({})
  const loading = ref(false)
  const error = ref('')
  const notice = ref('')

  const analysisOf = (card: PoolCard): CardAnalysis | undefined => analyses.value[card.reference]

  const rulesById = computed(() => Object.fromEntries(rules.value.filter((r) => r.id).map((r) => [r.id!, r])))
  const activeRuleCount = computed(() => rules.value.filter((r) => r.enabled).length)
  const categories = computed(() =>
    [...new Set(rules.value.map((r) => r.category).filter((c): c is string => !!c))].sort()
  )

  /** Number of pool cards flagged by each rule. */
  const ruleHits = computed(() => {
    const hits: Record<string, number> = {}
    for (const a of Object.values(analyses.value)) {
      for (const w of a.warnings) hits[w.ruleId] = (hits[w.ruleId] ?? 0) + 1
    }
    return hits
  })

  const statusCounts = computed(() => {
    const counts = { FLAGGED: 0, CLEARED: 0, REJECTED: 0 }
    for (const a of Object.values(analyses.value)) counts[a.status]++
    return counts
  })

  /** Cards to review, by their strongest open severity. */
  const severityCounts = computed(() => {
    const counts: Record<Severity, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 }
    for (const a of Object.values(analyses.value)) {
      const severity = a.status === 'FLAGGED' ? topSeverity(a) : null
      if (severity) counts[severity]++
    }
    return counts
  })

  const fetchRules = async () => {
    try {
      rules.value = await api.listRules()
    } catch (err) {
      error.value = `Could not load rules: ${errorMessage(err)}`
    }
  }

  const analyze = async () => {
    if (!pool.value.length) return
    loading.value = true
    error.value = ''
    try {
      const result = split(await api.analyze(pool.value))
      analyses.value = Object.fromEntries(result.analyses.map((a) => [a.reference, a]))
      dormant.value = Object.fromEntries(result.dormant)
    } catch (err) {
      error.value = `Analysis failed: ${errorMessage(err)}`
    } finally {
      loading.value = false
    }
  }

  /** Re-analyses a few cards without reloading the whole pool. */
  const refreshCards = async (cards: PoolCard[]) => {
    try {
      const result = split(await api.analyze(cards))
      const next = { ...analyses.value }
      const nextDormant = { ...dormant.value }
      cards.forEach((c) => {
        delete next[c.reference]
        delete nextDormant[c.reference]
      })
      result.analyses.forEach((a) => (next[a.reference] = a))
      result.dormant.forEach(([reference, matches]) => (nextDormant[reference] = matches))
      analyses.value = next
      dormant.value = nextDormant
    } catch (err) {
      error.value = `Analysis failed: ${errorMessage(err)}`
    }
  }

  const forget = (card: PoolCard) => {
    const next = { ...analyses.value }
    delete next[card.reference]
    analyses.value = next
    const nextDormant = { ...dormant.value }
    delete nextDormant[card.reference]
    dormant.value = nextDormant
  }

  /** Shows a message for a few seconds. */
  const flash = (message: string) => {
    notice.value = message
    setTimeout(() => {
      if (notice.value === message) notice.value = ''
    }, 4000)
  }

  return {
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
  }
}
