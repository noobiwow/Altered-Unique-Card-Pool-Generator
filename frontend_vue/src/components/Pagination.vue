<template>
  <nav v-if="pages > 1" class="pagination" aria-label="Pagination">
    <button type="button" class="btn" :disabled="page <= 1" @click="page--">Prev</button>
    <span class="page-info">Page {{ page }} / {{ pages }}</span>
    <button type="button" class="btn" :disabled="page >= pages" @click="page++">Next</button>
  </nav>
</template>

<script setup lang="ts">
defineProps<{ pages: number }>()
const page = defineModel<number>({ required: true })
</script>

<style scoped>
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 1rem;
}

.page-info {
  color: var(--text-muted);
  font-size: 0.85rem;
}
</style>
