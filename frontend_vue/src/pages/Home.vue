<template>
  <main class="main">
    <div class="home-container">
      <h1>{{ title }}</h1>
      <p class="tagline">Generate a pool of Altered TCG cards based on your own criteria.</p>
      <div v-if="pool" class="last-pool">
        <span class="last-pool-label">Current pool</span>
        <span class="last-pool-name">{{ pool.name }}</span>
        <span class="last-pool-meta">
          {{ pool.size }} cards
          <template v-if="pool.replacements">
            · {{ pool.replacements }} replacement{{ pool.replacements === 1 ? '' : 's' }}
          </template>
          <template v-if="generatedAt"> · generated {{ generatedAt }}</template>
        </span>
      </div>
      <p v-if="poolError" class="error-box">{{ poolError }}</p>
      <div class="home-actions">
        <template v-if="pool">
          <button type="button" class="btn-generate" @click="goToPool">Back to current pool</button>
          <button type="button" class="btn-restore" @click="confirmingNewPool = true">Generate a new pool</button>
        </template>
        <button v-else type="button" class="btn-generate" :disabled="!poolChecked" @click="goToGenerator">
          Generate a Pool
        </button>
      </div>
    </div>

    <Modal v-if="confirmingNewPool && pool" title="Erase the current pool?" size="small" @close="confirmingNewPool = false">
      <p class="confirm-text">
        Generating a new pool erases <strong>{{ pool.name }}</strong> ({{ pool.size }} cards<template
          v-if="pool.replacements"
          >, {{ pool.replacements }} replacement{{ pool.replacements === 1 ? '' : 's' }}</template
        >). Verdicts, rules and reports are kept.
      </p>
      <p class="confirm-hint">The current pool stays until the new one is generated.</p>
      <template #footer>
        <button type="button" class="btn" @click="confirmingNewPool = false">Cancel</button>
        <button type="button" class="btn danger" @click="goToGenerator">Erase and generate</button>
      </template>
    </Modal>

    <div v-if="reports.length" class="visualize-container reports">
      <h2>Analysis reports</h2>
      <ul class="report-list">
        <li v-for="r in reports" :key="r.id">
          <router-link :to="{ name: 'report', params: { id: r.id } }" class="report-link">
            <span class="report-title">{{ r.title }}</span>
            <span class="report-meta">
              {{ new Date(r.createdAt).toLocaleDateString() }}
              <span v-if="r.poolName"> · {{ r.poolName }}</span>
              · <span class="flagged">{{ r.summary.flagged }} to review</span>
              <span v-if="r.summary.rejected"> · <span class="rejected">{{ r.summary.rejected }} rejected</span></span>
            </span>
          </router-link>
          <button type="button" class="report-delete" title="Delete report" @click="deleteReport(r)">✕</button>
        </li>
      </ul>
    </div>

  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Modal from '../components/Modal.vue'
import { api, errorMessage, type PoolSummary } from '../api'
import { currentPoolSummary } from '../pool-store'
import type { ReportListItem } from '../analysis'

const title = 'Card Pool Generator'
const router = useRouter()

const pool = ref<PoolSummary | null>(null)
const poolChecked = ref(false)
const poolError = ref('')
const confirmingNewPool = ref(false)
const generatedAt = computed(() => {
  const date = pool.value?.generatedAt
  return date ? new Date(date).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' }) : ''
})
const reports = ref<ReportListItem[]>([])

onMounted(async () => {
  const [summary, reportList] = await Promise.allSettled([currentPoolSummary(), api.listReports()])
  if (summary.status === 'fulfilled') {
    pool.value = summary.value
  } else {
    poolError.value = `Could not check for a current pool: ${errorMessage(summary.reason)}`
  }
  poolChecked.value = true
  if (reportList.status === 'fulfilled') {
    reports.value = reportList.value
  } else {
    console.error('Error loading reports:', reportList.reason)
  }
})

const goToGenerator = () => router.push({ name: 'generator' })
const goToPool = () => router.push({ name: 'pool' })

const deleteReport = async (report: ReportListItem) => {
  if (!confirm(`Delete the report “${report.title}”? Its link will stop working.`)) return
  try {
    await api.deleteReport(report.id)
    reports.value = reports.value.filter((r) => r.id !== report.id)
  } catch (err) {
    console.error('Error deleting report:', err)
  }
}
</script>

<style scoped>
.main {
  flex-direction: column;
  gap: 2rem;
}

.home-container {
  background: var(--surface);
  padding: 3rem 2rem;
  border-radius: 12px;
  border: 1px solid var(--border);
  text-align: center;
  width: 100%;
  max-width: 500px;
}

.last-pool {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  background: var(--control);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  padding: 0.8rem 1rem;
  margin-bottom: 1rem;
}

.last-pool-label {
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-dim);
}

.last-pool-name {
  color: var(--text);
  font-weight: 500;
}

.last-pool-meta {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.tagline {
  margin-bottom: 2rem;
  color: var(--text-muted);
}

.home-actions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.btn-generate {
  background-color: var(--accent);
  color: #0a0a0a;
  padding: 0.75rem 1.5rem;
  font-weight: 600;
}

.btn-generate:hover {
  background-color: var(--accent-hover);
}

.btn-restore {
  background-color: var(--control);
  color: #cccccc;
  padding: 0.75rem 1.5rem;
  border: 1px solid var(--border-strong);
}

.btn-restore:hover {
  background-color: var(--control-hover);
}

.visualize-container {
  background: var(--surface);
  padding: 2rem;
  border-radius: 12px;
  border: 1px solid var(--border);
  width: 100%;
  max-width: 500px;
}

.visualize-container h2 {
  text-align: center;
  margin-bottom: 1.5rem;
  font-size: 1.25rem;
  color: #ffffff;
}






.confirm-text {
  color: var(--text);
  line-height: 1.5;
}

.confirm-hint {
  margin-top: 0.6rem;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.home-container .error-box {
  margin-bottom: 1rem;
}

.report-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.report-list li {
  display: flex;
  align-items: stretch;
  gap: 0.4rem;
}

.report-link {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  text-decoration: none;
  background: var(--control);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  padding: 0.6rem 0.8rem;
}

.report-link:hover {
  background: var(--control-hover);
}

.report-title {
  color: var(--text);
  font-weight: 500;
  font-size: 0.95rem;
}

.report-meta {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.report-meta .flagged { color: var(--warning); }
.report-meta .rejected { color: var(--danger); }

.report-delete {
  background: none;
  color: var(--text-dim);
  padding: 0 0.6rem;
}

.report-delete:hover {
  color: var(--danger);
}
</style>
