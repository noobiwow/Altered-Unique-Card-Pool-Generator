<template>
  <span class="legend">
    <span v-for="part in ABILITY_PARTS" :key="part" class="part" :class="part">{{ PART_LABELS[part] }}</span>
  </span>
</template>

<script setup lang="ts">
import { ABILITY_PARTS, PART_LABELS } from '../../analysis'
</script>

<style scoped>
.legend { display: inline-flex; gap: 0.6rem; font-size: 0.7rem; text-transform: none; letter-spacing: 0; }
.part.trigger { color: var(--part-trigger); font-weight: 600; }
.part.condition { color: var(--part-condition); font-style: italic; }
.part.effect { color: var(--text); }
</style>
