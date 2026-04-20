<template>
  <div class="dashboard">
    <section class="hero">
      <div class="hero-copy">
        <div class="eyebrow">OVERVIEW SNAPSHOT</div>
        <h1>首页只展示现在真实可用的数据。</h1>
        <p>
          聚合首页、报价、链上索引和交付看板四个核心入口。去掉重复导航和演示态信息，保留对当前可交付能力最有价值的概览。
        </p>
        <div class="hero-actions">
          <router-link class="hero-link" to="/blockchain">查看链上索引</router-link>
          <router-link class="hero-link ghost" to="/route">查看真实报价</router-link>
          <router-link class="hero-link ghost" to="/monitor">查看验收看板</router-link>
        </div>
      </div>

      <div class="hero-side">
        <div class="hero-stat">
          <span>快照时间</span>
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
        <span>Total TVL</span>
        <strong>{{ formatUsd(market.totalTvlUsdEstimate) }}</strong>
        <small>基于真实池状态估算</small>
      </article>
      <article class="summary-card">
        <span>Supported Pools</span>
        <strong>{{ market.poolCount || 0 }}</strong>
        <small>当前快速查询池</small>
      </article>
      <article class="summary-card">
        <span>Price Pairs</span>
        <strong>{{ market.pricePairCount || 0 }}</strong>
        <small>真实报价覆盖</small>
      </article>
      <article class="summary-card">
        <span>Latest Block</span>
        <strong>{{ market.latestBlockNumber || '-' }}</strong>
        <small>{{ market.syncStatus || 'BOOTSTRAPPING' }}</small>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">DELIVERY</div>
            <h2>交付摘要</h2>
          </div>
          <span class="muted">已完成 {{ stageSummary.doneStages || 0 }}/{{ stageSummary.totalStages || 0 }}</span>
        </div>

        <div class="delivery-grid">
          <div class="delivery-item">
            <span>阶段状态</span>
            <strong>{{ stageSummary.currentFocus || '-' }}</strong>
          </div>
          <div class="delivery-item">
            <span>Volume 边界</span>
            <strong>{{ volumeBoundary.supported ? '已开放' : '未开放' }}</strong>
          </div>
          <div class="delivery-item full">
            <span>当前说明</span>
            <p>{{ volumeBoundary.message || '暂无额外说明' }}</p>
          </div>
        </div>

        <div class="stage-summary">
          <div v-for="stage in stagePreview" :key="stage.code" class="stage-pill" :class="stage.done ? 'done' : ''">
            <strong>{{ stage.code }}</strong>
            <span>{{ stage.name }}</span>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">ACTIONS</div>
            <h2>核心入口</h2>
          </div>
        </div>

        <div class="action-list">
          <router-link v-for="action in actions" :key="action.to" class="action-card" :to="action.to">
            <span>{{ action.tag }}</span>
            <strong>{{ action.title }}</strong>
            <p>{{ action.description }}</p>
          </router-link>
        </div>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">PRICES</div>
            <h2>价格快照</h2>
          </div>
          <span class="muted">后台预热后直接读取</span>
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
          <div>
            <div class="eyebrow dark">POOLS</div>
            <h2>重点池子</h2>
          </div>
          <span class="muted">按估算 TVL 排序</span>
        </div>

        <div class="pool-list">
          <article v-for="pool in topPools.slice(0, 4)" :key="pool.poolAddress" class="pool-card">
            <div class="pool-top">
              <div>
                <span>{{ pool.pair }}</span>
                <h3>{{ pool.poolName }}</h3>
              </div>
              <div class="pool-fee">Fee {{ formatFee(pool.fee) }}</div>
            </div>
            <div class="pool-metrics">
              <div>
                <span>TVL</span>
                <strong>{{ formatUsd(pool.tvlUsdEstimate) }}</strong>
              </div>
              <div>
                <span>Block</span>
                <strong>{{ pool.blockNumber || '-' }}</strong>
              </div>
            </div>
          </article>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview } from '../api/statistics'

const overview = ref({})

const actions = [
  { to: '/blockchain', tag: 'CHAIN', title: '链上索引与事件', description: '查看 RPC 状态、扫链进度和 UniV3 事件分类。' },
  { to: '/route', tag: 'QUOTE', title: '真实报价', description: '验证路径、输出金额、gas 和滑点结果。' },
  { to: '/monitor', tag: 'TRACK', title: '交付看板', description: '查看阶段 0-4 的验收状态和当前边界。' }
]

const market = computed(() => overview.value?.market || {})
const prices = computed(() => overview.value?.prices || [])
const topPools = computed(() => overview.value?.topPools || [])
const stageSummary = computed(() => overview.value?.stageSummary || {})
const stagePreview = computed(() => stageSummary.value?.stagePreview || [])
const volumeBoundary = computed(() => overview.value?.volumeBoundary || {})

