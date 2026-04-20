<template>
  <div class="dashboard">
    <section class="hero">
      <div class="hero-copy">
        <div class="eyebrow">LIVE MARKET SNAPSHOT</div>
        <h1>真实池状态先更新，再给前端秒级返回。</h1>
        <p>首页只读取后台预热好的总览快照，集中展示主价格、池子深度、阶段进度和当前数据边界，不把重计算压到查询链路上。</p>
        <div class="hero-actions">
          <router-link class="hero-link" to="/route">查看路由报价</router-link>
          <router-link class="hero-link ghost" to="/monitor">查看交付看板</router-link>
        </div>
      </div>

      <div class="hero-side">
        <div class="hero-stat">
          <span>快照更新时间</span>
          <strong>{{ formatTime(overview.generatedAt) }}</strong>
        </div>
        <div class="hero-stat">
          <span>缓存策略</span>
          <strong>{{ overview.cache?.strategy || '-' }}</strong>
        </div>
        <div class="hero-stat">
          <span>当前 Focus</span>
          <strong>{{ stageSummary.currentFocus || '-' }}</strong>
        </div>
      </div>
    </section>

    <section class="summary-grid">
      <article class="summary-card accent">
        <span>总 TVL</span>
        <strong>{{ formatUsd(market.totalTvlUsdEstimate) }}</strong>
        <small>真实池状态估算</small>
      </article>
      <article class="summary-card">
        <span>支持池子</span>
        <strong>{{ market.poolCount || 0 }}</strong>
        <small>可快速查询的主网池</small>
      </article>
      <article class="summary-card">
        <span>报价覆盖</span>
        <strong>{{ market.pricePairCount || 0 }}</strong>
        <small>真实交易对</small>
      </article>
      <article class="summary-card">
        <span>最新块高</span>
        <strong>{{ market.latestBlockNumber || '-' }}</strong>
        <small>{{ market.syncStatus || 'BOOTSTRAPPING' }}</small>
      </article>
    </section>

    <section class="highlight-grid">
      <article v-for="item in highlights" :key="item.title" class="highlight-card">
        <span>{{ item.title }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.description }}</p>
      </article>
    </section>

    <section class="quick-grid">
      <router-link class="quick-card" to="/blockchain">
        <div class="eyebrow">CHAIN READ</div>
        <h2>主链状态</h2>
        <p>查看连接状态、最新区块和链上监听结果。</p>
      </router-link>
      <router-link class="quick-card" to="/univ3">
        <div class="eyebrow">INDEX</div>
        <h2>UniV3 索引</h2>
        <p>查看扫描进度、事件分布和池子同步状态。</p>
      </router-link>
      <router-link class="quick-card" to="/route">
        <div class="eyebrow">ROUTING</div>
        <h2>真实报价</h2>
        <p>对比路径、gas 和滑点，验证报价来源。</p>
      </router-link>
      <router-link class="quick-card" to="/monitor">
        <div class="eyebrow">DELIVERY</div>
        <h2>交付看板</h2>
        <p>查看阶段结果、边界和真实交付状态。</p>
      </router-link>
    </section>

    <section class="grid-2">
      <article class="panel">
        <div class="section-head">
          <h2>价格快照</h2>
          <span class="muted">只读快照结果</span>
        </div>
        <div class="price-grid">
          <div v-for="price in prices" :key="price.pair" class="price-card">
            <span>{{ price.pair }}</span>
            <strong>{{ formatUsd(price.price) }}</strong>
            <small>{{ formatTime(price.timestamp) }}</small>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="section-head">
          <h2>阶段摘要</h2>
          <span class="muted">已完成 {{ stageSummary.doneStages || 0 }}/{{ stageSummary.totalStages || 0 }}</span>
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
        <h2>重点池子</h2>
        <span class="muted">按估算 TVL 排序</span>
      </div>
      <div class="pool-grid">
        <article v-for="pool in topPools" :key="pool.poolAddress" class="pool-card">
          <div class="pool-top">
            <div>
              <span class="eyebrow">{{ pool.dex }}</span>
              <h3>{{ pool.poolName }}</h3>
            </div>
            <div class="pool-fee">Fee {{ formatFee(pool.fee) }}</div>
          </div>
          <div class="pool-metrics">
            <div>
              <span>Pair</span>
              <strong>{{ pool.pair }}</strong>
            </div>
            <div>
              <span>TVL</span>
              <strong>{{ formatUsd(pool.tvlUsdEstimate) }}</strong>
            </div>
            <div>
              <span>Price</span>
              <strong>{{ Number(pool.priceToken0InToken1 || 0).toFixed(4) }}</strong>
            </div>
            <div>
              <span>Block</span>
              <strong>{{ pool.blockNumber }}</strong>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview } from '../api/statistics'

const overview = ref({})

