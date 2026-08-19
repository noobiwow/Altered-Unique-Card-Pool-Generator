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
  background: #1e1e1e;
  color: #cccccc;
  padding: 0.5rem 1rem;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  cursor: pointer;
  z-index: 1100;
  font-weight: 500;
}

.btn-back:hover {
  background-color: #2a2a2a;
  color: #ffffff;
}
</style>
