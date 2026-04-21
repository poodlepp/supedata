<template>
  <div class="route-page">
    <section class="hero">
      <div>
        <div class="eyebrow">ROUTING ENGINE · STAGE 4</div>
        <h1>多路径报价与路由对比</h1>
        <p>当前阶段 4 展示真实候选路径、轻量拆单比较、Gas/LP fee/滑点/稳定性/新鲜度等评分因素，重点体现系统如何比较后选择，而不是固定返回一条路。</p>
      </div>
    </section>

    <section class="controls card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="From">
          <el-select v-model="from" style="width: 140px">
            <el-option v-for="token in tokens" :key="token" :label="token" :value="token" />
          </el-select>
        </el-form-item>
        <el-form-item label="To">
          <el-select v-model="to" style="width: 140px">
            <el-option v-for="token in tokens" :key="token" :label="token" :value="token" />
          </el-select>
        </el-form-item>
        <el-form-item label="Amount In">
          <el-input-number v-model="amountIn" :min="0.0001" :step="0.1" :precision="4" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadCompare">对比所有路径</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section v-if="compare" class="grid">
      <article class="summary card">
        <div class="eyebrow">BEST EXECUTION</div>
        <h2>{{ renderPath(bestExecution?.path) }}</h2>
        <div class="metric-grid">
          <div><span>执行方式</span><strong>{{ bestExecution?.executionType || '-' }}</strong></div>
          <div><span>Amount Out</span><strong>{{ fmt(bestExecution?.amountOut) }}</strong></div>
          <div><span>Gas Cost</span><strong>${{ fmt(bestExecution?.gasCostUsd) }}</strong></div>
          <div><span>Net Score</span><strong>{{ fmt(bestExecution?.netScore) }}</strong></div>
        </div>
      </article>

      <article class="summary card">
        <div class="eyebrow">COMPARE STATS</div>
        <h2>{{ compare.viableCount }} 条可用 / {{ compare.eliminatedCount }} 条淘汰 / {{ splitOptions.length }} 条拆单对比</h2>
        <div class="metric-grid">
          <div><span>最佳单路径</span><strong>{{ bestSingle?.type || '-' }}</strong></div>
          <div><span>最佳拆单</span><strong>{{ splitOptions[0]?.label || '-' }}</strong></div>
          <div><span>Search Beam</span><strong>{{ compare.search?.beamWidth || '-' }}</strong></div>
          <div><span>Max Hops</span><strong>{{ compare.search?.maxHops || '-' }}</strong></div>
        </div>
      </article>
    </section>

    <section class="grid" v-if="bestExecution?.scoreBreakdown">
      <article class="card">
        <div class="section-head">
          <h2>评分拆解</h2>
          <span class="muted">展示 gross / LP fee / impact / gas / net 的比较口径</span>
        </div>
        <div class="metric-grid">
          <div><span>Gross Out</span><strong>{{ fmt(bestExecution.scoreBreakdown.grossAmountOut) }}</strong></div>
          <div><span>LP Fee Loss</span><strong>{{ fmt(bestExecution.scoreBreakdown.lpFeeCostInOutToken) }}</strong></div>
          <div><span>Impact Loss</span><strong>{{ fmt(bestExecution.scoreBreakdown.priceImpactLossInOutToken) }}</strong></div>
          <div><span>Gas Loss</span><strong>{{ fmt(bestExecution.scoreBreakdown.gasCostInOutToken) }}</strong></div>
          <div><span>Net Amount</span><strong>{{ fmt(bestExecution.scoreBreakdown.netAmountOut) }}</strong></div>
          <div><span>Freshness</span><strong>{{ bestExecution.quoteFreshness || '-' }}</strong></div>
        </div>
      </article>

      <article class="card">
        <div class="section-head">
          <h2>稳定性与深度</h2>
          <span class="muted">不是精确市场风险模型，只是阶段 4 的可解释近似</span>
        </div>
        <div class="metric-grid">
          <div><span>Price Impact</span><strong>{{ fmt(bestExecution?.priceImpactPct) }}%</strong></div>
          <div><span>LP Fee($)</span><strong>${{ fmt(bestExecution?.lpFeeCostUsd) }}</strong></div>
          <div><span>Gas Units</span><strong>{{ formatInteger(bestExecution?.gasAmount) }}</strong></div>
          <div><span>Liquidity Score</span><strong>{{ fmt(bestExecution?.liquidityDepthScore) }}</strong></div>
          <div><span>Stability</span><strong>{{ fmt(bestExecution?.stabilityScore) }}</strong></div>
          <div><span>Quote Age</span><strong>{{ formatMs(bestExecution?.quoteFreshnessMs) }}</strong></div>
        </div>
      </article>
    </section>

    <!-- 可用路径排行 -->
    <section class="card" v-if="compare?.ranked?.length">
      <div class="section-head">
        <h2>可用路径排行（按净收益）</h2>
        <span class="muted">系统在比较之后选择最优路径</span>
      </div>
      <el-table :data="compare.ranked" stripe>
        <el-table-column label="路径" min-width="200">
          <template #default="{ row }">{{ renderPath(row.path) }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="200" />
        <el-table-column prop="hopCount" label="跳数" width="70" />
        <el-table-column label="Gross Out" width="140">
          <template #default="{ row }">{{ fmt(row.grossAmountOut) }}</template>
        </el-table-column>
        <el-table-column label="Net Out" width="140">
          <template #default="{ row }">{{ fmt(row.amountOut) }}</template>
        </el-table-column>
        <el-table-column label="LP Fee($)" width="110">
          <template #default="{ row }">{{ fmt(row.lpFeeCostUsd) }}</template>
        </el-table-column>
        <el-table-column label="Gas($)" width="100">
          <template #default="{ row }">{{ fmt(row.gasCostUsd) }}</template>
        </el-table-column>
        <el-table-column label="Impact(%)" width="110">
          <template #default="{ row }">{{ fmt(row.priceImpactPct) }}</template>
        </el-table-column>
        <el-table-column label="Freshness" width="100">
          <template #default="{ row }">{{ row.quoteFreshness }}</template>
        </el-table-column>
        <el-table-column label="Stability" width="110">
          <template #default="{ row }">{{ fmt(row.stabilityScore) }}</template>
        </el-table-column>
        <el-table-column label="Net Score" width="130">
          <template #default="{ row }"><strong>{{ fmt(row.netScore) }}</strong></template>
        </el-table-column>
        <el-table-column label="Pool" min-width="200">
          <template #default="{ row }">{{ row.poolName || row.label }}</template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 淘汰路径 -->
    <section class="card" v-if="compare?.eliminated?.length">
      <div class="section-head">
        <h2>淘汰路径</h2>
        <span class="muted">记录淘汰原因，可追溯</span>
      </div>
      <el-table :data="compare.eliminated" stripe>
        <el-table-column label="路径" min-width="200">
          <template #default="{ row }">{{ renderPath(row.path) }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="200" />
        <el-table-column label="可用" width="80">
          <template #default="{ row }">
            <el-tag type="info">NO</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eliminationReason" label="淘汰原因" min-width="260" />
      </el-table>
    </section>

    <section class="card" v-if="splitOptions.length">
      <div class="section-head">
        <h2>拆单比较</h2>
        <span class="muted">阶段 4 只做轻量双路径拆单，不做复杂优化器</span>
      </div>
      <el-table :data="splitOptions" stripe>
        <el-table-column prop="label" label="拆单方案" width="150" />
        <el-table-column label="路径组合" min-width="260">
          <template #default="{ row }">{{ row.legs.map(leg => `${leg.allocationPct}% ${renderPath(leg.path)}`).join(' | ') }}</template>
        </el-table-column>
        <el-table-column label="Amount Out" width="140">
          <template #default="{ row }">{{ fmt(row.amountOut) }}</template>
        </el-table-column>
        <el-table-column label="Gas($)" width="100">
          <template #default="{ row }">{{ fmt(row.gasCostUsd) }}</template>
        </el-table-column>
        <el-table-column label="Net Score" width="130">
          <template #default="{ row }"><strong>{{ fmt(row.netScore) }}</strong></template>
        </el-table-column>
        <el-table-column label="vs Best Single" width="140">
          <template #default="{ row }">
            <span :class="row.betterThanBestSingle ? 'good' : 'bad'">{{ signedFmt(row.improvementVsBestSingle) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 多跳路径详情 -->
    <section class="card" v-if="multiHopCandidates.length">
      <div class="section-head">
        <h2>多跳路径详情</h2>
        <span class="muted">每跳的 amountOut 和 priceImpact</span>
      </div>
      <div v-for="c in multiHopCandidates" :key="c.type" class="hop-detail">
        <div class="hop-header">{{ c.path.join(' → ') }} <el-tag :type="c.viable ? 'success' : 'info'" size="small">{{ c.viable ? 'VIABLE' : 'ELIMINATED' }}</el-tag></div>
        <el-table :data="c.hops || []" size="small" style="margin-top:8px">
          <el-table-column label="Hop" width="60">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="Path" min-width="160">
            <template #default="{ row }">{{ row.tokenIn }} → {{ row.tokenOut }}</template>
          </el-table-column>
          <el-table-column label="Amount Out" width="140">
            <template #default="{ row }">{{ fmt(row.amountOut) }}</template>
          </el-table-column>
          <el-table-column label="Impact(%)" width="110">
            <template #default="{ row }">{{ fmt(row.priceImpactPct) }}</template>
          </el-table-column>
          <el-table-column prop="poolName" label="Pool" min-width="200" />
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getCompare } from '@/api/route'

const tokens = ['ETH', 'USDC', 'DAI']
const from = ref('ETH')
const to = ref('USDC')
const amountIn = ref(1)
const loading = ref(false)
const compare = ref(null)

const bestSingle = computed(() => compare.value?.ranked?.[0] || null)
const bestExecution = computed(() => compare.value?.bestExecution || bestSingle.value)
const splitOptions = computed(() => compare.value?.splitOptions || [])
const multiHopCandidates = computed(() => {
  const all = [...(compare.value?.ranked || []), ...(compare.value?.eliminated || [])]
  return all.filter(c => c.hopCount > 1 && c.hops?.length)
})

const fmt = (value) => Number(value || 0).toFixed(4)
const signedFmt = (value) => {
  const num = Number(value || 0).toFixed(4)
  return Number(value || 0) > 0 ? `+${num}` : num
}
const formatInteger = (value) => value == null ? '-' : Number(value).toLocaleString('en-US')
const formatMs = (value) => value == null ? '-' : `${Math.round(Number(value) / 1000)}s`
const renderPath = (path) => {
  if (!Array.isArray(path)) {
    return '-'
  }
  if (Array.isArray(path[0])) {
    return path.map(item => item.join(' → ')).join(' | ')
  }
  return path.join(' → ')
}

const loadCompare = async () => {
  loading.value = true
  try {
    const res = await getCompare(from.value, to.value, amountIn.value)
    compare.value = res.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadCompare)
</script>

<style scoped>
.route-page{min-height:100vh;padding:24px;background:#f5f7fa;display:flex;flex-direction:column;gap:18px}
.hero,.card{background:#fff;border-radius:20px;padding:24px;box-shadow:0 12px 40px rgba(15,23,42,.08)}
.hero{background:linear-gradient(135deg,#0f172a 0%,#1d4ed8 100%);color:#fff}
.hero p{margin-top:10px;max-width:760px;line-height:1.7;color:rgba(255,255,255,.82)}
.eyebrow{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:#94a3b8}
.hero .eyebrow{color:rgba(255,255,255,.68)}
.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}
.metric-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:14px}
.metric-grid span{font-size:12px;color:#64748b}
.metric-grid strong{display:block;margin-top:8px;font-size:24px}
.section-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}
.muted{font-size:12px;color:#64748b}
.good{color:#15803d;font-weight:700}
.bad{color:#b91c1c;font-weight:700}
.hop-detail{margin-bottom:20px;padding:16px;border-radius:12px;background:#f8fafc}
.hop-header{font-weight:600;margin-bottom:4px;display:flex;align-items:center;gap:10px}
@media (max-width:900px){.grid,.metric-grid{grid-template-columns:1fr}}
</style>
