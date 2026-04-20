<template>
  <div class="blockchain-demo">
    <section class="hero-panel">
      <div>
        <div class="eyebrow">BLOCKCHAIN CONSOLE</div>
        <h1>主链状态与监听面板</h1>
        <p>查看主网连接状态、最新区块、链上价格监听与 Swap 事件流；该页面用于验证读链与监听能力，并作为真实交付的一部分持续保留。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" :loading="loading" :disabled="isListening" @click="handleStartListener">启动监听</el-button>
        <el-button :disabled="!isListening" @click="handleStopListener">停止监听</el-button>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" :closable="true" @close="error = null" />

    <section class="metrics-grid">
      <article class="metric-card"><span>Connection</span><strong>{{ isConnected ? 'ONLINE' : 'OFFLINE' }}</strong><small>{{ network }}</small></article>
      <article class="metric-card"><span>ETH / USDT</span><strong>{{ ethUsdtPrice.toFixed(2) }}</strong><small>{{ formatTime(lastUpdateTime) }}</small></article>
      <article class="metric-card"><span>Latest Block</span><strong>{{ latestBlock || '--' }}</strong><small>最新区块高度</small></article>
      <article class="metric-card"><span>Swap Events</span><strong>{{ swapEvents.length }}</strong><small>当前展示最新 {{ sampledSwapEvents.length }} 条</small></article>
    </section>

    <section class="panel info-panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">SYSTEM MATRIX</div>
          <h2>监听总览</h2>
        </div>
        <span class="pill" :class="isListening ? 'live' : 'idle'">{{ isListening ? 'LIVE' : 'IDLE' }}</span>
      </div>
      <div class="info-grid">
        <div><span>网络</span><strong>{{ network }}</strong></div>
        <div><span>监听状态</span><strong>{{ isListening ? '监听中' : '已停止' }}</strong></div>
        <div><span>最新区块</span><strong>{{ latestBlock || '--' }}</strong></div>
        <div><span>当前价格</span><strong>{{ ethUsdtPrice.toFixed(2) }} USDT</strong></div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">SWAP TAPE</div>
          <h2>Swap 事件流</h2>
        </div>
        <div class="feed-tip">最新 {{ sampledSwapEvents.length }} 条 / 共 {{ swapEvents.length }} 条</div>
      </div>

      <div v-if="!sampledSwapEvents.length" class="empty-feed">启动监听后，这里会显示最新的 Swap 事件。</div>

      <div v-else class="swap-feed-shell" @mouseenter="isFeedHovered = true" @mouseleave="isFeedHovered = false">
        <div class="swap-feed-track" :class="{ paused: !isListening || isFeedHovered }">
          <article
            v-for="(event, index) in duplicatedSwapEvents"
            :key="`${event.txHash}-${index}`"
            class="swap-card"
            :class="event.side === 'SELL_ETH' ? 'sell' : 'buy'"
            @click="openTxDetail(event.txHash)"
          >
            <div class="swap-top">
              <div>
                <div class="swap-side">{{ event.side === 'SELL_ETH' ? 'SELL ETH' : 'BUY ETH' }}</div>
                <div class="swap-time">{{ formatTime(event.timestamp) }}</div>
              </div>
              <strong>{{ formatPrice(event.price) }}</strong>
            </div>
            <div class="swap-row">{{ event.tokenInSymbol }} {{ formatAmount(event.amountIn) }} → {{ event.tokenOutSymbol }} {{ formatAmount(event.amountOut) }}</div>
            <div class="swap-meta">
              <span>Block #{{ event.blockNumber }}</span>
              <span>Tx {{ shortHash(event.txHash) }}</span>
              <span>From {{ shortHash(event.sender) }}</span>
              <span>To {{ shortHash(event.recipient) }}</span>
            </div>
            <div class="swap-action">点击查看交易详情 ↗</div>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useBlockchainStore } from '@/stores/blockchainStore'

const blockchainStore = useBlockchainStore()
const { isConnected, ethUsdtPrice, lastUpdateTime, network, latestBlock, isListening, loading, error, swapEvents, sampledSwapEvents } = storeToRefs(blockchainStore)

