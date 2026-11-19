<template>
  <main class="main">
    <div class="form-container">
      <h1>{{ title }}</h1>

      <div v-if="loading" class="loading">
        <div class="spinner"></div>
        <p>Generating pool...</p>
      </div>

      <form v-if="!loading" @submit.prevent="onGenerate">
        <div class="form-group">
          <label for="set">Set</label>
          <select v-model="form.set">
            <option v-for="set in sets" :value="set.reference">
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

        <label for="language">Language:</label>
        <select v-model="form.selectedLocale" id="language">
          <option v-for="locale in listOfLocale" :key="locale" :value="locale">
            {{ locale }}
          </option>
        </select>

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
              step="0.01"
              v-model.number="form.setWeights[set.reference]"
              class="weight-slider"
            />
            <input
              type="number"
              min="0"
              max="100"
              step="1"
              :value="Math.round(form.setWeights[set.reference] * 100)"
              @input="form.setWeights[set.reference] = ($event.target as HTMLInputElement).valueAsNumber / 100 || 0"
              class="weight-pct-input"
            /><span class="weight-pct-suffix">%</span>
          </div>
          <div v-if="weightTotal !== 1" class="weight-error">
            Total: {{ (weightTotal * 100).toFixed(0) }}% — must equal 100%
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { setPool } from '../pool-store'
import { API_BASE } from '../api'
import MultiSelect from '../components/MultiSelect.vue'

const router = useRouter()
const POOL_STORAGE_KEY = 'cardpool_generated_pool'
const title = 'Card Pool Generator'
const loading = ref(false)
const listOfLocale = ['de','en','es','fr','it']

interface Set {
  reference: string;
  name: string;
}
const sets = ref<Set[]>([]);
interface Subtype {
  name: string;
}
const subtypes = ref<Subtype[]>([]);
const subtypeNames = computed(() => subtypes.value.map((subtype) => subtype.name));
interface Faction {
  code: string;
  name: string;
}
interface MetaResponse {
  factions: Faction[];
  sets: Set[];
  subtypes: Subtype[];
}

// Pools are generated for every faction returned by the backend.
const factionCount = ref(0)

onMounted(async () => {
  const res = await fetch(`${API_BASE}/api/form/formValues`);
  const data: MetaResponse = await res.json();
  factionCount.value = data.factions.length;
  sets.value = data.sets;
  subtypes.value = data.subtypes;
  const defaultWeights: Record<string, number> = {
    COREKS: 0.05,
    CORE: 0.05,
    ALIZE: 0.1,
    BISE: 0.1,
    CYCLONE: 0.20,
    DUSTER: 0.25,
    EOLE: 0.25,
  };
  data.sets.forEach(s => {
    form.setWeights[s.reference] = defaultWeights[s.reference] ?? 0.1;
  });
});

const form = reactive({
  set: '',
  subTypes: [] as string[],
  type: '',
  checkExcludeBanned: false,
  checkExcludeSuspended: false,
  handCost: '',
  reserveCost: '',
  fieldSearch: '',
  numberOfCards: 10,
  selectedLocale: '',
  useSetWeights: true,
  setWeights: {} as Record<string, number>,
})

const totalCards = computed(() => (form.numberOfCards || 0) * factionCount.value)

const weightTotal = computed(() =>
  Object.values(form.setWeights).reduce((sum, v) => sum + v, 0)
)

const weightsValid = computed(() => !form.useSetWeights || weightTotal.value === 1)
const costExpression = /^\s*\d+\s*[+-]?\s*$/
const costsValid = computed(() =>
  [form.handCost, form.reserveCost].every(
    (value) => value.trim() === '' || costExpression.test(value)
  )
)

const onGenerate = () => {
  loading.value = true
  const locale = form.selectedLocale || "en"
  const size = form.numberOfCards || 10
  const payload = JSON.parse(JSON.stringify(form))
  if (!form.useSetWeights) {
    delete payload.setWeights
  }
  delete payload.useSetWeights
  axios
    .post<any[]>(`${API_BASE}/api/pool/generate?size=${size}&locale=${locale}`, payload)
    .then((response) => {
      const data = response.data
      setPool(data)
      try {
        localStorage.setItem(POOL_STORAGE_KEY, JSON.stringify(data))
      } catch {}
      router.push({ name: 'pool' })
    })
    .catch((err) => {
      console.error('Error generating pool:', err)
      loading.value = false
    })
}
</script>

<style scoped>
</style>
