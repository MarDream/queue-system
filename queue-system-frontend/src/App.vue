<template>
  <div class="scanline-overlay"></div>
  <router-view />
</template>

<script setup>
import { useUserStore } from './stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

// Logout function exposed globally
window.logout = function() {
  userStore.logout()
  router.push('/login')
}
</script>
