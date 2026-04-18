import { createRouter, createWebHistory } from 'vue-router'
import Portfolio from '../views/Portfolio.vue'
import Dashboard from '../views/Dashboard.vue'
import Monitor from '../views/Monitor.vue'
import RouteDemo from '../views/RouteDemo.vue'
import BlockchainDemo from '../views/BlockchainDemo.vue'
import UniV3Explorer from '../views/UniV3Explorer.vue'

const routes = [
  { path: '/', component: Dashboard },
  { path: '/portfolio', component: Portfolio },
  { path: '/dashboard', component: Dashboard },
  { path: '/monitor', component: Monitor },
  { path: '/route', component: RouteDemo },
  { path: '/blockchain', component: BlockchainDemo, meta: { title: 'Sepolia 演示' } },
  { path: '/univ3', component: UniV3Explorer, meta: { title: 'Uniswap V3 扫链数据' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
