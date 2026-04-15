import request from './index'

/**
 * Get all available business types.
 * @returns {Promise<Array>}
 */
export function getBusinessTypes() {
  return request.get('/business-types')
}
