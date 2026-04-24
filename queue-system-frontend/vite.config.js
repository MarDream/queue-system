import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import os from 'os'

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

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  // 优先使用环境变量 VITE_SERVER_IP，未配置则自动获取
  const serverIp = env.VITE_SERVER_IP || getLocalIp()
  const backendPort = env.VITE_BACKEND_PORT || 8888
  const frontendPort = env.VITE_FRONTEND_PORT || 5173
  const source = env.VITE_SERVER_IP ? 'configured' : 'auto-detect'

  console.log('========================================')
  console.log(`  Server IP  : ${serverIp} (${source})`)
  console.log(`  Frontend   : http://${serverIp}:${frontendPort}`)
  console.log(`  API Proxy  : http://${serverIp}:${backendPort}`)
  console.log('========================================')

  return {
    plugins: [vue()],
    server: {
      port: parseInt(frontendPort),
      host: '0.0.0.0',
      proxy: {
        '/api': {
          target: `http://${serverIp}:${backendPort}`,
          changeOrigin: true
        }
      }
    }
  }
})
