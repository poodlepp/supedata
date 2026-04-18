<template>
  <div class="univ3-tab">
    <div class="tab-header">
      <div>
        <div class="tab-title">Uniswap V3 扫链数据</div>
        <div class="tab-desc">Ethereum Mainnet · {{ summary?.poolName || 'WETH/USDC 0.05%' }}</div>
      </div>
      <div class="tab-actions">
        <el-select v-model="eventType" class="event-filter" size="small" @change="refreshEvents">
          <el-option label="全部事件" value="" />
          <el-option label="SWAP" value="SWAP" />
          <el-option label="MINT" value="MINT" />
          <el-option label="BURN" value="BURN" />
          <el-option label="COLLECT" value="COLLECT" />
          <el-option label="FLASH" value="FLASH" />
          <el-option label="INITIALIZE" value="INITIALIZE" />
        </el-select>
        <el-button size="small" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col :xs="24" :md="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">最新安全块</div>
          <div class="summary-value">{{ summary?.safeLatestBlock ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">已提交块</div>
          <div class="summary-value">{{ summary?.latestCommittedBlock ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">同步延迟</div>
          <div class="summary-value">{{ summary?.syncLag ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">事件总数</div>
          <div class="summary-value">{{ summary?.totalEvents ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="count-card">
      <template #header>
        <div class="card-title">事件分布</div>
      </template>
      <div v-if="eventCountEntries.length" class="count-grid">
        <div v-for="item in eventCountEntries" :key="item.key" class="count-item">
          <span class="count-key">{{ item.key }}</span>
          <span class="count-value">{{ item.value }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无事件统计" :image-size="80" />
    </el-card>

    <el-card shadow="never" class="events-card">
      <template #header>
        <div class="card-title">最近事件（严格按 block / tx / event 顺序落库）</div>
      </template>
      <el-table :data="events" stripe v-loading="loading">
        <el-table-column prop="blockNumber" label="Block" width="120" />
        <el-table-column prop="transactionIndex" label="TxIdx" width="90" />
        <el-table-column prop="logIndex" label="LogIdx" width="90" />
        <el-table-column prop="eventType" label="Type" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="tagType(row.eventType)">{{ row.eventType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="Summary" min-width="360" show-overflow-tooltip />
        <el-table-column prop="transactionHash" label="Tx Hash" min-width="220" show-overflow-tooltip />
        <el-table-column label="Block Time" width="180">
          <template #default="{ row }">
            {{ formatTime(row.blockTime) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import uniV3Api from '@/api/univ3'

const summary = ref(null)
const events = ref([])
const eventType = ref('')
const loading = ref(false)
let timer = null

const tagType = (type) => {
  const mapping = {
    SWAP: 'success',
    MINT: 'primary',
    BURN: 'danger',
    COLLECT: 'warning',
    FLASH: 'info',
    INITIALIZE: ''
  }
  return mapping[type] ?? ''
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(Number(time)).toLocaleString('zh-CN')
}

const eventCountEntries = computed(() => {
  const counts = summary.value?.eventCounts || {}
  return Object.entries(counts).map(([key, value]) => ({ key, value }))
})

const refreshSummary = async () => {
  summary.value = await uniV3Api.getSummary()
}

const refreshEvents = async () => {
  loading.value = true
  try {
    events.value = await uniV3Api.getEvents({ eventType: eventType.value || undefined, limit: 20 })
  } finally {
    loading.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([refreshSummary(), refreshEvents()])
}

onMounted(async () => {
  await refreshAll()
  timer = setInterval(refreshAll, 5000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.univ3-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.tab-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}

.tab-desc {
  margin-top: 6px;
  font-size: 14px;
  color: #76839b;
}

.tab-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.event-filter {
  width: 160px;
}

.summary-row {
  margin: 0 !important;
}

.summary-card {
  border-radius: 14px;
}

.summary-label {
  font-size: 13px;
  color: #7b8798;
  margin-bottom: 10px;
}

.summary-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
}

.count-card,
.events-card {
  border-radius: 16px;
}

.card-title {
  font-weight: 700;
  color: #1f2d3d;
}

.count-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.count-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f7f9fc;
}

.count-key {
  color: #5b6575;
  font-weight: 600;
}

.count-value {
  color: #111827;
  font-weight: 700;
}
</style>
