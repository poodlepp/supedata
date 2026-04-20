<template>
  <div class="monitor-page">
    <section class="hero">
      <div>
        <div class="eyebrow">DELIVERY TRACKER</div>
        <h1>分阶段交付验收看板</h1>
        <p>只保留阶段结果、真实数据覆盖和当前实现边界，不再展示原始 JSON 调试内容。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
    </section>

    <section class="stats-grid">
      <article class="stat-card">
        <span>阶段总数</span>
        <strong>{{ stages.length }}</strong>
      </article>
      <article class="stat-card">
        <span>已完成</span>
        <strong>{{ doneCount }}</strong>
      </article>
      <article class="stat-card">
        <span>Pool Count</span>
        <strong>{{ market.poolCount || 0 }}</strong>
      </article>
      <article class="stat-card">
        <span>Price Pairs</span>
        <strong>{{ market.pricePairCount || 0 }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <div class="eyebrow dark">STAGES</div>
          <h2>阶段进度</h2>
        </div>
      </div>
      <div class="stage-list">
        <article v-for="stage in stages" :key="stage.code" class="stage-card">
          <div class="stage-top">
            <div>
              <div class="stage-code">{{ stage.code }}</div>
              <h3>{{ stage.name }}</h3>
            </div>
            <el-tag :type="stage.done ? 'success' : 'info'">{{ stage.done ? '已完成/已验收' : '后续阶段' }}</el-tag>
          </div>
          <ul>
            <li v-for="item in stage.deliverables" :key="item">{{ item }}</li>
          </ul>
        </article>
      </div>
    </section>

    <section class="grid">
      <article class="panel mini-panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">SNAPSHOT</div>
            <h2>真实数据概览</h2>
          </div>
        </div>

        <div class="mini-grid">
          <div class="mini-card">
            <span>Sync Status</span>
            <strong>{{ market.syncStatus || '-' }}</strong>
          </div>
          <div class="mini-card">
            <span>Latest Block</span>
            <strong>{{ market.latestBlockNumber || '-' }}</strong>
          </div>
          <div class="mini-card">
            <span>Total TVL</span>
            <strong>{{ formatUsd(market.totalTvlUsdEstimate) }}</strong>
          </div>
          <div class="mini-card">
            <span>Snapshot Time</span>
            <strong>{{ formatTime(overview.generatedAt) }}</strong>
          </div>
        </div>
      </article>

      <article class="panel mini-panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">BOUNDARY</div>
            <h2>当前统计边界</h2>
          </div>
        </div>

        <div class="boundary-card">
          <span>{{ volume.pair || 'ALL' }} / {{ volume.period || '24h' }}</span>
          <strong>{{ volume.supported ? '已支持' : '暂未支持' }}</strong>
          <p>{{ volume.message || '-' }}</p>
          <small>{{ volume.methodology || '-' }}</small>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview, getStageProgress, getVolume } from '../api/statistics'

const loading = ref(false)
const progress = ref(null)
const overview = ref({})
const volume = ref({})

const stages = computed(() => progress.value?.stages || [])
const doneCount = computed(() => stages.value.filter(item => item.done).length)
const market = computed(() => overview.value?.market || {})

const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const formatUsd = (value) => value == null ? '-' : `$${Number(value).toLocaleString('en-US', { maximumFractionDigits: 2 })}`

const loadAll = async () => {
  loading.value = true
  try {
    const [stageRes, overviewRes, volumeRes] = await Promise.all([
      getStageProgress(),
      getOverview(),
      getVolume('ETH-USDC', '24h')
    ])
    progress.value = stageRes.data.data
    overview.value = overviewRes.data.data || {}
    volume.value = volumeRes.data.data || {}
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.monitor-page{min-height:100vh;padding:24px;background:linear-gradient(180deg,#eff6ff 0%,#f8fafc 100%);display:flex;flex-direction:column;gap:18px}
.hero,.panel,.stat-card,.mini-card,.boundary-card{background:#fff;border-radius:20px;padding:24px;box-shadow:0 16px 40px rgba(15,23,42,.08)}
.hero{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;background:linear-gradient(135deg,#0f172a 0%,#1d4ed8 100%);color:#fff}
.hero p{margin-top:10px;max-width:760px;line-height:1.7;color:rgba(255,255,255,.82)}
.eyebrow{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:rgba(255,255,255,.68)}
.eyebrow.dark{color:#64748b}
.stats-grid,.grid,.stage-list,.mini-grid{display:grid;gap:18px}
.stats-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.stage-list{grid-template-columns:repeat(2,minmax(0,1fr))}
.section-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:18px}
.section-head h2{margin-top:8px}
.stat-card span,.stage-code,.mini-card span,.boundary-card span,.boundary-card small{font-size:12px;color:#64748b}
.stat-card strong{display:block;margin-top:10px;font-size:32px}
.stage-card{border:1px solid #e2e8f0;border-radius:18px;padding:18px;background:#f8fafc}
.stage-top{display:flex;justify-content:space-between;gap:10px;align-items:flex-start;margin-bottom:12px}
.stage-card ul{padding-left:18px;color:#475569;line-height:1.8}
.mini-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.mini-card{padding:18px;background:linear-gradient(135deg,#fff7ed 0%,#eff6ff 100%)}
.mini-card strong,.boundary-card strong{display:block;margin-top:10px;font-size:22px;color:#0f172a}
.boundary-card p{margin-top:10px;line-height:1.7;color:#475569}
.boundary-card small{display:block;margin-top:10px}
@media (max-width:960px){.stats-grid,.grid,.stage-list,.mini-grid{grid-template-columns:1fr}.hero{flex-direction:column}}
</style>
