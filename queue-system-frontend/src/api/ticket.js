import request from './index'

/**
 * Take a new ticket.
 * @param {{ businessTypeId: number, phone?: string, source?: string }} data
 * @returns {Promise<Object>} created ticket
 */
export function takeTicket(data) {
  return request.post('/ticket/take', data)
}

/**
 * Cancel an existing ticket.
 * @param {{ ticketNo: string }} data
 * @returns {Promise<void>}
 */
export function cancelTicket(data) {
  return request.post('/ticket/cancel', data)
}

/**
 * Query queue status for a ticket.
 * @param {string} ticketNo
 * @returns {Promise<Object>} ticket with current status
 */
export function getQueueStatus(ticketNo) {
  return request.get('/queue/status', { params: { ticketNo } })
}
