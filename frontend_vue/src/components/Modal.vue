<template>
  <Teleport to="body">
    <div class="modal-backdrop" @mousedown.self="$emit('close')">
      <div class="modal" :class="size" role="dialog" aria-modal="true" :aria-label="title">
        <header class="modal-header">
          <h3>{{ title }}</h3>
          <button type="button" class="modal-x" aria-label="Close" @click="$emit('close')">✕</button>
        </header>
        <div class="modal-body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="modal-footer">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'

withDefaults(defineProps<{ title: string; size?: 'small' | 'medium' | 'large' }>(), { size: 'medium' })
const emit = defineEmits<{ close: [] }>()

const onKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  z-index: 2000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 4vh 1rem;
  overflow-y: auto;
}

.modal {
  background: var(--surface-raised);
  border: 1px solid #2f2f2f;
  border-radius: 10px;
  width: 100%;
  display: flex;
  flex-direction: column;
  max-height: 92vh;
}

.modal.small { max-width: 460px; }
.modal.medium { max-width: 720px; }
.modal.large { max-width: 1200px; }

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.9rem 1.2rem;
  border-bottom: 1px solid var(--border);
}

.modal-header h3 {
  color: #fff;
  font-size: 1.05rem;
  font-weight: 600;
}

.modal-x {
  background: none;
  color: var(--text-muted);
  padding: 0.2rem 0.5rem;
  font-size: 1rem;
}

.modal-x:hover { color: #fff; }

.modal-body {
  padding: 1.2rem;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.9rem 1.2rem;
  border-top: 1px solid var(--border);
}
</style>
