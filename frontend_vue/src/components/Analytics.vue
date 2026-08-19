<template>
  <div class="analytics">
    <header class="analytics-header">
      <div class="analytics-title">
        <h2>Pool Analysis</h2>
        <p v-if="!loading && pool.length" :class="{ 'has-warnings': totalWarnings > 0 }">
          <template v-if="totalWarnings > 0">
            {{ totalWarnings }} warning{{ totalWarnings > 1 ? 's' : '' }} on
            {{ warnedCount }} card{{ warnedCount > 1 ? 's' : '' }}
          </template>
          <template v-else>No warnings detected</template>
        </p>
      </div>
      <label class="filter-toggle">
        <input type="checkbox" v-model="onlyWarnings" />
        Show only warnings
      </label>
    </header>

      <div v-if="rules.length" class="rule-filter-row">
        <label class="rule-filter-label" for="rule-filter">Warning filter:</label>
        <select id="rule-filter" v-model="selectedRule" class="rule-filter">
          <option :value="null">All warnings</option>
          <optgroup v-for="group in ruleGroups" :key="group.type" :label="group.label">
            <option v-for="rule in group.rules" :key="rule.type + rule.value" :value="rule">
              {{ rule.label }}
            </option>
          </optgroup>
        </select>
      </div>

      <div v-if="loading" class="loading">
        <div class="spinner"></div>
        <p>Analyzing pool...</p>
      </div>

      <div v-else class="analytics-body">
        <div v-if="!visibleCards.length" class="empty">
          No cards to display.
        </div>

        <div v-else class="analytics-grid">
        <div
          v-for="card in paginatedCards"
          :key="card.reference"
          class="card-cell"
        >
          <div v-if="cardWarnings(card.reference).length" class="warn-reasons">
            <ul>
              <li v-for="(warning, i) in cardWarnings(card.reference)" :key="i">{{ warning.detail }}</li>
            </ul>
          </div>
          <div class="card-frame">
            <AlteredCard :card-ref="card.reference" />
          </div>
          <p class="card-name">{{ card.name || card.reference }}</p>
          <button type="button" class="btn-replace" disabled title="Card replacement coming soon">
            Replace
          </button>
        </div>
      </div>

      <div v-if="totalPages > 1" class="pagination">
        <button type="button" :disabled="currentPage === 1" @click="currentPage--">Prev</button>
        <span class="page-info">Page {{ currentPage }} / {{ totalPages }}</span>
        <button type="button" :disabled="currentPage === totalPages" @click="currentPage++">Next</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import axios from 'axios'
import { AlteredCard } from 'altered-tcg'

interface PoolCard {
  reference: string
  name?: string
}

interface EffectWarning {
  type: string
  detail: string
  rule: string
}

interface WarningRule {
  type: string
  value: string
  label: string
}

interface CardWarning {
  reference: string
  name: string
  warnings: EffectWarning[]
}

const props = defineProps<{ pool: PoolCard[] }>()

const loading = ref(false)
const onlyWarnings = ref(true)
const selectedRule = ref<WarningRule | null>(null)
const rules = ref<WarningRule[]>([])
const warningsByRef = ref<Record<string, EffectWarning[]>>({})
const currentPage = ref(1)
const itemsPerPage = 20

const TYPE_LABELS: Record<string, string> = {
  CARD_NAME: 'Card names',
  EFFECT_LINE: 'Effect lines',
  PATTERN: 'Patterns',
}

const totalWarnings = computed(() =>
  Object.values(warningsByRef.value).reduce((sum, warnings) => sum + warnings.length, 0)
)

const warnedCount = computed(() => Object.keys(warningsByRef.value).length)

const ruleGroups = computed(() => {
  const byType = new Map<string, WarningRule[]>()
  rules.value.forEach((rule) => {
    if (!byType.has(rule.type)) byType.set(rule.type, [])
    byType.get(rule.type)!.push(rule)
  })
  return Array.from(byType.entries()).map(([type, groupRules]) => ({
    type,
    label: TYPE_LABELS[type] ?? type,
    rules: groupRules,
  }))
})

