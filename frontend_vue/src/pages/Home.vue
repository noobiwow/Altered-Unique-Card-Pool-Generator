<template>
  <main class="main">
    <div class="home-container">
      <h1>{{ title }}</h1>
      <p class="tagline">Generate a pool of Altered TCG cards based on your own criteria.</p>
      <div class="home-actions">
        <button type="button" class="btn-generate" @click="goToGenerator">Generate a Pool</button>
        <button v-if="hasSavedPool" type="button" class="btn-restore" @click="goToPool">Continue working on pool</button>
      </div>
    </div>

    <div class="visualize-container">
      <h2>Visualize a Pool</h2>

      <div v-if="!manifestLoaded" class="loading">
        <div class="spinner"></div>
        <p>Loading pools...</p>
      </div>

      <div v-else class="pool-options">
        <button
          type="button"
          class="pool-option"
          :disabled="loadingPool || !currentPool"
          @click="loadPool(currentPool!)"
        >
          Current Pool
        </button>
        <button
          type="button"
          class="pool-option"
          :disabled="loadingPool || !nextPool"
          @click="loadPool(nextPool!)"
        >
          Next Pool
        </button>

        <div v-if="previousPools.length" class="previous-picker">
          <select v-model="selectedPrevious" :disabled="loadingPool">
            <option value="" disabled>Previous pools...</option>
            <option v-for="pool in previousPools" :key="pool.key" :value="pool.key">
              {{ pool.label }}
            </option>
          </select>
          <button
            type="button"
            class="pool-option"
            :disabled="loadingPool || !selectedPrevious"
            @click="loadPool(getPreviousPool(selectedPrevious)!)"
          >
            Visualize
          </button>
        </div>
      </div>

      <div v-if="loadingPool" class="loading">
        <div class="spinner"></div>
        <p>Loading pool...</p>
      </div>
    </div>

    <div v-if="showVisualizer" class="fullscreen-overlay">
      <button type="button" class="btn-close" @click="closeVisualizer">Close</button>
      <Visualizer :refs="poolRefs" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Visualizer from '../components/Visualizer.vue'

interface PoolEntry {
  key: string
  label: string
  path: string
}

interface PoolManifest {
  current?: PoolEntry
  next?: PoolEntry
  previous: PoolEntry[]
}

const title = 'Card Pool Generator'
const router = useRouter()
const POOL_STORAGE_KEY = 'cardpool_generated_pool'
const hasSavedPool = ref(false)

const goToGenerator = () => {
  router.push({ name: 'generator' })
}

const goToPool = () => {
  router.push({ name: 'pool' })
}

onMounted(() => {
  hasSavedPool.value = !!localStorage.getItem(POOL_STORAGE_KEY)
})

const manifestLoaded = ref(false)
const currentPool = ref<PoolEntry | undefined>()
const nextPool = ref<PoolEntry | undefined>()
const previousPools = ref<PoolEntry[]>([])

onMounted(async () => {
  try {
    const res = await fetch('/pools/manifest.json')
    const data: PoolManifest = await res.json()
    currentPool.value = data.current
    nextPool.value = data.next
    previousPools.value = data.previous ?? []
  } catch (err) {
    console.error('Error loading pool manifest:', err)
  } finally {
    manifestLoaded.value = true
  }
})

const selectedPrevious = ref('')
const loadingPool = ref(false)
const showVisualizer = ref(false)
const poolRefs = ref<string[]>([])

const getPreviousPool = (key: string): PoolEntry | undefined =>
  previousPools.value.find((p) => p.key === key)

const loadPool = async (pool: PoolEntry) => {
  loadingPool.value = true
  try {
    const res = await fetch(`/pools/${pool.path}`)
    const data = await res.json()
    poolRefs.value = data.included_refs ?? []
    showVisualizer.value = true
  } catch (err) {
    console.error('Error loading pool:', err)
  } finally {
    loadingPool.value = false
  }
}

const closeVisualizer = () => {
  showVisualizer.value = false
  poolRefs.value = []
}
</script>

<style scoped>
.main {
  flex-direction: column;
  gap: 2rem;
}

.home-container {
  background: #1e1e1e;
  padding: 3rem 2rem;
  border-radius: 12px;
  border: 1px solid #2a2a2a;
  text-align: center;
  width: 100%;
  max-width: 500px;
}

.tagline {
  margin-bottom: 2rem;
  color: #888888;
}

.home-actions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.btn-generate {
  background-color: #00d4ff;
  color: #0a0a0a;
  padding: 0.75rem 1.5rem;
  font-weight: 600;
}

.btn-generate:hover {
  background-color: #00b8e6;
}

.btn-restore {
  background-color: #2a2a2a;
  color: #cccccc;
  padding: 0.75rem 1.5rem;
  border: 1px solid #3a3a3a;
}

.btn-restore:hover {
  background-color: #333333;
}

.visualize-container {
  background: #1e1e1e;
  padding: 2rem;
  border-radius: 12px;
  border: 1px solid #2a2a2a;
  width: 100%;
  max-width: 500px;
}

.visualize-container h2 {
  text-align: center;
  margin-bottom: 1.5rem;
  font-size: 1.25rem;
  color: #ffffff;
}

.pool-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.pool-option {
  background-color: #2a2a2a;
  color: #cccccc;
  width: 100%;
  border: 1px solid #3a3a3a;
}

.pool-option:hover {
  background-color: #333333;
}

.pool-option:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.previous-picker {
  display: flex;
  gap: 0.5rem;
}
</style>
