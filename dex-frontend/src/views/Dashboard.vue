<template>
  <div class="dashboard">
    <div class="hero">
      <div>
        <div class="eyebrow">DEX AGGREGATOR</div>
        <h1>链上数据聚合与报价演示台</h1>
        <p>前半阶段聚焦展示与验证：用更清晰的首页把价格、池子、阶段进度和功能入口串起来，方便开发、测试、部署后的快速验收。</p>
      </div>
      <div class="hero-badges">
        <span>Stage 0-4 Ready</span>
        <span>Vue 3 + Spring Boot</span>
      </div>
    </div>

    <section class="quick-grid">
      <router-link class="quick-card" to="/blockchain">
        <div class="eyebrow">CHAIN READ</div>
        <h2>主链监听</h2>
        <p>查看连接状态、最新区块、ETH/USDT 和 Swap 事件流。</p>
      </router-link>
      <router-link class="quick-card" to="/univ3">
        <div class="eyebrow">PROTOCOL INDEX</div>
        <h2>UniV3 索引</h2>
        <p>查看池子扫描进度、事件分布与最近事件。</p>
      </router-link>
      <router-link class="quick-card" to="/route">
        <div class="eyebrow">ROUTING</div>
        <h2>路径报价</h2>
        <p>查看候选路径、gas、滑点与淘汰原因。</p>
      </router-link>
      <router-link class="quick-card" to="/monitor">
        <div class="eyebrow">DELIVERY</div>
        <h2>阶段看板</h2>
        <p>对照架构文档第十部分查看落地进度。</p>
      </router-link>
    </section>

    <section class="grid-2">
      <article class="panel">
        <div class="section-head">
          <h2>价格快照</h2>
          <span class="muted">最近更新：{{ formatTime(lastUpdated) }}</span>
        </div>
        <div class="price-grid">
          <div v-for="price in prices" :key="price.pair" class="price-card">
            <span>{{ price.pair }}</span>
            <strong>${{ Number(price.price || 0).toFixed(2) }}</strong>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="section-head">
          <h2>阶段进度摘要</h2>
          <span class="muted">已完成 {{ doneStages }}/{{ totalStages }}</span>
        </div>
        <div class="stage-summary">
          <div v-for="stage in stagePreview" :key="stage.code" class="stage-pill" :class="stage.done ? 'done' : ''">
            <strong>{{ stage.code }}</strong>
            <span>{{ stage.name }}</span>
          </div>
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="section-head">
        <h2>流动性池</h2>
      </div>
      <el-table :data="pools" stripe>
        <el-table-column prop="poolAddress" label="池地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="token0" label="Token 0" width="120" />
        <el-table-column prop="token1" label="Token 1" width="120" />
        <el-table-column prop="reserve0" label="Reserve 0" width="140" />
        <el-table-column prop="reserve1" label="Reserve 1" width="140" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getLiquidity } from '../api/liquidity'
import { getPrices } from '../api/price'
import { getStageProgress } from '../api/statistics'

const prices = ref([])
const pools = ref([])
const progress = ref(null)
const lastUpdated = ref(null)

const totalStages = computed(() => progress.value?.stages?.length || 0)
const doneStages = computed(() => (progress.value?.stages || []).filter(item => item.done).length)
const stagePreview = computed(() => progress.value?.stages?.slice(0, 6) || [])

const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN') : '-'

const loadDashboard = async () => {
  const [priceRes, poolRes, progressRes] = await Promise.all([
    getPrices(),
    getLiquidity(),
    getStageProgress()
  ])
  prices.value = priceRes.data.data || []
  pools.value = poolRes.data.data || []
  progress.value = progressRes.data.data || null
  lastUpdated.value = Date.now()
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard{min-height:100vh;padding:24px;background:#f8fafc;display:flex;flex-direction:column;gap:18px}.hero,.panel,.quick-card{background:#fff;border-radius:24px;padding:24px;box-shadow:0 16px 40px rgba(15,23,42,.08)}.hero{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;background:linear-gradient(135deg,#0f172a 0%,#1d4ed8 55%,#38bdf8 100%);color:#fff}.hero h1{margin-top:10px;font-size:34px}.hero p{margin-top:12px;max-width:720px;line-height:1.8;color:rgba(255,255,255,.85)}.eyebrow{font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:#94a3b8}.hero .eyebrow{color:rgba(255,255,255,.68)}.hero-badges{display:flex;flex-direction:column;gap:10px}.hero-badges span{padding:10px 14px;border-radius:999px;background:rgba(255,255,255,.12)}.quick-grid,.grid-2,.price-grid,.stage-summary{display:grid;gap:18px}.quick-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.grid-2{grid-template-columns:repeat(2,minmax(0,1fr))}.price-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.quick-card{text-decoration:none;color:inherit;transition:transform .2s ease,box-shadow .2s ease}.quick-card:hover{transform:translateY(-4px);box-shadow:0 20px 40px rgba(37,99,235,.12)}.quick-card h2{margin-top:10px}.quick-card p{margin-top:10px;color:#475569;line-height:1.7}.section-head{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:18px}.muted{font-size:12px;color:#64748b}.price-card{padding:18px;border-radius:18px;background:#eff6ff}.price-card span{font-size:12px;color:#64748b}.price-card strong{display:block;margin-top:10px;font-size:28px}.stage-pill{padding:14px;border-radius:16px;background:#f1f5f9;display:flex;flex-direction:column;gap:6px}.stage-pill.done{background:#dcfce7}.stage-pill span{color:#475569}@media (max-width:1100px){.quick-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media (max-width:860px){.hero,.grid-2,.price-grid,.quick-grid{grid-template-columns:1fr;display:grid}.hero{display:flex;flex-direction:column}}
</style>
