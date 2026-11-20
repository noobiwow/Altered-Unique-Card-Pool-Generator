<template>
  <button
    v-if="$route.name !== 'home'"
    type="button"
    class="btn-back"
    @click="$router.push({ name: 'home' })"
  >
    Back to Home
  </button>
  <router-view :key="$route.fullPath" />
</template>

<style>
.btn-back {
  position: fixed;
  top: 1rem;
  left: 1rem;
  background: var(--surface);
  color: #cccccc;
  padding: 0.5rem 1rem;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  z-index: 1100;
  font-weight: 500;
}

.btn-back:hover {
  background-color: var(--control);
  color: #ffffff;
}
</style>
