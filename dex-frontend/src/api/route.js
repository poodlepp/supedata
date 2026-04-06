import http from './http'

export const getBestRoute = (from, to) =>
  http.get('/api/v1/routes/best', { params: { from, to } })
