<template>
  <main class="main">
    <div class="form-container">
      <div v-if="!poolLoaded" class="loading">
        <div class="spinner"></div>
        <p>Loading pool...</p>
      </div>

      <template v-else>
        <h1>Generated Pool</h1>
        <p class="pool-count">{{ generatedPool.length }} cards</p>

        <div v-if="analyzing" class="loading">
          <div class="spinner"></div>
          <p>Analyzing effects...</p>
        </div>

        <div v-else class="post-generate-buttons">
          <button type="button" class="btn-export" @click="onExportCsv">Export CSV</button>
          <button type="button" class="btn-create" @click="onCreatePool">Create pool</button>
          <button type="button" class="btn-import" @click="showAnalytics = true">Analyze Effects</button>
          <button type="button" class="btn-visualize" @click="showVisualizer = true">Visualize</button>
          <input
            ref="fileInput"
            type="file"
            accept=".xlsx,.xls"
            @change="onFileSelected"
            hidden
          />
        </div>
      </template>
    </div>

    <div v-if="showVisualizer" class="fullscreen-overlay">
      <button type="button" class="btn-close" @click="showVisualizer = false">Close</button>
      <Visualizer :refs="generatedRefs" />
    </div>

    <div v-if="showAnalytics" class="fullscreen-overlay">
      <button type="button" class="btn-close" @click="showAnalytics = false">Close</button>
      <Analytics :pool="generatedPool" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import Visualizer from '../components/Visualizer.vue'
import Analytics from '../components/Analytics.vue'
import { getPool, clearPool } from '../pool-store'

const router = useRouter()
const POOL_STORAGE_KEY = 'cardpool_generated_pool'
const generatedPool = ref<any[]>([])
const generatedRefs = ref<string[]>([])
const poolLoaded = ref(false)
const analyzing = ref(false)
const file = ref<File | null>(null)
const showVisualizer = ref(false)
const showAnalytics = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

onMounted(() => {
  let data: any[] | null = getPool()

  if (!data) {
    const saved = localStorage.getItem(POOL_STORAGE_KEY)
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        if (Array.isArray(parsed) && parsed.length > 0) {
          data = parsed
        }
      } catch {}
    }
  }

  if (!data) {
    router.replace({ name: 'generator' })
    return
  }

  generatedPool.value = data
  generatedRefs.value = data.map((card: any) => card.reference)
  poolLoaded.value = true
  clearPool()
})

const onFileSelected = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    file.value = input.files[0]
    onAnalyzeCsv()
  }
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

const onCreatePool = () => {
  const refs = generatedPool.value.map((card: any) => card.reference)
  const payload = {
    id: 'frontier',
    version: 2,
    included_refs: refs,
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'frontier.json'
  a.click()
  window.URL.revokeObjectURL(url)
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
.pool-count {
  text-align: center;
  color: #888888;
  margin-bottom: 1.5rem;
}
</style>
