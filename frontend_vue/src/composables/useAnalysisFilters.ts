import { computed, ref, type Ref } from 'vue'
import { topSeverity, type CardAnalysis, type PoolCard, type Severity } from '../analysis'

/** ATTENTION = to review or rejected (the default view). */
export type StatusFilter = 'ATTENTION' | 'FLAGGED' | 'CLEARED' | 'REJECTED' | 'ALL'

/** Filters of the analysis view and the cards they keep. */
export function useAnalysisFilters(pool: Ref<PoolCard[]>, analysisOf: (card: PoolCard) => CardAnalysis | undefined) {
  const status = ref<StatusFilter>('ATTENTION')
  const severity = ref<Severity | ''>('')
  const ruleId = ref('')
  const faction = ref('')
  const search = ref('')

  const active = computed(
    () => status.value !== 'ATTENTION' || !!(severity.value || ruleId.value || faction.value || search.value.trim())
  )

  const reset = () => {
    status.value = 'ATTENTION'
    severity.value = ''
    ruleId.value = ''
    faction.value = ''
    search.value = ''
  }

  const matchesStatus = (a: CardAnalysis | undefined) => {
    switch (status.value) {
      case 'ALL':
        return true
      case 'ATTENTION':
        return !!a && a.status !== 'CLEARED'
      default:
        return a?.status === status.value
    }
  }

  const visibleCards = computed(() => {
    const query = search.value.trim().toLowerCase()
    return pool.value.filter((card) => {
      const a = analysisOf(card)
      if (!matchesStatus(a)) return false
      if (faction.value && card.mainFaction?.code !== faction.value) return false
      if (severity.value && (a?.status !== 'FLAGGED' || topSeverity(a) !== severity.value)) return false
      if (ruleId.value && !a?.warnings.some((w) => w.ruleId === ruleId.value)) return false
      if (query) {
        const text = `${card.name ?? ''} ${card.reference} ${card.mainEffect ?? ''} ${card.echoEffect ?? ''}`
        if (!text.toLowerCase().includes(query)) return false
      }
      return true
    })
  })

  return { status, severity, ruleId, faction, search, active, reset, visibleCards }
}
