function toValidDate(value) {
  if (!value) return null

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }

  if (Array.isArray(value)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = value
    const date = new Date(year, month - 1, day, hour, minute, second)
    return Number.isNaN(date.getTime()) ? null : date
  }

  if (typeof value === 'object') {
    const year = value.year
    if (year != null) {
      const month = value.monthValue ?? value.month ?? 1
      const day = value.dayOfMonth ?? value.day ?? 1
      const hour = value.hour ?? value.hours ?? 0
      const minute = value.minute ?? value.minutes ?? 0
      const second = value.second ?? value.seconds ?? 0
      const date = new Date(year, month - 1, day, hour, minute, second)
      return Number.isNaN(date.getTime()) ? null : date
    }
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value) {
  return String(value).padStart(2, '0')
}

export function formatDateTime(value, emptyText = '—') {
  const date = toValidDate(value)
  if (!date) return emptyText

  const year = date.getFullYear()
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hour = pad(date.getHours())
  const minute = pad(date.getMinutes())
  const second = pad(date.getSeconds())

  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

export function normalizeDateTime(value) {
  return toValidDate(value)
}
