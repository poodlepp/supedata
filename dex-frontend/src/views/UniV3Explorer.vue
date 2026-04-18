<template>
  <div class="univ3-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">ON-CHAIN INDEXER</div>
        <h1>Uniswap V3 扫链数据</h1>
        <p>独立查看 Ethereum Mainnet 上 WETH/USDC 0.05% Pool 的扫链进度、事件分布与最近事件流。</p>
      </div>
      <router-link to="/dashboard">
        <el-button>返回首页</el-button>
      </router-link>
    </div>

    <UniV3Tab />
  </div>
</template>

<script setup>
import UniV3Tab from '../components/layout/UniV3Tab.vue'
</script>

<style scoped>
.univ3-page {
  min-height: calc(100vh - 80px);
  padding: 24px;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
  padding: 24px 28px;
  border-radius: 20px;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  color: #fff;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.68);
}

.page-header h1 {
  margin: 10px 0 8px;
  font-size: 30px;
}

.page-header p {
  margin: 0;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.7;
}
</style>
