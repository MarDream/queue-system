<template>
  <div class="scanline-overlay"></div>
  <router-view />
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useUserStore } from './stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

// 记录用户活动的函数
function recordActivity() {
  if (userStore.isLoggedIn) {
    userStore.recordActivity()
  }
}

// 监听用户活动事件
function setupActivityListeners() {
  const events = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click']
  events.forEach(event => {
    document.addEventListener(event, recordActivity, { passive: true })
  })
}

// 移除活动监听器
function removeActivityListeners() {
  const events = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click']
  events.forEach(event => {
    document.removeEventListener(event, recordActivity)
  })
}

// Logout function exposed globally
window.logout = function() {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  // 页面加载时，如果有 token（从 localStorage 恢复），启动 session 检测
  if (userStore.isLoggedIn) {
    userStore.startSessionMonitor()
    setupActivityListeners()
  }
})

onUnmounted(() => {
  removeActivityListeners()
})
</script>
