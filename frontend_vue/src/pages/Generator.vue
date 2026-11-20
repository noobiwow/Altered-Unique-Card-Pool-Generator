<template>
  <main class="main">
    <div class="form-container">
      <h1>{{ title }}</h1>
      <p v-if="currentPool && !loading" class="replace-note">
        Generating replaces your current pool ({{ currentPool.size }} cards<span v-if="currentPool.replacements"
          >, {{ currentPool.replacements }} replacement{{ currentPool.replacements === 1 ? '' : 's' }}</span
        >).
        <router-link :to="{ name: 'pool' }">Open it instead</router-link>
      </p>

      <div v-if="loading" class="loading">
        <div class="spinner"></div>
        <p>Generating pool...</p>
      </div>

      <p v-if="error" class="error-box">{{ error }}</p>

      <form v-if="!loading" @submit.prevent="onGenerate">
        <div class="form-group">
          <label for="set">Set</label>
          <select id="set" v-model="form.set">
            <option value="">All sets</option>
            <option v-for="set in sets" :key="set.reference" :value="set.reference">
              {{ set.name }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label for="subType">Sub Types</label>
          <MultiSelect
            input-id="subType"
            v-model="form.subTypes"
            :options="subtypeNames"
            placeholder="Search sub types..."
          />
        </div>

        <div class="form-group checkbox-group">
          <input id="checkExcludeBanned" type="checkbox" v-model="form.checkExcludeBanned" />
          <label for="checkExcludeBanned">Exclude Banned</label>
        </div>

        <div class="form-group checkbox-group">
          <input id="checkExcludeSuspended" type="checkbox" v-model="form.checkExcludeSuspended" />
          <label for="checkExcludeSuspended">Exclude Suspended</label>
        </div>

        <div class="form-group checkbox-group">
          <input id="excludeRejected" type="checkbox" v-model="form.excludeRejected" />
          <label for="excludeRejected">Exclude cards rejected by the team</label>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="handCost">Hand Cost</label>
            <input id="handCost" type="text" v-model="form.handCost" placeholder="e.g. 4+ or 2-" />
          </div>

          <div class="form-group">
            <label for="reserveCost">Reserve Cost</label>
            <input id="reserveCost" type="text" v-model="form.reserveCost" placeholder="e.g. 4+ or 2-" />
          </div>
        </div>
        <p v-if="!costsValid" class="field-error">
          Use a number with an optional + or - (for example 4+ or 2-).
        </p>

        <div class="form-group">
          <label for="fieldSearch">Field Search</label>
          <input id="fieldSearch" type="text" v-model="form.fieldSearch" />
        </div>

        <div class="form-group">
          <label for="numberOfCards">Number of Cards per Faction</label>
          <input id="numberOfCards" type="number" v-model="form.numberOfCards" />
          <p v-if="factionCount" class="field-hint">
            {{ totalCards }} cards total across {{ factionCount }} factions
          </p>
        </div>

        <div class="form-group checkbox-group">
          <input id="useSetWeights" type="checkbox" v-model="form.useSetWeights" />
          <label for="useSetWeights">Use Set Weights</label>
        </div>

        <div v-if="form.useSetWeights" class="form-group">
          <label>Set Weights</label>
          <div v-for="set in sets" :key="set.reference" class="weight-row">
            <label :for="'weight-' + set.reference" class="weight-label">{{ set.name }}</label>
            <input
              :id="'weight-' + set.reference"
              type="range"
              min="0"
              max="1"
              step="0.001"
              v-model.number="form.setWeights[set.reference]"
              class="weight-slider"
            />
            <input
              type="number"
              min="0"
              max="100"
              step="any"
              :value="toPercent(form.setWeights[set.reference])"
              @input="setWeightPercent(set.reference, $event)"
              class="weight-pct-input"
            /><span class="weight-pct-suffix">%</span>
          </div>
          <div v-if="!weightsValid" class="weight-error">
            Total: {{ toPercent(weightTotal) }}% — must equal 100%
          </div>
        </div>

        <div class="button-group">
          <button type="submit" class="btn-generate" :disabled="!weightsValid || !costsValid">Generate</button>
        </div>
      </form>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, toRaw } from 'vue'
