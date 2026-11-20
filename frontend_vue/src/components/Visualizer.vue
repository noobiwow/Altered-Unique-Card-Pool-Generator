<template>
  <div>
    <div class="grid">
      <article v-for="card in pageItems" :key="card.reference" class="card">
        <header>
          <p class="name">{{ card.name || card.reference }}</p>
          <p class="facts">
            <span>{{ card.mainFaction?.name ?? factionName(card.mainFaction?.code) }}</span>
            <span v-if="card.cardType?.name?.en">{{ card.cardType.name.en }}</span>
          </p>
          <CardStats :card="card" />
          <p class="ref">{{ card.reference }}</p>
        </header>
        <CardText :card="card" />
      </article>
    </div>
    <Pagination v-model="page" :pages="pages" />
  </div>
</template>

<script setup lang="ts">
import { toRef } from 'vue'
import Pagination from './Pagination.vue'
import CardStats from './analysis/CardStats.vue'
import CardText from './analysis/CardText.vue'
import { usePagination } from '../composables/usePagination'
import { factionName, type PoolCard } from '../analysis'

const props = defineProps<{ cards: PoolCard[] }>()
const { page, pages, pageItems } = usePagination(toRef(props, 'cards'), 30)
</script>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  padding: 3rem 16px 16px;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.7rem;
  border: 1px solid #262626;
  border-radius: 8px;
  background: var(--surface-sunken);
}

.name { font-size: 0.95rem; font-weight: 600; color: #eee; }
.facts { display: flex; flex-wrap: wrap; gap: 0.5rem; font-size: 0.72rem; color: #999; }
.ref { font-family: monospace; font-size: 0.65rem; color: var(--text-dim); overflow-wrap: anywhere; }
</style>
