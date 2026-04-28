const TICKET_STATUS_TEXT_MAP = {
  waiting: '等待中',
  called: '已叫号',
  serving: '服务中',
  completed: '已完成',
  cancelled: '已取消',
  skipped: '已跳过'
}

export function normalizeTicketStatus(status) {
  return String(status || '').trim().toLowerCase()
}

export function getTicketStatusText(status) {
  const key = normalizeTicketStatus(status)
  return TICKET_STATUS_TEXT_MAP[key] || status || '—'
}

export function isActiveTicketStatus(status) {
  const key = normalizeTicketStatus(status)
  return key === 'waiting' || key === 'called' || key === 'serving'
}
