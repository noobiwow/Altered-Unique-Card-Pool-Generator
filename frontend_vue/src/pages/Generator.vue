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
          <label for="faction">Faction</label>
            <select v-model="form.faction">
              <option v-for="faction in factions" :value="faction.code">
                {{ faction.name }}
              </option>
            </select>
        </div>

        <div class="form-group">
          <label for="set">Set</label>
          <select v-model="form.set">
            <option v-for="set in sets" :value="set.reference">
              {{ set.name }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label for="subType">Sub Type</label>
          <select v-model="form.subType">
            <option v-for="subtype in subtypes" :value="subtype.name">
              {{ subtype.name }}
            </option>
          </select>
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
            <label for="minCost">Min Cost</label>
            <input id="minCost" type="number" v-model="form.minCost" />
          </div>

          <div class="form-group">
            <label for="maxCost">Max Cost</label>
            <input id="maxCost" type="number" v-model="form.maxCost" />
          </div>
        </div>

        <div class="form-group">
          <label for="fieldSearch">Field Search</label>
          <input id="fieldSearch" type="text" v-model="form.fieldSearch" />
        </div>

        <div class="form-group">
          <label for="numberOfCards">Number of Cards</label>
          <input id="numberOfCards" type="number" v-model="form.numberOfCards" />
        </div>

        <label for="language">Language:</label>
        <select v-model="form.selectedLocale" id="language">
          <option v-for="locale in listOfLocale" :key="locale" :value="locale">
            {{ locale }}
          </option>
        </select>

        <div class="form-group">
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
          <button type="submit" class="btn-generate" :disabled="weightTotal !== 1">Generate</button>
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

const router = useRouter()
const POOL_STORAGE_KEY = 'cardpool_generated_pool'
const title = 'Card Pool Generator'
const loading = ref(false)
const listOfLocale = ['de','en','es','fr','it']

interface Faction {
  code: string;
  name: string;
}
const factions = ref<Faction[]>([]);
interface Set {
  reference: string;
  name: string;
}
const sets = ref<Set[]>([]);
interface Subtype {
  name: string;
}
const subtypes = ref<Subtype[]>([]);
interface MetaResponse {
  factions: Faction[];
  sets: Set[];
  subtypes: Subtype[];
}

onMounted(async () => {
  const res = await fetch("http://localhost:8080/api/form/formValues");
  const data: MetaResponse = await res.json();
  factions.value = data.factions;
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
  faction: '',
  set: '',
  subType: '',
  type: '',
  checkExcludeBanned: false,
  checkExcludeSuspended: false,
  minCost: null as number | null,
  maxCost: null as number | null,
  fieldSearch: '',
  numberOfCards: 10,
  selectedLocale: '',
  setWeights: {} as Record<string, number>,
})

const weightTotal = computed(() =>
  Object.values(form.setWeights).reduce((sum, v) => sum + v, 0)
)

const onGenerate = () => {
  loading.value = true
  const locale = form.selectedLocale || "en"
  const size = form.numberOfCards || 10
  const payload = JSON.parse(JSON.stringify(form))
  axios
    .post<any[]>(`http://localhost:8080/api/pool/generate?size=${size}&locale=${locale}`, payload)
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
