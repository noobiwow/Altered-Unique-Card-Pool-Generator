<template>
  <main class="main">
    <div class="form-container">
      <h1>{{ title }}</h1>

      <div v-if="loading" class="loading">
        <div class="spinner"></div>
        <p>Generating pool...</p>
      </div>

      <form v-if="!loading && !poolGenerated" @submit.prevent="onGenerate">
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
          <input id="subType" type="text" v-model="form.subType" />
        </div>

        <div class="form-group">
          <label for="type">Type</label>
          <input id="type" type="text" v-model="form.type" />
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

        <div class="button-group">
          <button type="submit" class="btn-generate">Generate</button>
        </div>
      </form>

      <div v-if="poolGenerated && !showVisualizer && !showAnalytics" class="post-generate-buttons">
        <button type="button" class="btn-export" @click="onExportCsv">Export CSV</button>
        <!--<button type="button" class="btn-import" @click="showAnalytics = true">Analyze Effects</button>-->
        <button type="button" class="btn-visualize" @click="showVisualizer = true">Visualize</button>
        <input
          ref="fileInput"
          type="file"
          accept=".xlsx,.xls"
          @change="onFileSelected"
          hidden
        />
      </div>

      <div v-if="poolGenerated && analyzing" class="loading">
        <div class="spinner"></div>
        <p>Analyzing effects...</p>
      </div>
    </div>

    <div v-if="showVisualizer" class="fullscreen-overlay">
      <button type="button" class="btn-close" @click="showVisualizer = false">Close</button>
      <Visualizer :refs="generatedRefs" />
    </div>

    <!-- <div v-if="showAnalytics" class="fullscreen-overlay">
      <button type="button" class="btn-close" @click="showAnalytics = false">Close</button>
      <Analytics />
    </div>-->
  </main>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import axios from 'axios'
import Visualizer from './components/Visualizer.vue'
import Analytics from './components/Analytics.vue'

const title = 'Card Pool Generator'
const loading = ref(false)
const poolGenerated = ref(false)
const analyzing = ref(false)
const file = ref<File | null>(null)
const generatedPool = ref<any[]>([])
const generatedRefs = ref<string[]>([])
const showVisualizer = ref(false)
const showAnalytics = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
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
interface MetaResponse {
  factions: Faction[];
  sets: Set[];
}

onMounted(async () => {
  const res = await fetch("http://localhost:8080/api/form/formValues");
  const data: MetaResponse = await res.json();
  factions.value = data.factions;
  sets.value = data.sets;
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
})

const triggerFileInput = () => {
  fileInput.value?.click()
}

const onFileSelected = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    file.value = input.files[0]
    onAnalyzeCsv()
  }
}

const onGenerate = () => {
  loading.value = true
  poolGenerated.value = false
  const locale = form.selectedLocale || "en"
  const size = form.numberOfCards || 10
  console.log(form);
  axios
    .post<any[]>(`http://localhost:8080/api/pool/generate?size=${size}&locale=${locale}`, form)
    .then((response) => {
      console.log('Generated pool:', response.data)
      generatedPool.value = response.data 
      generatedRefs.value = response.data.map((card: any) => card.reference)
      loading.value = false
      poolGenerated.value = true
    })
    .catch((err) => {
      console.error('Error generating pool:', err)
      loading.value = false
    })
}

const onExportCsv = () => {
  axios
        .post(
      'http://localhost:8080/api/pool/export',
      generatedPool.value,
      { responseType: 'blob' }
    )
    .then((response) => {
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const a = document.createElement('a')
      a.href = url
      a.download = 'cards.xlsx'
      a.click()
      window.URL.revokeObjectURL(url)
    })
    .catch((err) => console.error('Error exporting:', err))
}

const onAnalyzeCsv = () => {
  if (!file.value) {
    console.error('No file selected')
    return
  }

  analyzing.value = true
  const formData = new FormData()
  formData.append('file', file.value)

  axios
    .post<string>('http://localhost:8080/api/pool/import/stats', formData)
    .then((response) => {
      console.log('Analysis result:', response.data)
      analyzing.value = false
    })
    .catch((err) => {
      console.error('Error analyzing:', err)
      analyzing.value = false
    })
}
</script>
