import { createRouter, createWebHistory } from 'vue-router'
import Portfolio from '../views/Portfolio.vue'
import Dashboard from '../views/Dashboard.vue'
import Monitor from '../views/Monitor.vue'
import RouteDemo from '../views/RouteDemo.vue'

const routes = [
  { path: '/', component: Portfolio },
  { path: '/dashboard', component: Dashboard },
  { path: '/monitor', component: Monitor },
  { path: '/route', component: RouteDemo }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
