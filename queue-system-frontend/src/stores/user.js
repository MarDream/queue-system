import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import router from '../router'

const SESSION_TIMEOUT_MS = 60 * 60 * 1000 // 1小时
const SESSION_CHECK_INTERVAL_MS = 60 * 1000 // 每分钟检测一次
const SESSION_ACTIVITY_KEY = 'lastActivityTime'
const SESSION_ACTIVITY_WRITE_INTERVAL_MS = 30 * 1000 // 最多每30秒落盘一次

function normalizeToken(raw) {
  if (!raw) return ''
  const trimmed = String(raw).trim()
  if (!trimmed) return ''
  if (trimmed === 'undefined' || trimmed === 'null') return ''
  return trimmed
}

export const useUserStore = defineStore('user', () => {
  const token = ref(normalizeToken(localStorage.getItem('token')))
  const userId = ref(localStorage.getItem('userId') || null)
  const username = ref(localStorage.getItem('username') || '')
  const name = ref(localStorage.getItem('name') || '')
  const role = ref(localStorage.getItem('role') || '')
  const regionId = ref(localStorage.getItem('regionId') || null)
  const regionCode = ref(localStorage.getItem('regionCode') || null)
  const regionName = ref(localStorage.getItem('regionName') || '')
  const menuPaths = ref(JSON.parse(localStorage.getItem('menuPaths') || '[]'))
  const buttonCodes = ref(JSON.parse(localStorage.getItem('buttonCodes') || '[]'))

  let sessionCheckTimer = null
  let lastActivityWrittenAt = Number(localStorage.getItem(SESSION_ACTIVITY_KEY) || 0) || 0

  const isLoggedIn = computed(() => !!token.value)
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const isRegionAdmin = computed(() => role.value === 'REGION_ADMIN')
  const isWindowOperator = computed(() => role.value === 'WINDOW_OPERATOR')

  function setStorageValue(key, value) {
    if (value == null) {
      if (localStorage.getItem(key) !== null) {
        localStorage.removeItem(key)
      }
      return
    }
    const nextValue = String(value)
    if (localStorage.getItem(key) !== nextValue) {
      localStorage.setItem(key, nextValue)
    }
  }

  function setStorageJson(key, value) {
    const nextValue = JSON.stringify(value ?? [])
    if (localStorage.getItem(key) !== nextValue) {
      localStorage.setItem(key, nextValue)
    }
  }

  function setUser(userData) {
    token.value = normalizeToken(userData.token)
    userId.value = userData.userId
    username.value = userData.username
    name.value = userData.name
    role.value = userData.role
    regionId.value = userData.regionId || null
    regionCode.value = userData.regionCode || null
    regionName.value = userData.regionName || ''
    menuPaths.value = userData.menuPaths || []
    buttonCodes.value = userData.buttonCodes || []

    // Save to localStorage
    if (token.value) {
      setStorageValue('token', token.value)
    } else {
      setStorageValue('token', null)
    }
    setStorageValue('userId', userData.userId)
    setStorageValue('username', userData.username || '')
    setStorageValue('name', userData.name || '')
    setStorageValue('role', userData.role || '')
    if (userData.regionId) {
      setStorageValue('regionId', userData.regionId)
    } else {
      setStorageValue('regionId', null)
    }
    if (userData.regionCode) {
      setStorageValue('regionCode', userData.regionCode)
    } else {
      setStorageValue('regionCode', null)
    }
    setStorageValue('regionName', userData.regionName || '')
    setStorageJson('menuPaths', userData.menuPaths || [])
    setStorageJson('buttonCodes', userData.buttonCodes || [])

    // 登录成功后启动 session 超时检测
    startSessionMonitor()
  }

  function recordActivity(force = false) {
    if (!token.value) return
    const now = Date.now()
    if (!force && lastActivityWrittenAt && now - lastActivityWrittenAt < SESSION_ACTIVITY_WRITE_INTERVAL_MS) {
      return
    }
    lastActivityWrittenAt = now
    setStorageValue(SESSION_ACTIVITY_KEY, now)
  }

  function checkSessionTimeout() {
    const lastActivity = localStorage.getItem(SESSION_ACTIVITY_KEY)
    if (!lastActivity) {
      return
    }

    const elapsed = Date.now() - parseInt(lastActivity, 10)
    if (elapsed >= SESSION_TIMEOUT_MS) {
      console.log('[Session] 1小时无操作，session已过期')
      ElMessage.warning('登录已过期，请重新登录')
      logout(true)
    }
  }

  function startSessionMonitor() {
    stopSessionMonitor()
    // 立即记录活动时间
    recordActivity(true)
    // 每分钟检测一次
    sessionCheckTimer = setInterval(checkSessionTimeout, SESSION_CHECK_INTERVAL_MS)
  }

  function stopSessionMonitor() {
    if (sessionCheckTimer) {
      clearInterval(sessionCheckTimer)
      sessionCheckTimer = null
    }
  }

  function logout(isSessionExpired = false) {
    stopSessionMonitor()
    lastActivityWrittenAt = 0
    token.value = ''
    userId.value = null
    username.value = ''
    name.value = ''
    role.value = ''
    regionId.value = null
    regionCode.value = null
    regionName.value = ''
    menuPaths.value = []
    buttonCodes.value = []

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('name')
    localStorage.removeItem('role')
    localStorage.removeItem('regionId')
    localStorage.removeItem('regionCode')
    localStorage.removeItem('regionName')
    localStorage.removeItem('menuPaths')
    localStorage.removeItem('buttonCodes')
    localStorage.removeItem(SESSION_ACTIVITY_KEY)

    if (!isSessionExpired) {
      router.push('/login')
    }
  }

  function hasButtonPermission(buttonCode) {
    return buttonCodes.value.includes(buttonCode)
  }

  function hasMenuPermission(menuPath) {
    return menuPaths.value.includes(menuPath)
  }

  return {
    token,
    userId,
    username,
    name,
    role,
    regionId,
    regionCode,
    regionName,
    menuPaths,
    buttonCodes,
    isLoggedIn,
    isSuperAdmin,
    isRegionAdmin,
    isWindowOperator,
    setUser,
    logout,
    recordActivity,
    startSessionMonitor,
    stopSessionMonitor,
    hasButtonPermission,
    hasMenuPermission
  }
})
