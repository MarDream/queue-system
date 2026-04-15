import api from './index'

export function getAppointments(params) {
  return api.get('/api/v1/appointment/list', { params })
}

export function getAppointmentDetail(id) {
  return api.get(`/api/v1/appointment/${id}`)
}

export function createAppointment(data) {
  return api.post('/api/v1/appointment/create', data)
}

export function cancelAppointment(id) {
  return api.post(`/api/v1/appointment/${id}/cancel`)
}
