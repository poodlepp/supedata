<template>
  <div class="route-page">
    <section class="hero">
      <div>
        <div class="eyebrow">ROUTING ENGINE</div>
        <h1>报价与路径解释面板</h1>
        <p>当前提供阶段 4 的演示版路由引擎：支持直连池与两跳路径的候选路径比较，输出预估数量、gas、滑点与淘汰原因。</p>
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
          <el-button type="primary" :loading="loading" @click="loadQuote">获取报价</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section v-if="quote" class="grid">
      <article class="summary card">
        <div class="eyebrow">BEST ROUTE</div>
        <h2>{{ quote.best?.path?.join(' → ') || '无可用路径' }}</h2>
        <div class="metric-grid">
          <div><span>Amount Out</span><strong>{{ formatNumber(quote.best?.amountOut) }}</strong></div>
          <div><span>Gas Cost</span><strong>${{ formatNumber(quote.best?.gasCostUsd) }}</strong></div>
          <div><span>Price Impact</span><strong>{{ formatNumber(quote.best?.priceImpactPct) }}%</strong></div>
          <div><span>Net Score</span><strong>{{ formatNumber(quote.best?.netScore) }}</strong></div>
        </div>
      </article>

      <article class="summary card">
        <div class="eyebrow">CANDIDATES</div>
        <h2>{{ quote.candidates?.length || 0 }} 条候选路径</h2>
        <p>同一请求会输出稳定、可解释的路径评估结果，便于后续接入真实 on-chain reserves、gas oracle 与多协议池图。</p>
      </article>
    </section>

    <section class="card">
      <div class="section-head">
        <h2>候选路径明细</h2>
      </div>
      <el-table :data="quote?.candidates || []" stripe>
        <el-table-column label="路径" min-width="220">
          <template #default="{ row }">{{ row.path.join(' → ') }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="viable" label="可用" width="100">
          <template #default="{ row }">
            <el-tag :type="row.viable ? 'success' : 'info'">{{ row.viable ? 'YES' : 'NO' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amountOut" label="Amount Out" width="140" />
        <el-table-column prop="gasCostUsd" label="Gas($)" width="120" />
        <el-table-column prop="priceImpactPct" label="Impact(%)" width="120" />
        <el-table-column prop="reason" label="说明" min-width="220" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getQuote } from '../api/route'

const tokens = ['ETH', 'USDC', 'DAI', 'BTC']
const from = ref('ETH')
const to = ref('USDC')
const amountIn = ref(1)
const loading = ref(false)
const quote = ref(null)

const formatNumber = (value) => Number(value || 0).toFixed(4)

const loadQuote = async () => {
  loading.value = true
  try {
    const response = await getQuote(from.value, to.value, amountIn.value)
    quote.value = response.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadQuote)
</script>

<style scoped>
.route-page{min-height:100vh;padding:24px;background:#f5f7fa;display:flex;flex-direction:column;gap:18px}.hero,.card{background:#fff;border-radius:20px;padding:24px;box-shadow:0 12px 40px rgba(15,23,42,.08)}.hero{background:linear-gradient(135deg,#0f172a 0%,#1d4ed8 100%);color:#fff}.hero p{margin-top:10px;max-width:760px;line-height:1.7;color:rgba(255,255,255,.82)}.eyebrow{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:#94a3b8}.hero .eyebrow{color:rgba(255,255,255,.68)}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.metric-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:14px}.metric-grid span{font-size:12px;color:#64748b}.metric-grid strong{display:block;margin-top:8px;font-size:24px}.section-head{margin-bottom:16px}@media (max-width:900px){.grid,.metric-grid{grid-template-columns:1fr}}
</style>
