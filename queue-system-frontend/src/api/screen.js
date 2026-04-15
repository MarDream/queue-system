import request from './index'

/**
 * Get current display board data (active ticket, waiting queue snapshot).
 * @returns {Promise<Object>}
 */
export function getScreenData(params) {
  return request.get('/queue/screen', { params })
}

/**
 * Get admin dashboard statistics.
 * @returns {Promise<Object>}
 */
export function getDashboard(params) {
  return request.get('/admin/dashboard', { params })
}

/**
 * Get ticket list for admin (supports filtering by status, date, and search conditions).
 * @param {{ status?: string, date?: string, startDate?: string, endDate?: string, phone?: string, name?: string, ticketNo?: string }} params
 * @returns {Promise<Object[]>}
 */
export function getTicketList(params) {
  return request.get('/admin/tickets', { params })
}

/**
 * Get business type detail stats: region, counter, operator, ticket count.
 * @param {number} businessTypeId
 * @param {object} params
 * @returns {Promise<Object[]>}
 */
export function getBusinessTypeDetail(businessTypeId, params) {
  return request.get(`/admin/business-types/${businessTypeId}/detail`, { params })
}
