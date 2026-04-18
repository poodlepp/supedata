import http from './http'

export const getBestRoute = (from, to, amountIn = 1) =>
  http.get('/api/v1/routes/best', { params: { from, to, amountIn } })

export const getQuote = (from, to, amountIn = 1) =>
  http.get('/api/v1/routes/quote', { params: { from, to, amountIn } })
