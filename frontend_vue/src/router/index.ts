import { createRouter, createWebHistory } from 'vue-router'
import Home from '../pages/Home.vue'
import Generator from '../pages/Generator.vue'
import Pool from '../pages/Pool.vue'

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
      path: '/pool',
      name: 'pool',
      component: Pool,
    },
  ],
})

export default router
