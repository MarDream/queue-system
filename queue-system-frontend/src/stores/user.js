import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(localStorage.getItem('userId') || null)
  const username = ref(localStorage.getItem('username') || '')
  const name = ref(localStorage.getItem('name') || '')
  const role = ref(localStorage.getItem('role') || '')
  const regionId = ref(localStorage.getItem('regionId') || null)
  const regionCode = ref(localStorage.getItem('regionCode') || null)
  const regionName = ref(localStorage.getItem('regionName') || '')
  const menuPaths = ref(JSON.parse(localStorage.getItem('menuPaths') || '[]'))
  const buttonCodes = ref(JSON.parse(localStorage.getItem('buttonCodes') || '[]'))

  const isLoggedIn = computed(() => !!token.value)
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const isRegionAdmin = computed(() => role.value === 'REGION_ADMIN')
  const isWindowOperator = computed(() => role.value === 'WINDOW_OPERATOR')

  function setUser(userData) {
    token.value = userData.token
    userId.value = userData.userId
    username.value = userData.username
    name.value = userData.name
    role.value = userData.role
    regionId.value = userData.regionId || null
    regionCode.value = userData.regionCode || userData.regionId || null
    regionName.value = userData.regionName || ''
    menuPaths.value = userData.menuPaths || []
    buttonCodes.value = userData.buttonCodes || []

    // Save to localStorage
    localStorage.setItem('token', userData.token)
    localStorage.setItem('userId', userData.userId)
    localStorage.setItem('username', userData.username)
    localStorage.setItem('name', userData.name)
    localStorage.setItem('role', userData.role)
    if (userData.regionId) {
      localStorage.setItem('regionId', userData.regionId)
    }
    if (userData.regionCode) {
      localStorage.setItem('regionCode', userData.regionCode)
    } else {
      localStorage.removeItem('regionCode')
    }
    localStorage.setItem('regionName', userData.regionName || '')
    localStorage.setItem('menuPaths', JSON.stringify(userData.menuPaths || []))
    localStorage.setItem('buttonCodes', JSON.stringify(userData.buttonCodes || []))
  }

  function logout() {
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
    hasButtonPermission,
    hasMenuPermission
  }
})
