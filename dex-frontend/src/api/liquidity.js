import http from './http'

export const getLiquidity = (pool) => http.get(`/api/v1/liquidity/${pool}`)
