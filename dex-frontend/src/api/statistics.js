import http from './http'

export const getOverview = () =>
  http.get('/api/v1/statistics/overview')

export const getStageProgress = () =>
  http.get('/api/v1/stages/progress')

export const getScanProgress = () =>
  http.get('/api/v1/stages/scan-progress')
