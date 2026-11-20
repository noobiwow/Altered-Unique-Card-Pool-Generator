<template>
  <Modal :title="`Team decision · ${card.name || card.reference}`" size="small" @close="$emit('close')">
    <div class="verdict">
      <div class="choices">
        <label class="choice approve" :class="{ on: status === 'APPROVED' }">
          <input type="radio" value="APPROVED" v-model="status" />
          <strong>Approve</strong>
          <span>Keep it in pools. It won't be flagged again unless a new rule matches it.</span>
        </label>
        <label class="choice reject" :class="{ on: status === 'REJECTED' }">
          <input type="radio" value="REJECTED" v-model="status" />
          <strong>Reject</strong>
          <span>Exclude it from future generated pools.</span>
        </label>
      </div>

      <div v-if="warnings.length" class="covered">
        <p class="muted">Rules covered by this decision:</p>
        <ul>
          <li v-for="w in warnings" :key="w.ruleId"><span class="sev" :class="w.severity">{{ w.severity }}</span> {{ w.label }}</li>
        </ul>
      </div>

      <div class="field">
        <label for="verdict-note">Note</label>
        <textarea id="verdict-note" v-model="note" rows="3" placeholder="e.g. Discussed on 19/11, fine without Reserve support" />
      </div>

      <p v-if="error" class="error-box">{{ error }}</p>
    </div>

    <template #footer>
      <button v-if="analysis?.verdict" type="button" class="btn" :disabled="saving" @click="reset">
        Back to pending
      </button>
      <button type="button" class="btn" @click="$emit('close')">Cancel</button>
      <button type="button" class="btn primary" :disabled="saving" @click="save">
        {{ saving ? 'Saving…' : 'Save decision' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Modal from '../Modal.vue'
import { api, errorMessage } from '../../api'
import {
  type CardAnalysis,
  type PoolCard,
  type VerdictStatus,
} from '../../analysis'

const props = defineProps<{
  card: PoolCard
  analysis?: CardAnalysis | null
  initialStatus?: VerdictStatus
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const status = ref<VerdictStatus>(props.initialStatus ?? props.analysis?.verdict?.status ?? 'APPROVED')
const note = ref(props.analysis?.verdict?.note ?? '')
const saving = ref(false)
const error = ref('')
const warnings = computed(() => props.analysis?.warnings ?? [])

const save = async () => {
  saving.value = true
  error.value = ''
  try {
    await api.setVerdict(props.card.reference, {
      name: props.card.name,
      status: status.value,
      note: note.value,
      reviewedRuleIds: warnings.value.map((w) => w.ruleId),
    })
    emit('saved')
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    saving.value = false
  }
}

const reset = async () => {
  saving.value = true
  error.value = ''
  try {
    await api.clearVerdict(props.card.reference)
    emit('saved')
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.verdict {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.choices {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.choice {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  text-transform: none;
  letter-spacing: 0;
  margin: 0;
  border: 1px solid #2f2f2f;
  border-radius: 8px;
  padding: 0.6rem 0.8rem;
  cursor: pointer;
}

.choice input { display: none; }
.choice strong { font-size: 0.95rem; color: #ddd; }
.choice span { font-size: 0.8rem; color: var(--text-muted); }
.choice.approve.on { border-color: #3a8a5a; background: #102419; }
.choice.approve.on strong { color: var(--success); }
.choice.reject.on { border-color: #8a3a44; background: #241014; }
.choice.reject.on strong { color: var(--danger); }

.covered ul {
  list-style: none;
  margin-top: 0.3rem;
  font-size: 0.82rem;
  color: #ccc;
}

.covered li + li { margin-top: 0.25rem; }
</style>