const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const formatUsd = (value) => value == null ? '-' : `$${Number(value).toLocaleString('en-US', { maximumFractionDigits: 2 })}`
const formatFee = (value) => value == null ? '-' : `${Number(value) / 10000}%`

const loadDashboard = async () => {
  const response = await getOverview()
  overview.value = response.data.data || {}
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard{min-height:100vh;padding:24px;background:radial-gradient(circle at top left,#fef3c7 0,#f8fafc 45%,#e0f2fe 100%);display:flex;flex-direction:column;gap:18px}
.hero,.panel,.summary-card,.action-card,.pool-card,.price-card{border-radius:24px;box-shadow:0 18px 48px rgba(15,23,42,.08)}
.hero{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(280px,.9fr);gap:20px;padding:28px;background:linear-gradient(135deg,#111827 0%,#0f766e 55%,#f59e0b 100%);color:#fff}
.hero-copy h1{margin-top:10px;font-size:40px;line-height:1.08;max-width:720px}
.hero-copy p{margin-top:14px;max-width:700px;line-height:1.8;color:rgba(255,255,255,.84)}
.hero-actions{display:flex;gap:12px;margin-top:18px;flex-wrap:wrap}
.hero-link{display:inline-flex;align-items:center;justify-content:center;padding:12px 16px;border-radius:999px;background:#fff;color:#0f172a;text-decoration:none;font-weight:600}
.hero-link.ghost{background:rgba(255,255,255,.14);color:#fff;border:1px solid rgba(255,255,255,.2)}
.hero-side{display:grid;gap:14px}
.hero-stat{padding:18px;border-radius:20px;background:rgba(15,23,42,.22);backdrop-filter:blur(12px)}
.hero-stat span,.eyebrow{font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:rgba(255,255,255,.68)}
.hero-stat strong{display:block;margin-top:10px;font-size:22px;line-height:1.35}
.eyebrow.dark{color:#64748b}
.summary-grid,.content-grid,.delivery-grid,.action-list,.price-grid,.pool-list,.stage-summary{display:grid;gap:18px}
.summary-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.summary-card,.panel{padding:24px;background:rgba(255,255,255,.9)}
.summary-card span,.delivery-item span,.action-card span,.price-card span,.pool-card span{font-size:12px;color:#64748b}
.summary-card strong{display:block;margin-top:10px;font-size:30px}
.summary-card small{display:block;margin-top:8px;color:#475569;line-height:1.7}
.summary-card.accent{background:linear-gradient(135deg,#0f172a 0%,#155e75 100%);color:#fff}
.summary-card.accent span,.summary-card.accent small{color:rgba(255,255,255,.74)}
.content-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.section-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:18px}
.section-head h2{margin-top:8px}
.muted{font-size:12px;color:#64748b}
.delivery-grid{grid-template-columns:repeat(2,minmax(0,1fr));margin-bottom:18px}
.delivery-item{padding:18px;border-radius:18px;background:#f8fafc}
.delivery-item strong{display:block;margin-top:8px;font-size:20px;color:#0f172a}
.delivery-item p{margin-top:8px;color:#475569;line-height:1.7}
.delivery-item.full{grid-column:1 / -1}
.stage-summary{grid-template-columns:repeat(2,minmax(0,1fr))}
.stage-pill{padding:14px;border-radius:16px;background:#f1f5f9;display:flex;flex-direction:column;gap:6px}
.stage-pill.done{background:#dcfce7}
.stage-pill span{color:#475569}
.action-list{grid-template-columns:1fr}
.action-card{padding:18px 20px;background:linear-gradient(135deg,#fff7ed 0%,#eff6ff 100%);text-decoration:none;color:inherit;transition:transform .2s ease,box-shadow .2s ease}
.action-card:hover{transform:translateY(-3px);box-shadow:0 18px 36px rgba(14,116,144,.16)}
.action-card strong{display:block;margin-top:10px;font-size:22px;color:#0f172a}
.action-card p{margin-top:8px;color:#475569;line-height:1.7}
.price-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.price-card{padding:18px;background:linear-gradient(135deg,#fff7ed 0%,#eff6ff 100%)}
.price-card strong{display:block;margin-top:10px;font-size:28px;color:#0f172a}
.price-card small{display:block;margin-top:8px;color:#64748b}
.pool-list{grid-template-columns:1fr}
.pool-card{padding:20px;background:linear-gradient(135deg,#0f172a 0%,#1e293b 100%);color:#fff}
.pool-top{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}
.pool-top h3{margin-top:10px;font-size:20px}
.pool-fee{padding:8px 12px;border-radius:999px;background:rgba(255,255,255,.12);white-space:nowrap}
.pool-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:18px}
.pool-metrics strong{display:block;margin-top:8px;font-size:20px}
@media (max-width:1180px){.summary-grid,.content-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media (max-width:860px){.hero,.summary-grid,.content-grid,.price-grid,.stage-summary,.delivery-grid,.pool-metrics{grid-template-columns:1fr}.hero-copy h1{font-size:32px}}
</style>
