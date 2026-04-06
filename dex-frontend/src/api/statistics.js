import http from './http'

export const getVolume = (pair) =>
  http.get('/api/v1/statistics/volume', { params: { pair } })
