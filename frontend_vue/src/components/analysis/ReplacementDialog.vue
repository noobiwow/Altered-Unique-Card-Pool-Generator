<template>
  <Modal :title="`Replace ${card.name || card.reference}`" size="large" @close="$emit('close')">
    <p class="muted intro">
      Candidates come from the same faction and set{{ filter ? ' and match the filters this pool was generated with' : ' with the same card type' }}.
      Cards already in the pool, rejected cards and cards with warnings that haven't been reviewed are left out.
    </p>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>Searching the {{ setLabel }} cards… this can take a little while.</p>
    </div>

    <p v-else-if="error" class="error-box">{{ error }}</p>

    <p v-else-if="!candidates.length" class="empty">No suitable replacement found.</p>

    <div v-else class="candidates">
      <div v-for="c in page" :key="c.reference" class="candidate">
        <p class="name">{{ c.name || c.reference }}</p>
        <CardStats :card="c" />
        <p class="ref">{{ c.reference }}</p>
        <CardText :card="c" class="text" />
        <button type="button" class="btn primary" @click="$emit('replace', c)">Use this card</button>
      </div>
    </div>

    <template #footer>
      <span v-if="candidates.length" class="muted pager">
        {{ offset + 1 }}–{{ Math.min(offset + PAGE, candidates.length) }} of {{ candidates.length }}
      </span>
      <button type="button" class="btn" :disabled="loading || candidates.length <= PAGE" @click="next">
        Show others
      </button>
      <button type="button" class="btn" :disabled="loading" @click="load">New draw</button>
      <button type="button" class="btn" @click="$emit('close')">Cancel</button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CardStats from './CardStats.vue'
import CardText from './CardText.vue'
import Modal from '../Modal.vue'
import { api, errorMessage } from '../../api'
import {
  type PoolCard,
} from '../../analysis'

const props = defineProps<{
  card: PoolCard
  poolReferences: string[]
  filter: Record<string, unknown> | null
}>()
defineEmits<{ close: []; replace: [card: PoolCard] }>()

const PAGE = 6
const loading = ref(false)
const error = ref('')
const candidates = ref<PoolCard[]>([])
const offset = ref(0)

const setLabel = computed(() => props.card.cardSet?.name ?? props.card.cardSet?.code ?? 'set')
const page = computed(() => candidates.value.slice(offset.value, offset.value + PAGE))

const next = () => {
  offset.value = offset.value + PAGE >= candidates.value.length ? 0 : offset.value + PAGE
}

const load = async () => {
  loading.value = true
  error.value = ''
  offset.value = 0
  try {
    candidates.value = await api.replacements(props.card, props.poolReferences, props.filter)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.intro { margin-bottom: 1rem; }
.empty { text-align: center; color: var(--text-dim); padding: 2rem 0; }
.pager { margin-right: auto; align-self: center; }

.candidates {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 1rem;
}

.candidate {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  align-items: stretch;
  padding: 0.6rem;
  border: 1px solid #262626;
  border-radius: 8px;
  background: var(--surface-sunken);
}

.text { flex: 1; }

.name { font-size: 0.9rem; font-weight: 600; color: #eee; }
.ref { font-family: monospace; font-size: 0.65rem; color: var(--text-dim); overflow-wrap: anywhere; }
</style>
