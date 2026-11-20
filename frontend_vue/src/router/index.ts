import { createRouter, createWebHistory } from 'vue-router'
import Home from '../pages/Home.vue'
import Generator from '../pages/Generator.vue'
import Pool from '../pages/Pool.vue'
import Report from '../pages/Report.vue'
import { loadCurrentPool } from '../pool-store'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: Home,
    },
    {
      path: '/generate',
      name: 'generator',
      component: Generator,
    },
    {
      // The current pool (saved by the backend).
      path: '/pool',
      name: 'pool',
      component: Pool,
      // No pool yet: generate one. When the backend cannot be reached, the
      // pool page shows the error.
      beforeEnter: () => loadCurrentPool().then((pool) => (pool ? true : { name: 'generator' }), () => true),
    },
    {
      path: '/report/:id',
      name: 'report',
      component: Report,
    },
  ],
})

export default router