const isFeedHovered = ref(false)
const duplicatedSwapEvents = computed(() => sampledSwapEvents.value.length ? [...sampledSwapEvents.value, ...sampledSwapEvents.value] : [])
const formatTime = (t) => !t ? '未更新' : new Date(t).toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
const formatAmount = (v) => Number(v || 0).toFixed(4)
const formatPrice = (v) => `${Number(v || 0).toFixed(2)} USDT`
const shortHash = (v) => v ? `${v.slice(0, 6)}...${v.slice(-4)}` : '--'
const openTxDetail = (txHash) => window.open(`https://etherscan.io/tx/${txHash}`, '_blank', 'noopener,noreferrer')
const handleStartListener = async () => { await blockchainStore.startListener() }
const handleStopListener = async () => { await blockchainStore.stopListener() }

onMounted(async () => { await blockchainStore.init() })
onUnmounted(() => { if (isListening.value) blockchainStore.stopListener() })
</script>

<style scoped>
.blockchain-demo{--bg:#050b14;--panel:#0a1524;--line:rgba(125,168,255,.14);--text:#ebf3ff;--muted:#90a7c4;--accent:#6ea8ff;--green:#41d39f;--red:#ff6b88;min-height:100vh;padding:24px;color:var(--text);background:radial-gradient(circle at top left,rgba(76,125,255,.24),transparent 30%),linear-gradient(180deg,#08111d 0%,var(--bg) 100%);display:flex;flex-direction:column;gap:18px}.hero-panel,.panel,.metric-card{border:1px solid var(--line);border-radius:22px;background:linear-gradient(180deg,rgba(10,21,36,.95),rgba(7,15,28,.84));backdrop-filter:blur(16px)}.hero-panel{display:flex;justify-content:space-between;gap:20px;padding:28px}.eyebrow{font-size:11px;letter-spacing:.18em;color:var(--muted);text-transform:uppercase}.hero-panel h1,.panel h2{margin:10px 0 0}.hero-panel p{max-width:760px;color:var(--muted);line-height:1.7}.hero-actions{display:flex;gap:10px;align-items:flex-end}.metrics-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.metric-card{padding:20px;display:flex;flex-direction:column;gap:10px}.metric-card span,.info-grid span,.feed-tip{color:var(--muted);font-size:12px}.metric-card strong{font-size:30px}.panel{padding:22px}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:18px}.pill{padding:8px 12px;border-radius:999px;font-size:12px;border:1px solid transparent}.pill.live{color:var(--green);background:rgba(65,211,159,.1)}.pill.idle{color:#ffd479;background:rgba(255,212,121,.1)}.info-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.info-grid div,.swap-card{padding:14px;border-radius:16px;background:rgba(255,255,255,.02);border:1px solid rgba(255,255,255,.05)}.info-grid strong{display:block;margin-top:8px}.empty-feed{min-height:180px;display:flex;align-items:center;justify-content:center;color:var(--muted);border:1px dashed rgba(110,168,255,.2);border-radius:18px}.swap-feed-shell{max-height:540px;overflow:hidden;mask-image:linear-gradient(180deg,transparent,#000 8%,#000 92%,transparent);-webkit-mask-image:linear-gradient(180deg,transparent,#000 8%,#000 92%,transparent)}.swap-feed-track{display:flex;flex-direction:column;gap:12px;animation:scrollFeed 20s linear infinite}.swap-feed-track.paused{animation-play-state:paused}.swap-card{cursor:pointer;transition:transform .2s ease,border-color .2s ease,background .2s ease}.swap-card:hover{transform:translateY(-2px);border-color:rgba(110,168,255,.32);background:rgba(255,255,255,.04)}.swap-card.buy{border-color:rgba(65,211,159,.24)}.swap-card.sell{border-color:rgba(255,107,136,.24)}.swap-top{display:flex;justify-content:space-between;gap:12px}.swap-side{font-size:11px;letter-spacing:.16em;color:var(--muted)}.swap-time{margin-top:6px;font-size:14px}.swap-row{margin-top:12px;font-weight:600}.swap-meta{margin-top:12px;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;color:var(--muted);font-size:12px}.swap-action{margin-top:12px;color:var(--accent);font-size:12px;font-weight:600}@keyframes scrollFeed{0%{transform:translateY(0)}100%{transform:translateY(-50%)}}@media (max-width:1000px){.metrics-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media (max-width:760px){.blockchain-demo{padding:14px}.hero-panel{flex-direction:column}.hero-actions,.metrics-grid,.info-grid,.swap-meta{grid-template-columns:1fr;width:100%}.metrics-grid{display:grid}.hero-actions{flex-direction:column;align-items:stretch}}
</style>
