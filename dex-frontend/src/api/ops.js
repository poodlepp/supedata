import http from './http'

export const getOpsOverview = () =>
  http.get('/api/v1/ops/overview')

export const triggerReplay = (fromBlock, toBlock, reason) =>
  http.post('/api/v1/ops/replay', null, {
    params: {
      fromBlock,
      ...(toBlock ? { toBlock } : {}),
      ...(reason ? { reason } : {})
    }
  })