import { useRouter } from 'vue-router'
import MultiSelect from '../components/MultiSelect.vue'
import { api, errorMessage } from '../api'
import { currentPoolSummary, saveCurrentPool, type PoolContext } from '../pool-store'
import type { PoolSummary } from '../api'
import type { SetOption } from '../analysis'

const COST_EXPRESSION = /^\s*\d+\s*[+-]?\s*$/

const router = useRouter()
const currentPool = ref<PoolSummary | null>(null)
const title = 'Card Pool Generator'
const loading = ref(false)
const error = ref('')
const sets = ref<SetOption[]>([])
const subtypeNames = ref<string[]>([])
/** Pools are generated for every faction offered by the backend. */
const factionCount = ref(0)

const form = reactive({
  set: '',
  subTypes: [] as string[],
  checkExcludeBanned: false,
  checkExcludeSuspended: false,
  excludeRejected: true,
  handCost: '',
  reserveCost: '',
  fieldSearch: '',
  numberOfCards: 10,
  useSetWeights: true,
  setWeights: {} as Record<string, number>,
})

onMounted(async () => {
  currentPoolSummary().then(
    (summary) => (currentPool.value = summary),
    () => {}
  )
  try {
    const catalog = await api.catalog()
    factionCount.value = catalog.factions.length
    sets.value = catalog.sets
    subtypeNames.value = catalog.subtypes.map((s) => s.name)
    catalog.sets.forEach((s) => (form.setWeights[s.reference] = s.defaultWeight))
  } catch (err) {
    error.value = `Could not load the form values: ${errorMessage(err)}`
  }
})

const totalCards = computed(() => (form.numberOfCards || 0) * factionCount.value)
const weightTotal = computed(() => Object.values(form.setWeights).reduce((sum, v) => sum + v, 0))
const weightsValid = computed(() => !form.useSetWeights || Math.abs(weightTotal.value - 1) < 1e-9)
const costsValid = computed(() =>
  [form.handCost, form.reserveCost].every((value) => value.trim() === '' || COST_EXPRESSION.test(value))
)

const toPercent = (weight: number | undefined) => Number(((weight ?? 0) * 100).toFixed(6))

const setWeightPercent = (reference: string, event: Event) => {
  const value = (event.target as HTMLInputElement).valueAsNumber
  form.setWeights[reference] = Number.isFinite(value) ? Math.min(100, Math.max(0, value)) / 100 : 0
}

/** The form as the backend expects it (see FilterForm). */
const filterPayload = () => {
  const { numberOfCards, useSetWeights, setWeights, ...filters } = structuredClone(toRaw(form))
  return useSetWeights ? { ...filters, setWeights } : filters
}

const onGenerate = async () => {
  loading.value = true
  error.value = ''
  const filter = filterPayload()
  try {
    const cards = await api.generate(form.numberOfCards || 10, filter)
    // The filters are kept with the pool: replacements reuse them.
    const now = new Date()
    const context: PoolContext = {
      name: `Generated pool ${now.toLocaleDateString()}`,
      filter,
      replacements: [],
      generatedAt: now.toISOString(),
    }
    // Saved before leaving: a failure is shown on the pool page.
    await saveCurrentPool(cards, context)
    router.push({ name: 'pool' })
  } catch (err) {
    error.value = `Generation failed: ${errorMessage(err)}`
    loading.value = false
  }
}
</script>

<style scoped>
.replace-note {
  text-align: center;
  font-size: 0.85rem;
  color: var(--text-muted);
  margin: -0.5rem 0 1.5rem;
}
</style>