const hasWarnings = (reference: string) => !!warningsByRef.value[reference]

const ruleMatches = (warning: EffectWarning) => {
  const rule = selectedRule.value
  if (!rule) return true
  return warning.type === rule.type && warning.rule === rule.value
}

const cardWarnings = (reference: string): EffectWarning[] =>
  (warningsByRef.value[reference] ?? []).filter(ruleMatches)

const visibleCards = computed<PoolCard[]>(() => {
  return props.pool.filter((c) => {
    if (onlyWarnings.value && !hasWarnings(c.reference)) return false
    if (selectedRule.value && !cardWarnings(c.reference).length) return false
    return true
  })
})

const totalPages = computed(() => Math.ceil(visibleCards.value.length / itemsPerPage) || 1)

const paginatedCards = computed(() => {
  const start = (currentPage.value - 1) * itemsPerPage
  return visibleCards.value.slice(start, start + itemsPerPage)
})

const fetchRules = async () => {
  try {
    const res = await axios.get<WarningRule[]>('http://localhost:8080/api/pool/warningRules')
    rules.value = res.data
  } catch (err) {
    console.error('Error fetching warning rules:', err)
  }
}

const analyze = async () => {
  if (!props.pool.length) return
  loading.value = true
  try {
    const res = await axios.post<CardWarning[]>(
      'http://localhost:8080/api/pool/analyze',
      props.pool
    )
    const map: Record<string, EffectWarning[]> = {}
    res.data.forEach((card) => {
      map[card.reference] = card.warnings
    })
    warningsByRef.value = map
  } catch (err) {
    console.error('Error analyzing pool:', err)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.pool,
  () => {
    currentPage.value = 1
    analyze()
  },
  { deep: true }
)

watch(onlyWarnings, () => {
  currentPage.value = 1
})

watch(selectedRule, () => {
  currentPage.value = 1
})

fetchRules()
analyze()
</script>

<style scoped>
.analytics {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding-top: 3rem;
}

.analytics-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1.5rem;
}

.analytics-title h2 {
  margin-bottom: 0.25rem;
}

.analytics-title p {
  color: #00d4ff;
  font-size: 0.9rem;
}

.analytics-title p.has-warnings {
  color: #ff6b6b;
  font-weight: 600;
}

.filter-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0;
  cursor: pointer;
  color: #cccccc;
}

.analytics-body {
  flex: 1;
}

.rule-filter-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.rule-filter-label {
  font-size: 0.85rem;
  color: #888888;
}

.rule-filter {
  max-width: 520px;
  min-width: 260px;
  padding: 0.4rem 0.5rem;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  font-size: 0.85rem;
  background: #161616;
  color: #e0e0e0;
}

.rule-filter:focus {
  outline: none;
  border-color: #00d4ff;
}

.empty {
  text-align: center;
  color: #555555;
  padding: 3rem 0;
}

.analytics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.card-cell {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-width: 320px;
}

.card-frame {
  position: relative;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
}

.card-frame :deep(altered-card) {
  --card-width: 280px;
}

.warn-reasons {
  background: #1a1215;
  border: 1px solid #3a2028;
  border-radius: 6px;
  padding: 0.3rem 0.5rem;
}

.warn-reasons ul {
  margin: 0;
  padding-left: 1rem;
  color: #ff8a8a;
  font-size: 0.7rem;
  line-height: 1.3;
}

.warn-reasons li + li {
  margin-top: 0.2rem;
}

.card-name {
  font-size: 0.7rem;
  color: #888888;
  text-align: center;
  line-height: 1.1;
  min-height: 0.8rem;
}

.btn-replace {
  background-color: #2a2a2a;
  color: #cccccc;
  font-size: 0.75rem;
  padding: 0.3rem 0.4rem;
  width: 100%;
  border: 1px solid #3a3a3a;
}

.btn-replace:hover:not(:disabled) {
  background-color: #333333;
}

.btn-replace:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
</style>
