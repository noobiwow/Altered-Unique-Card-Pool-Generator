<template>
  <div>
    <h2>Exemple UpSet.js</h2>
  </div>
</template>

<script setup>

// Définition des ensembles
const sets = [
  { name: 'A' },
  { name: 'B' },
  { name: 'C' }
]

// Données : chaque entrée appartient à un ou plusieurs sets
const data = [
  { name: 'Item 1', sets: ['A'] },
  { name: 'Item 2', sets: ['A', 'B'] },
  { name: 'Item 3', sets: ['B'] },
  { name: 'Item 4', sets: ['A', 'C'] },
  { name: 'Item 5', sets: ['A', 'B', 'C'] }
]
</script>

<style scoped>
div {
  padding: 20px;
}
</style>