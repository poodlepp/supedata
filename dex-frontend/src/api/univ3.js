import axios from 'axios'

const API_BASE = '/api/univ3'

const uniV3Api = {
  getSummary() {
    return axios.get(`${API_BASE}/summary`).then(res => res.data.data)
  },

  getEvents(params = {}) {
    return axios.get(`${API_BASE}/events`, { params }).then(res => res.data.data)
  }
}

export default uniV3Api
