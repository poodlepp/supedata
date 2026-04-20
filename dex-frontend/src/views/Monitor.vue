<template>
  <div class="monitor-page">
    <section class="hero">
      <div>
        <div class="eyebrow">STAGE 5 · LIVE OPS</div>
        <h1>实时化与运维能力</h1>
        <p>这里只保留真正影响系统是否健康的东西：实时价格是否在推、索引是否追得上、有没有活跃告警、能不能安全回放。</p>
      </div>
      <div class="hero-actions">
        <el-tag :type="streamState === 'LIVE' ? 'success' : 'warning'">{{ streamState }}</el-tag>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card">
        <span>Pipeline</span>
        <strong>{{ processing.status || '-' }}</strong>
      </article>
      <article class="stat-card">
        <span>Sync Lag</span>
        <strong>{{ processing.syncLagBlocks ?? '-' }}</strong>
      </article>
      <article class="stat-card">
        <span>Event Delay</span>
        <strong>{{ formatLag(processing.latestEventLagMs) }}</strong>
      </article>
      <article class="stat-card">
        <span>Active Alerts</span>
        <strong>{{ alerts.length }}</strong>
      </article>
    </section>

    <section class="grid">
      <article class="panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">PIPELINE</div>
            <h2>索引与推送状态</h2>
          </div>
        </div>
        <div class="mini-grid">
          <div class="mini-card">
            <span>Latest Block</span>
            <strong>{{ processing.latestBlock ?? '-' }}</strong>
          </div>
          <div class="mini-card">
            <span>Committed Block</span>
            <strong>{{ processing.latestCommittedBlock ?? '-' }}</strong>
          </div>
          <div class="mini-card">
            <span>SSE Subscribers</span>
            <strong>{{ metrics.sseSubscribers ?? 0 }}</strong>
          </div>
          <div class="mini-card">
            <span>Kafka Failures</span>
            <strong>{{ metrics.kafkaFailuresTotal ?? 0 }}</strong>
          </div>
        </div>
        <div class="subtext">
          <span>Pool: {{ processing.poolName || '-' }}</span>
          <span>Latest Event: {{ formatTime(processing.latestEventTime) }}</span>
        </div>
      </article>

      <article class="panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">PROMETHEUS</div>
            <h2>指标采集入口</h2>
          </div>
        </div>
        <div class="mini-grid">
          <div class="mini-card">
            <span>App Metrics</span>
            <strong>/actuator/prometheus</strong>
          </div>
          <div class="mini-card">
            <span>Prometheus UI</span>
            <strong>http://localhost:9090</strong>
          </div>
          <div class="mini-card">
            <span>Replay Total</span>
            <strong>{{ metrics.replayTotal ?? 0 }}</strong>
          </div>
          <div class="mini-card">
            <span>Last Replay From</span>
            <strong>{{ metrics.lastReplayFromBlock || '-' }}</strong>
          </div>
        </div>
        <p class="panel-note">应用负责暴露指标，Prometheus 容器负责独立采集；监控页只展示少量关键值，不替代 Prometheus 查询。</p>
      </article>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <div class="eyebrow dark">LIVE PRICES</div>
          <h2>实时价格快照</h2>
        </div>
        <span class="muted">通过 SSE 周期推送，用来确认实时链路还在工作</span>
      </div>
      <el-table :data="livePrices" stripe>
        <el-table-column prop="pair" label="Pair" width="140" />
        <el-table-column label="Price" width="160">
          <template #default="{ row }">{{ row.price == null ? '-' : Number(row.price).toFixed(4) }}</template>
        </el-table-column>
        <el-table-column label="Freshness" width="140">
          <template #default="{ row }">{{ formatLag(row.freshnessMs) }}</template>
        </el-table-column>
        <el-table-column label="Timestamp" min-width="180">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="grid">
      <article class="panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">ALERTS</div>
            <h2>活跃告警</h2>
          </div>
        </div>
        <div v-if="alerts.length" class="alert-list">
          <div v-for="item in alerts" :key="`${item.code}-${item.timestamp}`" class="alert-card">
            <div class="alert-top">
              <el-tag :type="tagType(item.severity)">{{ item.severity }}</el-tag>
              <span>{{ item.code }}</span>
            </div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.detail }}</p>
            <small>{{ formatTime(item.timestamp) }}</small>
          </div>
        </div>
        <div v-else class="empty-card">当前没有活跃告警</div>
      </article>

      <article class="panel">
        <div class="section-head">
          <div>
            <div class="eyebrow dark">REPLAY</div>
            <h2>手动回放 / 补数</h2>
          </div>
        </div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="From Block">
            <el-input-number v-model="replayForm.fromBlock" :min="1" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="Reason">
            <el-input v-model="replayForm.reason" placeholder="例如：repair gap / rescan suspicious window" />
          </el-form-item>
          <el-button type="primary" :loading="replaying" @click="submitReplay">触发回放</el-button>
        </el-form>
        <el-table :data="replayJobs" stripe style="margin-top: 18px">
          <el-table-column prop="status" label="Status" width="110" />
          <el-table-column prop="requestedFromBlock" label="From" width="110" />
          <el-table-column prop="reason" label="Reason" min-width="160" />
          <el-table-column label="Requested At" min-width="180">
            <template #default="{ row }">{{ formatTime(row.requestedAt) }}</template>
          </el-table-column>
        </el-table>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { getOpsOverview, triggerReplay } from '@/api/ops'

