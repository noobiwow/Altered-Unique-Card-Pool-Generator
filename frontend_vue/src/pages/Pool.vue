<template>
  <main class="main">
    <div class="form-container">
      <div v-if="loading" class="loading">
        <div class="spinner"></div>
        <p>Loading pool...</p>
      </div>

      <p v-else-if="!cards.length" class="error-box">{{ error || 'No pool.' }}</p>

      <template v-else>
        <h1>{{ context.name }}</h1>
        <p class="pool-count">
          {{ cards.length }} cards<span v-if="context.replacements.length">
            · {{ context.replacements.length }} replacement{{ context.replacements.length === 1 ? '' : 's' }}</span>
        </p>
        <p v-if="saveError" class="storage-warning">
          {{ saveError }} Use “Create pool” to keep a copy before leaving.
        </p>
        <p v-if="error" class="error-box">{{ error }}</p>

        <div class="post-generate-buttons">
          <button type="button" class="btn-export" :disabled="exporting" @click="exportExcel">
            {{ exporting ? 'Exporting…' : 'Export Excel' }}
          </button>
          <button type="button" class="btn-create" @click="downloadPoolFile">Create pool</button>
          <button type="button" class="btn-import" @click="overlay = 'analysis'">Analyze Effects</button>
          <button type="button" class="btn-visualize" @click="overlay = 'visualizer'">Visualize</button>
        </div>
      </template>
    </div>

    <div v-if="overlay" class="fullscreen-overlay">
      <button type="button" class="btn-close" @click="overlay = null">Close</button>
      <Visualizer v-if="overlay === 'visualizer'" :cards="cards" />
      <AnalysisPanel
        v-else
        :pool="cards"
        :pool-name="context.name"
        :filter="context.filter"
        :replacements="context.replacements"
        @replace="replaceCard"
      />
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Visualizer from '../components/Visualizer.vue'
import AnalysisPanel from '../components/analysis/AnalysisPanel.vue'
import { api, errorMessage, saveBlob } from '../api'
import { loadCurrentPool, saveCurrentPool, saveError, type PoolContext } from '../pool-store'
import type { PoolCard } from '../analysis'

/** File format of a published pool (see pools/). */
const POOL_FILE = { id: 'frontier', version: 2 }

const router = useRouter()

const cards = ref<PoolCard[]>([])
const context = ref<PoolContext>({ name: 'Pool', filter: null, replacements: [] })
const loading = ref(true)
const exporting = ref(false)
const error = ref('')
const overlay = ref<'analysis' | 'visualizer' | null>(null)

const references = computed(() => cards.value.map((card) => card.reference))

onMounted(async () => {
  try {
    const pool = await loadCurrentPool()
    if (!pool) return router.replace({ name: 'generator' })
    cards.value = pool.cards
    context.value = pool.context
  } catch (err) {
    error.value = `Could not load the pool: ${errorMessage(err)}`
  } finally {
    loading.value = false
  }
})

/** Every change is saved right away (see pool-store). */
const persist = () => saveCurrentPool(cards.value, context.value)

function replaceCard(oldCard: PoolCard, newCard: PoolCard) {
  const index = cards.value.findIndex((card) => card.reference === oldCard.reference)
  if (index === -1) return
  cards.value = cards.value.map((card, i) => (i === index ? newCard : card))
  context.value = {
    ...context.value,
    replacements: [
      ...context.value.replacements,
      {
        fromReference: oldCard.reference,
        fromName: oldCard.name,
        toReference: newCard.reference,
        toName: newCard.name,
        replacedAt: new Date().toISOString(),
      },
    ],
  }
  persist()
}

async function exportExcel() {
  exporting.value = true
  error.value = ''
  try {
    saveBlob(await api.exportExcel(cards.value), 'cards.xlsx')
  } catch (err) {
    error.value = `Export failed: ${errorMessage(err)}`
  } finally {
    exporting.value = false
  }
}

function downloadPoolFile() {
  const payload = { ...POOL_FILE, included_refs: references.value }
  saveBlob(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }), `${POOL_FILE.id}.json`)
}
</script>

<style scoped>
.pool-count {
  text-align: center;
  color: var(--text-muted);
  margin-bottom: 1.5rem;
}

.storage-warning {
  font-size: 0.8rem;
  color: var(--warning);
  text-align: center;
  margin: -0.8rem 0 1rem;
}

.error-box {
  margin-bottom: 1rem;
}
</style>
