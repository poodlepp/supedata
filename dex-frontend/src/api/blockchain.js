/**
 * 区块链 API 调用模块
 * 
 * 职责：
 * - 封装区块链相关的 HTTP 请求
 * - 统一错误处理
 * - 请求拦截和响应转换
 */

import axios from 'axios'

const API_BASE = '/api/blockchain'

const blockchainApi = {
  /**
   * 获取 ETH/USDT 最新价格
   */
  getEthUsdtPrice() {
    return axios.get(`${API_BASE}/price/eth-usdt`)
      .then(res => res.data.data)
      .catch(err => {
        console.error('Failed to get ETH/USDT price:', err)
        throw err
      })
  },

  /**
   * 获取区块链连接状态
   */
  getStatus() {
    return axios.get(`${API_BASE}/status`)
      .then(res => res.data.data)
      .catch(err => {
        console.error('Failed to get blockchain status:', err)
        throw err
      })
  },

  /**
   * 获取最新区块号
   */
  getLatestBlock() {
    return axios.get(`${API_BASE}/block`)
      .then(res => res.data.data)
      .catch(err => {
        console.error('Failed to get latest block:', err)
        throw err
      })
  },

  /**
   * 获取最近的 Swap 事件
   */
  getRecentSwaps() {
    return axios.get(`${API_BASE}/swaps`)
      .then(res => res.data.data)
      .catch(err => {
        console.error('Failed to get recent swaps:', err)
        throw err
      })
  },

  /**
   * 启动价格监听
   */
  startListener() {
    return axios.post(`${API_BASE}/listener/start`)
      .then(res => res.data.data)
      .catch(err => {
        console.error('Failed to start listener:', err)
        throw err
      })
  },

  /**
   * 停止价格监听
   */
  stopListener() {
    return axios.post(`${API_BASE}/listener/stop`)
      .then(res => res.data.data)
      .catch(err => {
        console.error('Failed to stop listener:', err)
        throw err
      })
  }
}

export default blockchainApi
