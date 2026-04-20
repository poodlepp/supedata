import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Monitor from '../views/Monitor.vue'
import RouteDemo from '../views/RouteDemo.vue'
import BlockchainDemo from '../views/BlockchainDemo.vue'

const routes = [
  { path: '/', component: Dashboard },
  { path: '/portfolio', redirect: '/' },
  { path: '/dashboard', redirect: '/' },
  { path: '/monitor', component: Monitor },
  { path: '/route', component: RouteDemo },
  { path: '/blockchain', component: BlockchainDemo, meta: { title: '链上索引与事件' } },
  { path: '/univ3', redirect: '/blockchain' }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
