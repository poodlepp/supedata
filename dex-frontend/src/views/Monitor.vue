<template>
  <div class="monitor-page">
    <section class="hero">
      <div>
        <div class="eyebrow">DELIVERY TRACKER</div>
        <h1>分阶段交付验收看板</h1>
        <p>当前展示阶段 0-4 的真实交付状态：主网连接、协议索引、真实池数据服务与真实报价能力均已收口验收。</p>
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
        <strong>{{ progress?.poolCount || 0 }}</strong>
      </article>
      <article class="stat-card">
        <span>Price Pairs</span>
        <strong>{{ progress?.pricePairsReady || 0 }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="section-head">
        <h2>阶段进度</h2>
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

    <section class="panel grid">
      <article class="mini-panel">
        <div class="eyebrow">OVERVIEW</div>
        <h2>真实数据概览</h2>
        <pre>{{ JSON.stringify(overview, null, 2) }}</pre>
      </article>
      <article class="mini-panel">
        <div class="eyebrow">VOLUME</div>
        <h2>当前统计边界</h2>
        <pre>{{ JSON.stringify(volume, null, 2) }}</pre>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview, getStageProgress, getVolume } from '../api/statistics'

const loading = ref(false)
const progress = ref(null)
const overview = ref(null)
const volume = ref(null)

const stages = computed(() => progress.value?.stages || [])
const doneCount = computed(() => stages.value.filter(item => item.done).length)

const loadAll = async () => {
  loading.value = true
  try {
    const [stageRes, overviewRes, volumeRes] = await Promise.all([
      getStageProgress(),
      getOverview(),
      getVolume('ETH-USDC', '24h')
    ])
    progress.value = stageRes.data.data
    overview.value = overviewRes.data.data
    volume.value = volumeRes.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.monitor-page{min-height:100vh;padding:24px;background:#eef2ff;display:flex;flex-direction:column;gap:18px}.hero,.panel,.stat-card{background:#fff;border-radius:20px;padding:24px;box-shadow:0 16px 40px rgba(15,23,42,.08)}.hero{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;background:linear-gradient(135deg,#111827 0%,#312e81 100%);color:#fff}.hero p{margin-top:10px;max-width:760px;line-height:1.7;color:rgba(255,255,255,.82)}.eyebrow{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:#94a3b8}.hero .eyebrow{color:rgba(255,255,255,.68)}.stats-grid,.grid,.stage-list{display:grid;gap:18px}.stats-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.stage-list{grid-template-columns:repeat(2,minmax(0,1fr))}.stat-card span,.stage-code{font-size:12px;color:#64748b}.stat-card strong{display:block;margin-top:10px;font-size:32px}.stage-card{border:1px solid #e2e8f0;border-radius:18px;padding:18px;background:#f8fafc}.stage-top{display:flex;justify-content:space-between;gap:10px;align-items:flex-start;margin-bottom:12px}.stage-card ul{padding-left:18px;color:#475569;line-height:1.8}.mini-panel pre{margin-top:12px;padding:16px;border-radius:16px;background:#0f172a;color:#dbeafe;overflow:auto}@media (max-width:960px){.stats-grid,.grid,.stage-list{grid-template-columns:1fr}.hero{flex-direction:column}}
</style>
