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
          <input id="faction" type="text" v-model="form.faction" />
        </div>

        <div class="form-group">
          <label for="set">Set</label>
          <input id="set" type="text" v-model="form.set" />
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

        <label for="dropdown">Choose an option:</label>
        <!-- 3. v-model binds the selection, v-for generates options -->
        <select v-model="form.selectedLocale" id="dropdown">
          <option disabled value="">Please select one</option>
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
import { ref, reactive } from 'vue'
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

<style scoped>
:host {
  display: block;
  height: 100dvh;
  font-family:
    'Inter',
    -apple-system,
    BlinkMacSystemFont,
    'Segoe UI',
    Roboto,
    Helvetica,
    Arial,
    sans-serif;
  box-sizing: border-box;
}

.main {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 1rem;
  background-color: #f5f5f5;
}

.form-container {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}

h1 {
  text-align: center;
  margin-bottom: 1.5rem;
  font-size: 1.5rem;
  color: #333;
}

.form-group {
  margin-bottom: 1rem;
}

.form-row {
  display: flex;
  gap: 1rem;
}

.form-row .form-group {
  flex: 1;
}

label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
  color: #555;
}

input[type='text'],
input[type='number'] {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  box-sizing: border-box;
}

input[type='text']:focus,
input[type='number']:focus {
  outline: none;
  border-color: #007bff;
}

.checkbox-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox-group input[type='checkbox'] {
  width: auto;
  margin: 0;
}

.checkbox-group label {
  margin-bottom: 0;
  cursor: pointer;
}

.button-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1.5rem;
}

button {
  padding: 0.75rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn-generate {
  background-color: #007bff;
  color: white;
}

.btn-generate:hover {
  background-color: #0056b3;
}

.btn-import {
  background-color: #6c757d;
  color: white;
}

.btn-import:hover {
  background-color: #545b62;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  gap: 1rem;
}

.loading p {
  font-size: 1.125rem;
  color: #007bff;
  margin: 0;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #e0e0e0;
  border-top-color: #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.post-generate-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1.5rem;
}

.btn-export {
  background-color: #28a745;
  color: white;
}

.btn-export:hover {
  background-color: #1e7e34;
}

.btn-visualize {
  background-color: #17a2b8;
  color: white;
}

.btn-visualize:hover {
  background-color: #138496;
}

.fullscreen-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: white;
  z-index: 1000;
  overflow: auto;
  padding: 2rem;
}

.btn-close {
  position: fixed;
  top: 1rem;
  right: 1rem;
  background: #dc3545;
  color: white;
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  z-index: 1001;
}
</style>
