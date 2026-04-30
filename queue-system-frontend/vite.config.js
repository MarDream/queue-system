import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import os from 'os'
import fs from 'fs'
import path from 'path'

function getLocalIp() {
  const interfaces = os.networkInterfaces()
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal && !iface.address.startsWith('172.')) {
        return iface.address
      }
    }
  }
  return '127.0.0.1'
}

function resolveAppVersion(env) {
  const explicitVersion = (env.VITE_APP_VERSION || process.env.VITE_APP_VERSION || '').trim()
  if (explicitVersion) {
    return explicitVersion
  }

  const versionPath = path.resolve(process.cwd(), '../VERSION')
  try {
    const fileVersion = fs.readFileSync(versionPath, 'utf8').trim()
    return fileVersion || 'dev'
  } catch {
    return 'dev'
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const appVersion = resolveAppVersion(env)

  // 对外访问地址可自动探测，但本地开发代理默认回环到本机后端
  const publicHost = env.VITE_SERVER_IP || getLocalIp()
  const backendHost = env.VITE_BACKEND_HOST || '127.0.0.1'
  const backendPort = env.VITE_BACKEND_PORT || 8080
  const frontendPort = env.VITE_FRONTEND_PORT || 5173
  const source = env.VITE_SERVER_IP ? 'configured' : 'auto-detect'

  console.log('========================================')
  console.log(`  Server IP  : ${publicHost} (${source})`)
  console.log(`  Frontend   : http://${publicHost}:${frontendPort}`)
  console.log(`  API Proxy  : http://${backendHost}:${backendPort}`)
  console.log('========================================')

  return {
    plugins: [vue()],
    define: {
      __APP_VERSION__: JSON.stringify(appVersion)
    },
    server: {
      port: parseInt(frontendPort),
      host: '0.0.0.0',
      proxy: {
        '/api': {
          target: `http://${backendHost}:${backendPort}`,
          changeOrigin: true
        }
      }
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            if (id.includes('element-plus')) return 'element-plus'
            if (id.includes('@element-plus/icons-vue')) return 'element-plus-icons'
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'vue-core'
            if (id.includes('sortablejs')) return 'sortable'
            if (id.includes('qrcode')) return 'qrcode'
            return 'vendor'
          }
        }
      }
    }
  }
})
