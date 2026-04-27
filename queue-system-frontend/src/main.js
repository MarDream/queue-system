import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles/global.css'
import App from './App.vue'
import router from './router/index.js'
import permissionDirective from './directives/permission.js'

if (typeof window !== 'undefined') {
  const shouldSuppressConnectionNoise = (value) => {
    return String(value || '').includes('Could not establish connection. Receiving end does not exist')
  }

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event?.reason
    const message = typeof reason === 'string' ? reason : (reason?.message || '')
    if (
      shouldSuppressConnectionNoise(message) ||
      shouldSuppressConnectionNoise(reason) ||
      shouldSuppressConnectionNoise(reason?.message) ||
      shouldSuppressConnectionNoise(reason?.toString?.())
    ) {
      event.preventDefault()
      event.stopImmediatePropagation?.()
    }
  }, true)

  window.addEventListener('error', (event) => {
    if (
      shouldSuppressConnectionNoise(event?.message) ||
      shouldSuppressConnectionNoise(event?.error) ||
      shouldSuppressConnectionNoise(event?.error?.message)
    ) {
      event.preventDefault()
    }
  }, true)
}

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('permission', permissionDirective)
app.mount('#app')
