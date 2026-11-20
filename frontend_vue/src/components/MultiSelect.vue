<template>
  <div ref="root" class="multi-select">
    <div
      class="multi-select-control"
      :class="{ 'is-open': open }"
      @click="focusInput"
    >
      <span
        v-for="value in modelValue"
        :key="value"
        class="multi-select-chip"
      >
        {{ value }}
        <button
          type="button"
          class="multi-select-chip-remove"
          :aria-label="`Remove ${value}`"
          @click.stop="remove(value)"
        >
          ×
        </button>
      </span>

      <input
        :id="inputId"
        ref="input"
        v-model="query"
        type="text"
        class="multi-select-input"
        role="combobox"
        :aria-expanded="open"
        aria-autocomplete="list"
        :placeholder="modelValue.length === 0 ? placeholder : ''"
        @focus="onFocus"
        @input="onInput"
        @keydown="onKeydown"
      />
    </div>

    <ul v-if="open" class="multi-select-options" role="listbox" aria-multiselectable="true">
      <li
        v-for="(option, index) in filteredOptions"
        :key="option"
        class="multi-select-option"
        :class="{ 'is-highlighted': index === highlightedIndex, 'is-selected': isSelected(option) }"
        role="option"
        :aria-selected="isSelected(option)"
        @mousedown.prevent="toggle(option)"
        @mousemove="highlightedIndex = index"
      >
        <span class="multi-select-check">{{ isSelected(option) ? '✓' : '' }}</span>
        <span>{{ option }}</span>
      </li>

      <li v-if="filteredOptions.length === 0" class="multi-select-empty">
        No matching sub types
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string[]
    options: string[]
    placeholder?: string
    inputId?: string
  }>(),
  { placeholder: 'Search...' }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()

const root = ref<HTMLElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const open = ref(false)
const query = ref('')
const highlightedIndex = ref(0)

const filteredOptions = computed(() => {
  const search = query.value.trim().toLowerCase()
  if (!search) return props.options
  return props.options.filter((option) => option.toLowerCase().includes(search))
})

const isSelected = (option: string) => props.modelValue.includes(option)

const focusInput = () => {
  input.value?.focus()
}

const onFocus = () => {
  open.value = true
}

const onInput = () => {
  open.value = true
  highlightedIndex.value = 0
}

const toggle = (option: string) => {
  const next = isSelected(option)
    ? props.modelValue.filter((value) => value !== option)
    : [...props.modelValue, option]
  emit('update:modelValue', next)
  query.value = ''
  highlightedIndex.value = 0
  focusInput()
}

const remove = (option: string) => {
  emit('update:modelValue', props.modelValue.filter((value) => value !== option))
  focusInput()
}

const close = () => {
  open.value = false
  query.value = ''
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    close()
    return
  }

  if (event.key === 'Backspace' && query.value === '' && props.modelValue.length > 0) {
    remove(props.modelValue[props.modelValue.length - 1])
    return
  }

  if (!open.value) {
    if (event.key === 'ArrowDown' || event.key === 'Enter') {
      open.value = true
    }
    return
  }

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (filteredOptions.value.length > 0) {
      highlightedIndex.value = (highlightedIndex.value + 1) % filteredOptions.value.length
    }
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (filteredOptions.value.length > 0) {
      highlightedIndex.value =
        (highlightedIndex.value - 1 + filteredOptions.value.length) % filteredOptions.value.length
    }
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const option = filteredOptions.value[highlightedIndex.value]
    if (option) toggle(option)
  }
}

const onDocumentPointerDown = (event: PointerEvent) => {
  if (root.value && !root.value.contains(event.target as Node)) {
    close()
  }
}

watch(filteredOptions, () => {
  if (highlightedIndex.value >= filteredOptions.value.length) {
    highlightedIndex.value = 0
  }
})

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
})
</script>

<style scoped>
.multi-select {
  position: relative;
}

.multi-select-control {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  width: 100%;
  min-height: 2.65rem;
  padding: 0.4rem 0.75rem;
  border: 1px solid var(--border);
  border-radius: 6px;
  background-color: var(--surface-sunken);
  cursor: text;
  transition: border-color 0.2s;
}

.multi-select-control.is-open {
  border-color: var(--accent);
}

.multi-select-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.4rem;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  background-color: var(--control);
  color: var(--text);
  font-size: 0.8rem;
  line-height: 1.4;
}

.multi-select-chip-remove {
  padding: 0;
  border: none;
  background: none;
  color: #999999;
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
}

.multi-select-chip-remove:hover {
  color: var(--accent);
}

.multi-select-input {
  flex: 1;
  min-width: 7rem;
  width: auto;
  padding: 0.2rem 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 0.95rem;
}

.multi-select-input:focus {
  border: none;
}

.multi-select-input::placeholder {
  color: var(--text-dim);
}

.multi-select-options {
  position: absolute;
  top: calc(100% + 0.3rem);
  left: 0;
  right: 0;
  z-index: 20;
  max-height: 14rem;
  margin: 0;
  padding: 0.3rem;
  list-style: none;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
  background-color: var(--surface);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}

.multi-select-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.45rem 0.6rem;
  border-radius: 4px;
  font-size: 0.9rem;
  color: #cccccc;
  cursor: pointer;
  user-select: none;
}

.multi-select-option.is-highlighted {
  background-color: var(--control);
  color: #ffffff;
}

.multi-select-option.is-selected {
  color: var(--accent);
}

.multi-select-check {
  width: 1rem;
  color: var(--accent);
  text-align: center;
}

.multi-select-empty {
  padding: 0.5rem 0.6rem;
  font-size: 0.85rem;
  color: #777777;
}
</style>
