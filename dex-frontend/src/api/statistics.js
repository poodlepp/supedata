import http from './http'

export const getVolume = (pair, period = '24h') =>
  http.get('/api/v1/statistics/volume', { params: { pair, period } })

export const getOverview = () =>
  http.get('/api/v1/statistics/overview')

export const getStageProgress = () =>
  http.get('/api/v1/stages/progress')
