<template>
  <Modal title="Create a report for the team" size="small" @close="$emit('close')">
    <div class="form">
      <div class="field">
        <label for="report-title">Title</label>
        <input id="report-title" v-model="title" type="text" />
      </div>
      <div class="field">
        <label for="report-notes">Notes for the team</label>
        <textarea id="report-notes" v-model="notes" rows="3" placeholder="Context, questions to discuss…" />
      </div>
      <label class="inline-check">
        <input v-model="includeCleared" type="checkbox" />
        Also include already approved cards ({{ statusCounts.CLEARED }})
      </label>
      <p class="muted">
        {{ statusCounts.FLAGGED }} to review and {{ statusCounts.REJECTED }} rejected card(s) will be included<span
          v-if="replacements.length">, plus {{ replacements.length }} replacement(s)</span>.
        The report is a snapshot: later changes won't alter it.
      </p>
      <p v-if="error" class="error-box">{{ error }}</p>
    </div>
    <template #footer>
      <button type="button" class="btn" @click="$emit('close')">Cancel</button>
      <button type="button" class="btn primary" :disabled="creating" @click="create">
        {{ creating ? 'Creating…' : 'Create & open' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Modal from '../Modal.vue'
import { api, errorMessage } from '../../api'
import type { PoolCard, Replacement, Report } from '../../analysis'

const props = defineProps<{
  pool: PoolCard[]
  poolName: string
  replacements: Replacement[]
  statusCounts: { FLAGGED: number; CLEARED: number; REJECTED: number }
}>()
const emit = defineEmits<{ close: []; created: [report: Report] }>()

const title = ref(`${props.poolName} review – ${new Date().toLocaleDateString()}`)
const notes = ref('')
const includeCleared = ref(false)
const creating = ref(false)
const error = ref('')

const create = async () => {
  creating.value = true
  error.value = ''
  try {
    const report = await api.createReport({
      title: title.value,
      poolName: props.poolName,
      notes: notes.value,
      cards: props.pool,
      replacements: props.replacements,
      includeCleared: includeCleared.value,
    })
    emit('created', report)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.form { display: flex; flex-direction: column; gap: 0.9rem; }
</style>
