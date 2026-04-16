/**
 * 区块链状态管理 Store
 * 
 * 使用 Pinia 管理：
 * - 价格数据
 * - 连接状态
 * - 监听器状态
 * - Swap 事件流
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import blockchainApi from '@/api/blockchain'

const MAX_SWAP_EVENTS = 80
const DISPLAY_SWAP_EVENTS = 10

export const useBlockchainStore = defineStore('blockchain', () => {
  const priceSnapshot = ref(null)
  const isConnected = ref(false)
  const latestBlock = ref(0)
  const isListening = ref(false)
  const loading = ref(false)
  const error = ref(null)
  const swapEvents = ref([])

  const ethUsdtPrice = computed(() => priceSnapshot.value?.price || 0)
  const lastUpdateTime = computed(() => priceSnapshot.value?.timestamp || null)
  const network = computed(() => priceSnapshot.value?.network || 'Ethereum Mainnet')
  const sampledSwapEvents = computed(() => swapEvents.value.slice(0, DISPLAY_SWAP_EVENTS))

  const fetchEthUsdtPrice = async () => {
    loading.value = true
    error.value = null
    try {
      priceSnapshot.value = await blockchainApi.getEthUsdtPrice()
    } catch (err) {
      error.value = err.message
      console.error('Failed to fetch ETH/USDT price:', err)
    } finally {
      loading.value = false
    }
  }

  const fetchStatus = async () => {
    try {
      isConnected.value = await blockchainApi.getStatus()
    } catch (err) {
      isConnected.value = false
      console.error('Failed to fetch status:', err)
    }
  }

  const fetchLatestBlock = async () => {
    try {
      latestBlock.value = await blockchainApi.getLatestBlock()
    } catch (err) {
      console.error('Failed to fetch latest block:', err)
    }
  }

  const fetchRecentSwaps = async () => {
    try {
      const events = await blockchainApi.getRecentSwaps()
      swapEvents.value = Array.isArray(events) ? events.slice(0, MAX_SWAP_EVENTS) : []
    } catch (err) {
      console.error('Failed to fetch recent swaps:', err)
    }
  }

  const startListener = async () => {
    try {
      await blockchainApi.startListener()
      isListening.value = true
      await Promise.all([fetchEthUsdtPrice(), fetchLatestBlock(), fetchRecentSwaps()])
      startPricePolling()
    } catch (err) {
      error.value = err.message
      console.error('Failed to start listener:', err)
    }
  }

  const stopListener = async () => {
    try {
      await blockchainApi.stopListener()
      isListening.value = false
      stopPricePolling()
    } catch (err) {
      error.value = err.message
      console.error('Failed to stop listener:', err)
    }
  }

  let pollInterval = null

  const startPricePolling = () => {
    if (pollInterval) return
    pollInterval = setInterval(() => {
      fetchEthUsdtPrice()
      fetchLatestBlock()
      fetchRecentSwaps()
    }, 5000)
  }

  const stopPricePolling = () => {
    if (pollInterval) {
      clearInterval(pollInterval)
      pollInterval = null
    }
  }

  const init = async () => {
    await fetchStatus()
    await fetchEthUsdtPrice()
    await fetchLatestBlock()
    await fetchRecentSwaps()
  }

  return {
    priceSnapshot,
    isConnected,
    latestBlock,
    isListening,
    loading,
    error,
    swapEvents,
    sampledSwapEvents,
    ethUsdtPrice,
    lastUpdateTime,
    network,
    fetchEthUsdtPrice,
    fetchStatus,
    fetchLatestBlock,
    fetchRecentSwaps,
    startListener,
    stopListener,
    init
  }
})
