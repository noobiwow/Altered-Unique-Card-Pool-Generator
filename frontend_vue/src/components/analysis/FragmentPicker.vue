<template>
  <div ref="root" class="fragment-picker">
    <ul v-if="modelValue.length" class="chips">
      <li v-for="f in modelValue" :key="f.id" class="chip" :class="part" :title="`Fragment #${f.id}`">
        <span class="chip-text">{{ f.text }}</span>
        <span class="chip-id">#{{ f.id }}</span>
        <button type="button" class="chip-remove" :aria-label="`Remove ${f.text}`" @click="remove(f.id)">×</button>
      </li>
    </ul>

    <input
      ref="input"
      v-model="query"
      type="text"
      class="search"
      role="combobox"
      :aria-expanded="open"
      :aria-label="`Search ${part} fragments`"
      :placeholder="modelValue.length ? `Add another ${part}…` : `Search the ${part}s of the cards…`"
      @focus="open = true"
      @input="onInput"
      @keydown="onKeydown"
    />

    <ul v-if="open" class="options" role="listbox">
      <li v-if="loading" class="empty">Loading fragments…</li>
      <li v-else-if="error" class="empty">{{ error }}</li>
      <li v-else-if="!available" class="empty">
        No fragments: the card source has no fragment ids (CardsData CSV only).
      </li>
      <template v-else>
        <li
          v-for="(f, index) in options"
          :key="f.id"
          class="option"
          :class="{ 'is-highlighted': index === highlighted }"
          role="option"
          @mousedown.prevent="add(f)"
          @mousemove="highlighted = index"
        >
          <span class="option-text">{{ f.text }}</span>
          <span class="option-meta">#{{ f.id }} · {{ f.uses.toLocaleString() }}</span>
        </li>
        <li v-if="!options.length" class="empty">No {{ part }} matches “{{ query }}”.</li>
        <li v-else-if="more > 0" class="empty">{{ more }} more: refine the search.</li>
      </template>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, errorMessage } from '../../api'
import { withoutStaticMarker, type AbilityPart, type EffectFragment, type FragmentRef } from '../../analysis'

const MAX_OPTIONS = 40

const props = withDefaults(defineProps<{ part: AbilityPart; initialQuery?: string }>(), { initialQuery: '' })
const modelValue = defineModel<FragmentRef[]>({ required: true })

const root = ref<HTMLElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const query = ref(props.initialQuery)
const open = ref(false)
const highlighted = ref(0)
const loading = ref(true)
const error = ref('')
const all = ref<EffectFragment[]>([])

const PART_KEY = { trigger: 'TRIGGER', condition: 'CONDITION', effect: 'EFFECT' } as const

/** Case, whitespace (including non-breaking spaces) and static marker insensitive. */
const comparable = (text: string) => withoutStaticMarker(text).replace(/[\s ]+/g, ' ').toLowerCase()

const ofPart = computed(() => all.value.filter((f) => f.part === PART_KEY[props.part]))
const available = computed(() => ofPart.value.length > 0)

/** Matching fragments not chosen yet, most used first (as sent by the backend). */
const matching = computed(() => {
  const search = comparable(query.value.trim())
  const chosen = new Set(modelValue.value.map((f) => f.id))
  return ofPart.value.filter((f) => !chosen.has(f.id) && (!search || comparable(f.text).includes(search)))
})
const options = computed(() => matching.value.slice(0, MAX_OPTIONS))
const more = computed(() => matching.value.length - options.value.length)

const add = (f: EffectFragment) => {
  modelValue.value = [...modelValue.value, { id: f.id, text: f.text }]
  highlighted.value = Math.min(highlighted.value, Math.max(0, options.value.length - 1))
  input.value?.focus()
}

const remove = (fragmentId: number) => {
  modelValue.value = modelValue.value.filter((f) => f.id !== fragmentId)
}

const onInput = () => {
  open.value = true
  highlighted.value = 0
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    open.value = false
  } else if (event.key === 'ArrowDown') {
    event.preventDefault()
    open.value = true
    if (options.value.length) highlighted.value = (highlighted.value + 1) % options.value.length
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (options.value.length) highlighted.value = (highlighted.value - 1 + options.value.length) % options.value.length
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const f = options.value[highlighted.value]
    if (open.value && f) add(f)
  } else if (event.key === 'Backspace' && !query.value && modelValue.value.length) {
    remove(modelValue.value[modelValue.value.length - 1].id)
  }
}

const onDocumentPointerDown = (event: PointerEvent) => {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false
}

onMounted(async () => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  try {
    all.value = await api.fragments()
  } catch (err) {
    error.value = `Could not load the fragments: ${errorMessage(err)}`
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
</script>

<style scoped>
.fragment-picker {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.chips {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.chip {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  background: var(--control);
  font-size: 0.82rem;
  line-height: 1.35;
}

.chip-text { flex: 1; min-width: 0; color: var(--text); }
.chip.trigger .chip-text { color: var(--part-trigger); }
.chip.condition .chip-text { color: var(--part-condition); font-style: italic; }
.chip-id { color: var(--text-dim); font-size: 0.72rem; flex-shrink: 0; }

.chip-remove {
  padding: 0 0.15rem;
  border: none;
  background: none;
  color: var(--text-dim);
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
}

.chip-remove:hover { color: var(--danger); }

.search {
  width: 100%;
  padding: 0.4rem 0.5rem;
  font-size: 0.85rem;
}

.options {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 30;
  max-height: 16rem;
  margin-top: 0.25rem;
  padding: 0.3rem;
  list-style: none;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}

.option {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  padding: 0.4rem 0.55rem;
  border-radius: 4px;
  font-size: 0.83rem;
  color: var(--text);
  cursor: pointer;
  user-select: none;
}

.option.is-highlighted { background: var(--control); }
.option-text { flex: 1; min-width: 0; }
.option-meta { flex-shrink: 0; color: var(--text-dim); font-size: 0.72rem; }

.empty {
  padding: 0.45rem 0.55rem;
  font-size: 0.8rem;
  color: var(--text-dim);
}
</style>
