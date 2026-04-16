import { createRouter, createWebHistory } from 'vue-router'
import Portfolio from '../views/Portfolio.vue'
import Dashboard from '../views/Dashboard.vue'
import Monitor from '../views/Monitor.vue'
import RouteDemo from '../views/RouteDemo.vue'
import BlockchainDemo from '../views/BlockchainDemo.vue'

const routes = [
  { path: '/', component: Dashboard },
  { path: '/portfolio', component: Portfolio },
  { path: '/dashboard', component: Dashboard },
  { path: '/monitor', component: Monitor },
  { path: '/route', component: RouteDemo },
  { path: '/blockchain', component: BlockchainDemo, meta: { title: 'Sepolia 演示' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