const loading = ref(false)
const replaying = ref(false)
const opsOverview = ref({})
const streamPayload = ref(null)
const streamState = ref('CONNECTING')
const replayForm = reactive({
  fromBlock: 0,
  reason: 'manual replay'
})

let eventSource

const processing = computed(() => streamPayload.value?.processing || opsOverview.value?.processing || {})
const livePrices = computed(() => streamPayload.value?.prices || opsOverview.value?.prices || [])
const alerts = computed(() => streamPayload.value?.alerts || opsOverview.value?.alerts || [])
const metrics = computed(() => opsOverview.value?.metrics || {})
const replayJobs = computed(() => opsOverview.value?.replayJobs || [])

const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const formatLag = (value) => {
  if (value == null || value < 0) return '-'
  if (value < 1000) return `${Math.round(value)} ms`
  if (value < 60000) return `${Math.round(value / 1000)} s`
  return `${Math.round(value / 60000)} min`
}
const tagType = (severity) => {
  if (severity === 'CRITICAL') return 'danger'
  if (severity === 'WARN') return 'warning'
  return 'info'
}

const handleStreamEvent = (event) => {
  try {
    streamPayload.value = JSON.parse(event.data)
    streamState.value = 'LIVE'
  } catch {
    streamState.value = 'PARSE_ERROR'
  }
}

const connectStream = () => {
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  eventSource?.close()
  streamState.value = 'CONNECTING'
  eventSource = new EventSource(`${base}/api/v1/ops/stream/prices`)
  eventSource.addEventListener('ops-init', handleStreamEvent)
  eventSource.addEventListener('ops-update', handleStreamEvent)
  eventSource.onopen = () => {
    streamState.value = 'LIVE'
  }
  eventSource.onerror = () => {
    streamState.value = 'RECONNECTING'
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    const res = await getOpsOverview()
    opsOverview.value = res.data.data || {}
    if (!replayForm.fromBlock) {
      replayForm.fromBlock = Number(opsOverview.value?.processing?.latestCommittedBlock || 0)
    }
  } finally {
    loading.value = false
  }
}

const submitReplay = async () => {
  replaying.value = true
  try {
    await triggerReplay(replayForm.fromBlock, null, replayForm.reason)
    await loadAll()
  } finally {
    replaying.value = false
  }
}

onMounted(async () => {
  await loadAll()
  connectStream()
})

onBeforeUnmount(() => {
  eventSource?.close()
})
</script>

<style scoped>
.monitor-page{min-height:100vh;padding:24px;background:linear-gradient(180deg,#eef2ff 0%,#f8fafc 100%);display:flex;flex-direction:column;gap:18px}
.hero,.panel,.stat-card,.mini-card,.alert-card,.empty-card{background:#fff;border-radius:20px;padding:24px;box-shadow:0 16px 40px rgba(15,23,42,.08)}
.hero{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;background:linear-gradient(135deg,#0f172a 0%,#1d4ed8 100%);color:#fff}
.hero p{margin-top:10px;max-width:760px;line-height:1.7;color:rgba(255,255,255,.82)}
.hero-actions{display:flex;gap:12px;align-items:center}
.eyebrow{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:rgba(255,255,255,.68)}
.eyebrow.dark{color:#64748b}
.stats-grid,.grid,.mini-grid,.alert-list{display:grid;gap:18px}
.stats-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.mini-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.section-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:18px}
.section-head h2{margin-top:8px}
.stat-card span,.mini-card span{font-size:12px;color:#64748b}
.stat-card strong{display:block;margin-top:10px;font-size:32px}
.mini-card{padding:18px;background:linear-gradient(135deg,#fff7ed 0%,#eff6ff 100%)}
.mini-card strong{display:block;margin-top:10px;font-size:22px;color:#0f172a;word-break:break-word}
.panel-note,.subtext{margin-top:12px;color:#475569;line-height:1.7}
.subtext{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap}
.muted{color:#64748b;font-size:13px}
.alert-list{grid-template-columns:1fr}
.alert-card{padding:18px;background:linear-gradient(135deg,#ffffff 0%,#f8fafc 100%);border:1px solid #e2e8f0}
.alert-top{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:10px;color:#64748b;font-size:12px}
.alert-card strong{display:block;margin-bottom:8px}
.alert-card p{margin:0;color:#475569;line-height:1.7}
.alert-card small{display:block;margin-top:10px;color:#94a3b8}
.empty-card{display:flex;align-items:center;justify-content:center;min-height:180px;color:#64748b}
@media (max-width:960px){.stats-grid,.grid,.mini-grid{grid-template-columns:1fr}.hero{flex-direction:column}.hero-actions{width:100%;justify-content:flex-start}}
</style>
