import http from './http'

export const getLiquidity = (pool) => http.get('/api/v1/liquidity/pools')
export const getPoolByAddress = (address) => http.get(`/api/v1/liquidity/${address}`)
export const getPoolsByPair = (token0, token1) => http.get(`/api/v1/liquidity/pair/${token0}/${token1}`)

