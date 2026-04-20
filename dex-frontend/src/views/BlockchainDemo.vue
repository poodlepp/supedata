<template>
  <div class="chain-page">
    <section class="hero-panel">
      <div>
        <div class="eyebrow">CHAIN INDEX</div>
        <h1>主链状态与 UniV3 扫链事件</h1>
        <p>
          `blockchain` 与 `univ3` 页面已合并。当前页面只保留 Ethereum Mainnet RPC 状态、阶段 1 扫链进度和
          Uniswap V3 事件结果，不再展示 V2 监听数据。
        </p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :loading="loading" @click="loadPage">刷新</el-button>
      </div>
    </section>

    <el-alert v-if="pageError" :title="pageError" type="error" :closable="true" @close="pageError = ''" />
    <el-alert
      v-if="summary.errorMessage || scanProgress.errorMessage"
      :title="summary.errorMessage || scanProgress.errorMessage"
      type="warning"
      :closable="false"
    />

    <section class="metrics-grid">
      <article class="metric-card">
        <span>RPC Status</span>
        <strong>{{ rpcConnected ? 'ONLINE' : 'OFFLINE' }}</strong>
        <small>Ethereum Mainnet</small>
      </article>
      <article class="metric-card">
        <span>Latest Block</span>
        <strong>{{ formatNumber(scanProgress.latestBlock || summary.latestBlock) }}</strong>
        <small>{{ scanProgress.status || summary.status || 'UNKNOWN' }}</small>
      </article>
      <article class="metric-card">
        <span>Committed Block</span>
        <strong>{{ formatNumber(scanProgress.latestCommittedBlock || summary.latestCommittedBlock) }}</strong>
        <small>索引检查点</small>
      </article>
      <article class="metric-card">
        <span>Total Events</span>
        <strong>{{ formatNumber(scanProgress.totalEvents || summary.totalEvents) }}</strong>
        <small>已入库 UniV3 事件</small>
      </article>
    </section>

    <section class="panel-grid">
      <article class="panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">STAGE 1</div>
            <h2>扫链进度</h2>
          </div>
          <span class="pill" :class="statusClass">{{ scanProgress.status || summary.status || 'UNKNOWN' }}</span>
        </div>

        <div class="detail-grid">
          <div>
            <span>Pool</span>
            <strong>{{ scanProgress.poolName || summary.poolName || '-' }}</strong>
          </div>
          <div>
            <span>Pool Address</span>
            <strong>{{ shortHash(scanProgress.poolAddress || summary.poolAddress) }}</strong>
          </div>
          <div>
            <span>起始块</span>
            <strong>{{ formatNumber(scanProgress.startBlock || summary.startBlock) }}</strong>
          </div>
          <div>
            <span>安全块高</span>
            <strong>{{ formatNumber(scanProgress.safeLatestBlock || summary.safeLatestBlock) }}</strong>
          </div>
          <div>
            <span>同步延迟</span>
            <strong>{{ formatNumber(scanProgress.syncLag || summary.syncLag) }}</strong>
          </div>
          <div>
            <span>最新事件时间</span>
            <strong>{{ formatTime(scanProgress.latestEventTime || summary.latestEventTime) }}</strong>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">EVENT MIX</div>
            <h2>事件类型分布</h2>
          </div>
          <span class="muted">全量计数 + 最近事件分组</span>
        </div>

        <div v-if="distributionEntries.length" class="distribution-grid">
          <article v-for="item in distributionEntries" :key="item.type" class="distribution-card">
            <span>{{ item.type }}</span>
            <strong>{{ formatNumber(item.count) }}</strong>
            <small>{{ eventLabels[item.type] || item.type }}</small>
          </article>
        </div>
        <div v-else class="empty-state">暂无事件分布数据。</div>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">RECENT EVENTS</div>
          <h2>最近 {{ recentEvents.length }} 条 UniV3 事件</h2>
        </div>
        <span class="muted">按事件类型归类展示</span>
      </div>

      <div v-if="!groupedEvents.length" class="empty-state">暂无最近事件数据。</div>

      <div v-else class="event-groups">
        <article v-for="group in groupedEvents" :key="group.type" class="event-group">
          <div class="group-head">
            <div>
              <span class="group-type">{{ group.type }}</span>
              <h3>{{ eventLabels[group.type] || group.type }}</h3>
            </div>
            <span class="group-count">{{ group.items.length }} recent / {{ formatNumber(group.totalCount) }} total</span>
          </div>

          <div class="event-list">
            <article v-for="event in group.items" :key="event.eventUid" class="event-card">
              <div class="event-top">
                <div>
                  <strong>{{ event.summary || fallbackSummary(event) }}</strong>
                  <small>{{ formatTime(event.blockTime) }}</small>
                </div>
                <a class="event-link" :href="txUrl(event.transactionHash)" target="_blank" rel="noopener noreferrer">Tx</a>
              </div>

              <div class="event-meta">
                <span>Block {{ formatNumber(event.blockNumber) }}</span>
                <span>Log {{ event.logIndex ?? '-' }}</span>
                <span>Sender {{ shortHash(event.sender || event.ownerAddress) }}</span>
                <span>Recipient {{ shortHash(event.recipient) }}</span>
              </div>
            </article>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import blockchainApi from '@/api/blockchain'
