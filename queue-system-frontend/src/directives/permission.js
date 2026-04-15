import { useUserStore } from '../stores/user'

export default {
  mounted(el, binding) {
    const userStore = useUserStore()
    const permission = binding.value

    if (!permission) return

    // permission can be a string or array of strings
    const permissions = Array.isArray(permission) ? permission : [permission]

    const hasPermission = permissions.some(p => {
      // Check button permission
      if (p.startsWith('btn:')) {
        return userStore.hasButtonPermission(p)
      }
      // Check menu permission
      return userStore.hasMenuPermission(p)
    })

    if (!hasPermission) {
      el.parentNode?.removeChild(el)
    }
  }
}
