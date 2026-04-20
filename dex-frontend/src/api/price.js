import http from './http'

export const getPrice = (pair) => http.get(`/api/v1/prices/${pair}`)
export const getPrices = () => http.get('/api/v1/prices')
export const getPriceHistory = (pair) => http.get(`/api/v1/prices/${pair}/history`)
export const getCandles = (pair, minutes = 5) => http.get(`/api/v1/prices/${pair}/candles`, { params: { minutes } })
export const getAnomalies = (pair, threshold = 1.0) => http.get(`/api/v1/prices/${pair}/anomalies`, { params: { threshold } })
