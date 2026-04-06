import http from './http'

export const getPrice = (pair) => http.get(`/api/v1/prices/${pair}`)
export const getPrices = () => http.get('/api/v1/prices')
