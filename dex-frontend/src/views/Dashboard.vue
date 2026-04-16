<template>
  <div class="dashboard">
    <el-container>
      <el-main>
        <div class="header">
          <h1>DEX 聚合平台</h1>
          <p>首页随机展示后端返回的数据，验证前后端联通</p>
        </div>

        <div class="section entry-section">
          <h2>功能入口</h2>
          <div class="entry-card">
            <div>
              <div class="entry-title">区块链监听演示</div>
              <div class="entry-desc">进入 Sepolia 区块链页面，查看连接状态、最新区块和 ETH/USDT 实时监听演示。</div>
            </div>
            <router-link class="entry-link" to="/blockchain">
              <el-button type="primary" size="large">进入 Blockchain 页面</el-button>
            </router-link>
          </div>
        </div>

        <div class="section spotlight-section">
          <h2>随机抽样 · 来自后端接口</h2>
          <el-row :gutter="20">
            <el-col :xs="24" :md="12">
              <el-card class="spotlight-card" shadow="hover">
                <template #header>
                  <div class="card-header">
                    <span>随机价格</span>
                    <span class="status-tag">API</span>
                  </div>
                </template>
                <template v-if="featuredPrice">
                  <div class="spotlight-title">{{ featuredPrice.pair }}</div>
                  <div class="spotlight-value">${{ Number(featuredPrice.price).toFixed(2) }}</div>
                  <div class="spotlight-meta">创建时间：{{ formatTime(featuredPrice.createdAt) }}</div>
                </template>
                <div v-else class="empty-text">暂无价格数据</div>
              </el-card>
            </el-col>

            <el-col :xs="24" :md="12">
              <el-card class="spotlight-card" shadow="hover">
                <template #header>
                  <div class="card-header">
                    <span>随机流动性池</span>
                    <span class="status-tag">API</span>
                  </div>
                </template>
                <template v-if="featuredPool">
                  <div class="spotlight-title">{{ featuredPool.token0 }} / {{ featuredPool.token1 }}</div>
                  <div class="spotlight-meta">池地址：{{ featuredPool.poolAddress }}</div>
                  <div class="spotlight-meta">Reserve0：{{ Number(featuredPool.reserve0).toFixed(2) }}</div>
                  <div class="spotlight-meta">Reserve1：{{ Number(featuredPool.reserve1).toFixed(2) }}</div>
                </template>
                <div v-else class="empty-text">暂无流动性池数据</div>
              </el-card>
            </el-col>
          </el-row>
          <div class="fetch-time">最近一次拉取：{{ formatTime(lastUpdated) }}</div>
        </div>

        <div class="section">
          <h2>实时价格</h2>
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="8" v-for="price in prices" :key="price.id">
              <el-card class="price-card">
                <template #header>
                  <div class="card-header">
                    <span class="pair-name">{{ price.pair }}</span>
                  </div>
                </template>
                <div class="price-value">${{ Number(price.price).toFixed(2) }}</div>
                <div class="price-time">{{ formatTime(price.createdAt) }}</div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <div class="section">
          <h2>流动性池</h2>
          <el-table :data="pools" stripe>
            <el-table-column prop="poolAddress" label="池地址" width="200" show-overflow-tooltip />
            <el-table-column prop="token0" label="Token 0" width="100" />
            <el-table-column prop="token1" label="Token 1" width="100" />
            <el-table-column prop="reserve0" label="储备 0" width="150">
              <template #default="{ row }">
                {{ Number(row.reserve0).toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="reserve1" label="储备 1" width="150">
              <template #default="{ row }">
                {{ Number(row.reserve1).toFixed(2) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getPrice } from '../api/price'
import { getLiquidity } from '../api/liquidity'

const prices = ref([])
const pools = ref([])
const featuredPrice = ref(null)
const featuredPool = ref(null)
const lastUpdated = ref(null)
let refreshTimer = null

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const shuffleList = (list) => [...list].sort(() => Math.random() - 0.5)

const pickRandomItem = (list) => {
  if (!list.length) return null
  return list[Math.floor(Math.random() * list.length)]
}

const loadPrices = async () => {
  try {
    const pairs = ['ETH-USDC', 'BTC-USDC', 'DAI-USDC']
    const results = await Promise.all(pairs.map((pair) => getPrice(pair)))
    const loadedPrices = results
      .map((response) => response.data?.data)
      .filter(Boolean)

    prices.value = shuffleList(loadedPrices)
    featuredPrice.value = pickRandomItem(loadedPrices)
  } catch (error) {
    console.error('Failed to load prices:', error)
  }
}

const loadPools = async () => {
  try {
    const response = await getLiquidity()
    const loadedPools = response.data?.data || []

    pools.value = shuffleList(loadedPools)
    featuredPool.value = pickRandomItem(loadedPools)
  } catch (error) {
    console.error('Failed to load pools:', error)
  }
}

const refreshDashboard = async () => {
  await Promise.all([loadPrices(), loadPools()])
  lastUpdated.value = new Date().toISOString()
}

onMounted(() => {
  refreshDashboard()
  refreshTimer = setInterval(refreshDashboard, 30000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.header {
  margin-bottom: 40px;
  text-align: center;
}

.header h1 {
  font-size: 32px;
  color: #333;
  margin-bottom: 10px;
}

.header p {
  font-size: 16px;
  color: #666;
}

.section {
  margin-bottom: 40px;
}

.section h2 {
  font-size: 20px;
  color: #333;
  margin-bottom: 20px;
  border-bottom: 2px solid #409eff;
  padding-bottom: 10px;
}

.entry-section {
  margin-bottom: 32px;
}

.entry-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 24px 28px;
  background: linear-gradient(135deg, #ecf5ff 0%, #f4f9ff 100%);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 18px;
}

.entry-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 8px;
}

.entry-desc {
  font-size: 14px;
  color: #607085;
  line-height: 1.7;
}

.entry-link {
  flex-shrink: 0;
}

.spotlight-section {
  margin-bottom: 32px;
}

.spotlight-card {
  border-radius: 16px;
}

.price-card {
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.price-card:hover {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-tag {
  font-size: 12px;
  color: #409eff;
  background: rgba(64, 158, 255, 0.12);
  padding: 4px 10px;
  border-radius: 999px;
}

.pair-name {
  font-weight: bold;
  font-size: 16px;
  color: #409eff;
}

.spotlight-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 12px;
}

.spotlight-value {
  font-size: 34px;
  font-weight: bold;
  color: #333;
  margin-bottom: 16px;
}

.spotlight-meta,
.fetch-time,
.price-time,
.empty-text {
  font-size: 13px;
  color: #7a8599;
}

.fetch-time {
  margin-top: 14px;
}

.price-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  margin: 20px 0;
}
</style>