import { getScanProgress } from '@/api/statistics'
import uniV3Api from '@/api/univ3'

const loading = ref(false)
const pageError = ref('')
const rpcConnected = ref(false)
const scanProgress = ref({})
const summary = ref({})
const recentEvents = ref([])

const eventOrder = ['SWAP', 'MINT', 'BURN', 'COLLECT', 'FLASH', 'INITIALIZE']
const eventLabels = {
  SWAP: '交易交换',
  MINT: '新增流动性',
  BURN: '移除流动性',
  COLLECT: '手续费领取',
  FLASH: '闪电贷',
  INITIALIZE: '池初始化'
}

let refreshTimer = null

const statusClass = computed(() => (scanProgress.value.status || summary.value.status) === 'RUNNING' ? 'live' : 'idle')

const distributionEntries = computed(() => {
  const counts = summary.value?.eventCounts || scanProgress.value?.eventCounts || {}
  const known = eventOrder
    .filter(type => counts[type] != null)
    .map(type => ({ type, count: counts[type] }))
  const extra = Object.entries(counts)
    .filter(([type]) => !eventOrder.includes(type))
    .map(([type, count]) => ({ type, count }))
  return [...known, ...extra]
})

const groupedEvents = computed(() => {
  if (!recentEvents.value.length) {
    return []
  }

  const byType = recentEvents.value.reduce((groups, event) => {
    const type = event.eventType || 'UNKNOWN'
    if (!groups[type]) {
      groups[type] = []
    }
    groups[type].push(event)
    return groups
  }, {})

  const orderedTypes = [
    ...eventOrder.filter(type => byType[type]),
    ...Object.keys(byType).filter(type => !eventOrder.includes(type)).sort()
  ]

  return orderedTypes.map(type => ({
    type,
    items: byType[type],
    totalCount: (summary.value?.eventCounts || scanProgress.value?.eventCounts || {})[type] ?? byType[type].length
  }))
})

const formatNumber = (value) => value == null ? '-' : Number(value).toLocaleString('en-US')

const formatTime = (value) => {
  if (!value) {
    return '未更新'
  }
  return new Date(Number(value)).toLocaleString('zh-CN', { hour12: false })
}

const shortHash = (value) => value ? `${value.slice(0, 6)}...${value.slice(-4)}` : '-'

const txUrl = (hash) => `https://etherscan.io/tx/${hash}`

const fallbackSummary = (event) => {
  if (!event?.eventType) {
    return 'Unknown event'
  }
  return `${event.eventType} @ block ${event.blockNumber || '-'}`
}

const loadPage = async () => {
  loading.value = true

  const [statusRes, scanRes, summaryRes, eventsRes] = await Promise.allSettled([
    blockchainApi.getStatus(),
    getScanProgress(),
    uniV3Api.getSummary(),
    uniV3Api.getEvents({ limit: 80 })
  ])

  const errors = []

  if (statusRes.status === 'fulfilled') {
    rpcConnected.value = Boolean(statusRes.value)
  } else {
    errors.push('RPC 状态读取失败')
  }

  if (scanRes.status === 'fulfilled') {
    scanProgress.value = scanRes.value.data.data || {}
  } else {
    errors.push('扫链进度读取失败')
  }

  if (summaryRes.status === 'fulfilled') {
    summary.value = summaryRes.value || {}
  } else {
    errors.push('UniV3 摘要读取失败')
  }

  if (eventsRes.status === 'fulfilled') {
    recentEvents.value = Array.isArray(eventsRes.value) ? eventsRes.value : []
  } else {
    errors.push('最近事件读取失败')
  }

  pageError.value = errors.join('；')
  loading.value = false
}