const market = computed(() => overview.value?.market || {})
const prices = computed(() => overview.value?.prices || [])
const highlights = computed(() => overview.value?.highlights || [])
const topPools = computed(() => overview.value?.topPools || [])
const stageSummary = computed(() => overview.value?.stageSummary || {})
const stagePreview = computed(() => stageSummary.value?.stagePreview || [])

const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN') : '-'
const formatUsd = (value) => value == null ? '-' : `$${Number(value).toLocaleString('en-US', { maximumFractionDigits: 2 })}`
const formatFee = (value) => value == null ? '-' : `${Number(value) / 10000}%`

const loadDashboard = async () => {
  const response = await getOverview()
  overview.value = response.data.data || {}
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard{min-height:100vh;padding:24px;background:radial-gradient(circle at top left,#fff6db 0,#f6fbff 42%,#eef3ff 100%);display:flex;flex-direction:column;gap:18px}
.hero,.panel,.quick-card,.summary-card,.highlight-card{border-radius:24px;padding:24px;box-shadow:0 18px 48px rgba(15,23,42,.08)}
.hero{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(280px,.9fr);gap:20px;background:linear-gradient(135deg,#1f2937 0%,#0f766e 52%,#f59e0b 100%);color:#fff}
.hero-copy h1{margin-top:10px;font-size:40px;line-height:1.08;max-width:760px}
.hero-copy p{margin-top:14px;max-width:720px;line-height:1.8;color:rgba(255,255,255,.85)}
.hero-actions{display:flex;gap:12px;margin-top:18px;flex-wrap:wrap}
.hero-link{display:inline-flex;align-items:center;justify-content:center;padding:12px 16px;border-radius:999px;background:#fff;color:#0f172a;text-decoration:none;font-weight:600}
.hero-link.ghost{background:rgba(255,255,255,.14);color:#fff;border:1px solid rgba(255,255,255,.2)}
.hero-side{display:grid;gap:14px}
.hero-stat{padding:18px;border-radius:20px;background:rgba(15,23,42,.22);backdrop-filter:blur(12px)}
.hero-stat span,.eyebrow{font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:#94a3b8}
.hero .eyebrow,.hero-stat span{color:rgba(255,255,255,.68)}
.hero-stat strong{display:block;margin-top:10px;font-size:22px;line-height:1.35}
.summary-grid,.highlight-grid,.quick-grid,.grid-2,.price-grid,.stage-summary,.pool-grid{display:grid;gap:18px}
.summary-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.summary-card,.highlight-card,.panel,.quick-card{background:rgba(255,255,255,.92)}
.summary-card span,.highlight-card span,.price-card span,.pool-metrics span{font-size:12px;color:#64748b}
.summary-card strong,.highlight-card strong{display:block;margin-top:10px;font-size:30px}
.summary-card small,.highlight-card p{display:block;margin-top:8px;color:#475569;line-height:1.7}
.summary-card.accent{background:linear-gradient(135deg,#0f172a 0%,#155e75 100%);color:#fff}
.summary-card.accent span,.summary-card.accent small{color:rgba(255,255,255,.72)}
.highlight-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.quick-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.grid-2{grid-template-columns:repeat(2,minmax(0,1fr))}
.price-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.quick-card{text-decoration:none;color:inherit;transition:transform .2s ease,box-shadow .2s ease}
.quick-card:hover{transform:translateY(-4px);box-shadow:0 20px 40px rgba(8,145,178,.16)}
.quick-card h2{margin-top:10px}
.quick-card p{margin-top:10px;color:#475569;line-height:1.7}
.section-head{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:18px}
.muted{font-size:12px;color:#64748b}
.price-card{padding:18px;border-radius:18px;background:linear-gradient(135deg,#fff7ed 0%,#eff6ff 100%)}
.price-card strong{display:block;margin-top:10px;font-size:28px}
.price-card small{display:block;margin-top:8px;color:#64748b}
.stage-pill{padding:14px;border-radius:16px;background:#f1f5f9;display:flex;flex-direction:column;gap:6px}
.stage-pill.done{background:#dcfce7}
.stage-pill span{color:#475569}
.pool-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.pool-card{padding:22px;border-radius:22px;background:linear-gradient(135deg,#0f172a 0%,#1e293b 100%);color:#fff}
.pool-top{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}
.pool-top h3{margin-top:10px;font-size:22px}
.pool-fee{padding:8px 12px;border-radius:999px;background:rgba(255,255,255,.12);white-space:nowrap}
.pool-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:18px}
.pool-metrics strong{display:block;margin-top:8px;font-size:22px}
@media (max-width:1180px){.summary-grid,.highlight-grid,.quick-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.pool-grid{grid-template-columns:1fr}}
@media (max-width:860px){.hero,.grid-2,.price-grid,.summary-grid,.highlight-grid,.quick-grid,.pool-grid{grid-template-columns:1fr}.hero-copy h1{font-size:32px}}
</style>