onMounted(() => {
  loadPage()
  refreshTimer = window.setInterval(loadPage, 10000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.chain-page{--bg:#07111c;--panel:#0d1828;--line:rgba(148,163,184,.16);--text:#e2e8f0;--muted:#94a3b8;--accent:#38bdf8;--green:#4ade80;min-height:100vh;padding:24px;color:var(--text);background:radial-gradient(circle at top left,rgba(56,189,248,.18),transparent 30%),linear-gradient(180deg,#08111c 0%,var(--bg) 100%);display:flex;flex-direction:column;gap:18px}
.hero-panel,.panel,.metric-card,.distribution-card,.event-card{border:1px solid var(--line);border-radius:22px;background:linear-gradient(180deg,rgba(13,24,40,.96),rgba(8,16,28,.9))}
.hero-panel{display:flex;justify-content:space-between;gap:20px;padding:28px}
.hero-panel h1,.panel h2{margin:10px 0 0}
.hero-panel p{max-width:760px;color:var(--muted);line-height:1.8}
.hero-actions{display:flex;align-items:flex-end}
.eyebrow{font-size:11px;letter-spacing:.18em;color:var(--muted);text-transform:uppercase}
.metrics-grid,.panel-grid,.distribution-grid,.detail-grid,.event-groups,.event-list,.event-meta{display:grid;gap:16px}
.metrics-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.metric-card{padding:20px;display:flex;flex-direction:column;gap:10px}
.metric-card span,.detail-grid span,.distribution-card span,.muted{font-size:12px;color:var(--muted)}
.metric-card strong{font-size:30px}
.metric-card small,.distribution-card small{color:var(--muted)}
.panel-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.panel{padding:22px}
.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:18px}
.pill{padding:8px 12px;border-radius:999px;font-size:12px}
.pill.live{color:var(--green);background:rgba(74,222,128,.12)}
.pill.idle{color:#fbbf24;background:rgba(251,191,36,.12)}
.detail-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.detail-grid div,.distribution-card,.event-card{padding:16px;border-radius:18px;background:rgba(255,255,255,.02)}
.detail-grid strong,.distribution-card strong{display:block;margin-top:8px;font-size:20px;word-break:break-word}
.distribution-grid{grid-template-columns:repeat(3,minmax(0,1fr))}
.empty-state{min-height:160px;display:flex;align-items:center;justify-content:center;border:1px dashed rgba(56,189,248,.18);border-radius:18px;color:var(--muted)}
.event-groups{grid-template-columns:1fr}
.event-group{padding:18px;border:1px solid rgba(255,255,255,.06);border-radius:20px;background:rgba(255,255,255,.02)}
.group-head{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;margin-bottom:16px}
.group-type{font-size:11px;letter-spacing:.16em;color:var(--muted);text-transform:uppercase}
.group-head h3{margin-top:8px}
.group-count{font-size:12px;color:var(--muted)}
.event-list{grid-template-columns:repeat(2,minmax(0,1fr))}
.event-top{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}
.event-top strong{display:block;line-height:1.6}
.event-top small{display:block;margin-top:8px;color:var(--muted)}
.event-link{display:inline-flex;align-items:center;justify-content:center;padding:8px 12px;border-radius:999px;background:rgba(56,189,248,.12);color:var(--accent);text-decoration:none;font-weight:600}
.event-meta{grid-template-columns:repeat(2,minmax(0,1fr));margin-top:14px}
.event-meta span{font-size:12px;color:var(--muted)}
@media (max-width:1180px){.metrics-grid,.distribution-grid,.event-list{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media (max-width:860px){.chain-page{padding:14px}.hero-panel,.panel-grid,.metrics-grid,.distribution-grid,.detail-grid,.event-list,.event-meta{grid-template-columns:1fr}.hero-panel{flex-direction:column}.hero-actions{align-items:stretch}}
</style>
