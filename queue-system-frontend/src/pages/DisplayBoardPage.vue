<template>
  <div class="queue-board">
    <header class="board-header">
      <div class="header-left">
        <h1 class="region-name">{{ regionName || '叫号服务' }}</h1>
        <p class="region-slogan">智慧排队 服务民生</p>
      </div>
      <div class="header-right">
        <div class="time-box">
          <span class="time-date">{{ currentDate }} {{ weekDay }}</span>
          <span class="time-clock">{{ currentTime }}</span>
        </div>
      </div>
    </header>

    <main class="board-main">
      <section class="panel calling-panel">
        <div class="panel-header">
          <span class="panel-icon">🔔</span>
          <span class="panel-title">正在呼号（<span class="count-num">{{ callingQueue.length }}</span>人）</span>
        </div>
        <div class="panel-body">
          <div class="calling-table" v-if="displayCalling.length">
            <div class="calling-table__head">
              <span>票号</span>
              <span>窗口</span>
            </div>
            <div class="calling-table__body">
              <div class="calling-row" v-for="item in displayCalling" :key="`${item.ticketNo}-${item.windowName}`">
                <span class="calling-ticket">{{ formatTicketNo(item.ticketNo) }}</span>
                <span class="calling-window">{{ item.windowName || '--' }}</span>
              </div>
            </div>
          </div>
          <div class="panel-empty" v-else>
            <div class="empty-icon">📋</div>
            <div class="empty-text">暂无呼号</div>
          </div>
        </div>
      </section>

      <section class="panel waiting-panel">
        <div class="panel-header">
          <span class="panel-icon">⏳</span>
          <span class="panel-title">等候中（<span class="count-num">{{ waitingQueue.length }}</span>人）</span>
        </div>
        <div class="panel-body">
          <div class="waiting-grid" v-if="displayWaiting.length">
            <div class="waiting-card" v-for="item in displayWaiting" :key="item.id">
              <div class="waiting-ticket">{{ formatTicketNo(item.ticketNo) }}</div>
              <div class="waiting-business" v-if="item.businessType">{{ item.businessType }}</div>
            </div>
          </div>
          <div class="panel-empty" v-else>
            <div class="empty-icon">📋</div>
            <div class="empty-text">暂无人等候</div>
          </div>
        </div>
      </section>

      <aside class="side-column">
        <section class="panel qrcode-panel">
          <div class="panel-header">
            <span class="panel-icon">📱</span>
            <span class="panel-title">取号二维码</span>
          </div>
          <div class="panel-body qrcode-body">
            <div class="qrcode-box" v-if="qrcodeUrl">
              <img :src="qrcodeUrl" alt="取号二维码" class="qrcode-img" />
            </div>
            <div class="panel-empty" v-else>
              <div class="empty-icon">📱</div>
              <div class="empty-text">二维码加载中...</div>
            </div>
          </div>
        </section>

        <section class="panel skipped-panel">
          <div class="panel-header">
            <span class="panel-icon">⏭</span>
            <span class="panel-title">已过号（<span class="count-num">{{ skippedQueue.length }}</span>人）</span>
          </div>
          <div class="panel-body">
            <div class="skipped-list" v-if="displaySkipped.length">
              <div class="skipped-row" v-for="item in displaySkipped" :key="`${item.ticketNo}-${item.windowName}`">
                <span class="skipped-ticket">{{ formatTicketNo(item.ticketNo) }}</span>
                <span class="skipped-window">{{ item.windowName || '--' }}</span>
              </div>
            </div>
            <div class="panel-empty" v-else>
              <div class="empty-icon">📋</div>
              <div class="empty-text">暂无过号</div>
            </div>
          </div>
        </section>
      </aside>
    </main>

    <footer class="board-footer">
      <div class="notice-bar" v-if="notice">
        <span class="notice-icon">📢</span>
        <div class="notice-scroll"><span class="notice-text">{{ notice }}</span></div>
      </div>
      <div class="notice-bar empty" v-else>欢迎光临 {{ regionName || '本服务大厅' }}</div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { getScreenData } from '../api/screen'

const route = useRoute()

const currentTime = ref('')
const currentDate = ref('')
const weekDay = ref('')
const regionName = ref('')
const notice = ref('')
const qrcodeUrl = ref('')

const callingQueue = ref([])
const waitingQueue = ref([])
const skippedQueue = ref([])
const currentQrRegionId = ref(null)

const displayCalling = computed(() => callingQueue.value.slice(0, 8))
const displayWaiting = computed(() => waitingQueue.value.slice(0, 24))
const displaySkipped = computed(() => skippedQueue.value.slice(0, 6))

function formatTicketNo(no) {
  if (!no) return ''
  // 核心逻辑：去掉票号开头的所有纯数字（行政区划代码）
  // 无论行政区划代码是6位、8位还是其他长度，都将其剥离，仅保留业务字母及序号
  return no.replace(/^\d+/, '')
}

function updateClock() {
  const now = new Date()
  const weekdays = ['周日','周一','周二','周三','周四','周五','周六']
  weekDay.value = weekdays[now.getDay()]
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2,'0')
  const d = String(now.getDate()).padStart(2,'0')
  currentDate.value = `${y}-${m}-${d}`
  const h = String(now.getHours()).padStart(2,'0')
  const mi = String(now.getMinutes()).padStart(2,'0')
  const s = String(now.getSeconds()).padStart(2,'0')
  currentTime.value = `${h}:${mi}:${s}`
}

async function fetchData() {
  try {
    const regionCodeQuery = route.query.region || ''
    const data = await getScreenData({ regionCode: regionCodeQuery })

    if (data.regionName) regionName.value = data.regionName
    if (data.announcementText) notice.value = data.announcementText

    // 正在呼号 (called / serving)
    callingQueue.value = (data.currentCalls || []).map(c => ({
      ticketNo: c.ticketNo,
      windowName: c.counterName || '',
      businessType: c.businessTypeName || ''
    }))

    // 等候
    waitingQueue.value = (data.waitingQueue || []).map((t, i) => ({
      id: t.id || i,
      ticketNo: t.ticketNo,
      windowName: '',
      businessType: t.businessTypeName || ''
    }))

    // 过号 (从 recentCalls 里取)
    const recent = data.recentCalls || []
    skippedQueue.value = recent
      .filter(r => r.ticketNo && r.ticketNo)
      .slice(0, 10)
      .map(r => ({ ticketNo: r.ticketNo, windowName: r.counterName || '' }))

    // 获取二维码
    if (data.regionId && currentQrRegionId.value !== data.regionId) {
      fetchQrCode(data.regionId)
    }
  } catch(e) {
    console.warn('fetchData failed:', e)
  }
}

async function fetchQrCode(regionId) {
  if (!regionId) return
  try {
    if (qrcodeUrl.value) {
      URL.revokeObjectURL(qrcodeUrl.value)
      qrcodeUrl.value = ''
    }
    // 直接请求后端二维码接口，不走封装的request（会unwrap失败）
    const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
    const qrResp = await axios.get(`${baseUrl}/api/v1/qrcode/generate`, {
      params: { regionId: regionId, size: 200, baseUrl: window.location.origin },
      responseType: 'blob'
    })
    qrcodeUrl.value = URL.createObjectURL(qrResp.data)
    currentQrRegionId.value = regionId
  } catch(e) {
    console.warn('fetchQrCode failed:', e)
  }
}

let clockTimer, pollTimer
onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  fetchData()
  pollTimer = setInterval(fetchData, 5000)
})
onUnmounted(() => {
  clearInterval(clockTimer)
  clearInterval(pollTimer)
  if (qrcodeUrl.value) {
    URL.revokeObjectURL(qrcodeUrl.value)
  }
})
</script>

<style scoped>
.queue-board {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0d1b3a;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
}

.board-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 32px;
  background: #0052d4;
  color: #fff;
  flex-shrink: 0;
}

.header-left {}
.region-name {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  letter-spacing: 2px;
}
.region-slogan {
  font-size: 12px;
  opacity: 0.8;
  margin: 2px 0 0;
}
.time-box {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
}
.time-date { opacity: 0.9; }
.time-clock {
  font-size: 22px;
  font-weight: 700;
  font-family: 'Courier New', monospace;
}

.board-main {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(420px, 1.25fr) minmax(220px, 0.85fr);
  gap: 12px;
  padding: 12px;
  min-height: 0;
  overflow: hidden;
}

.side-column {
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(210px, 0.9fr) minmax(210px, 1.02fr);
  gap: 12px;
}

.panel {
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(12px);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 14px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}
.panel-icon { font-size: 18px; }
.panel-title {
  flex: 1;
  text-align: center;
  font-size: 23px;
  font-weight: 800;
  line-height: 1.2;
}
.count-num { color: #1e293b; font-weight: 800; }
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  min-height: 0;
}

.calling-panel .panel-header { background: #e8f0fe; color: #0052d4; }
.waiting-panel .panel-header { background: #f3f0ff; color: #7c3aed; }
.skipped-panel .panel-header { background: #fef3c7; color: #d97706; }
.qrcode-panel .panel-header { background: #10b981; color: #fff; }

.calling-panel .panel-body {
  display: flex;
  align-items: stretch;
  justify-content: center;
}

.calling-table {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.calling-table__head,
.calling-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  text-align: center;
}

.calling-table__head {
  padding: 4px 0 14px;
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}

.calling-table__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  justify-content: flex-start;
  overflow-y: auto;
}

.calling-row {
  padding: 12px 8px;
  font-size: 26px;
  line-height: 1.2;
  border-radius: 6px;
  background: rgba(30, 64, 175, 0.72);
}

.calling-ticket {
  font-weight: 700;
  color: #ffffff;
}

.calling-window {
  font-weight: 700;
  color: #dbeafe;
}

.waiting-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-auto-rows: minmax(80px, 1fr);
  gap: 12px;
  align-content: start;
  overflow-y: auto;
}

.waiting-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  padding: 10px;
  background: #f5f3ff;
}

.waiting-ticket {
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  color: #1e293b;
}

.waiting-business {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
  text-align: center;
}

.qrcode-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.qrcode-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qrcode-img {
  width: 247px;
  height: 247px;
  border-radius: 12px;
  background: #fff;
  padding: 10px;
}

.skipped-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skipped-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border-radius: 4px;
  background: #fef9c3;
}

.skipped-ticket {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.skipped-window {
  font-size: 14px;
  color: #64748b;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  height: 100%;
  gap: 8px;
  color: #94a3b8;
}

.empty-icon { font-size: 32px; opacity: 0.5; }
.empty-text { font-size: 14px; }

.board-footer {
  flex-shrink: 0;
  background: #1e293b;
  color: #94a3b8;
  padding: 8px 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.notice-bar { display: flex; align-items: center; gap: 8px; width: 100%; }
.notice-icon { font-size: 16px; }
.notice-scroll {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
}

.notice-text {
  display: inline-block;
  animation: notice-scroll 20s linear infinite;
  color: #94a3b8;
}

@keyframes notice-scroll {
  0% {
    transform: translateX(100%);
  }
  100% {
    transform: translateX(-100%);
  }
}

.notice-bar.empty { justify-content: center; }

@media (max-width: 1440px) {
  .board-header {
    padding: 12px 20px;
  }

  .region-name {
    font-size: 20px;
  }

  .time-clock {
    font-size: 20px;
  }

  .board-main {
    grid-template-columns: minmax(220px, 0.88fr) minmax(320px, 1.2fr) minmax(200px, 0.82fr);
  }

  .side-column {
    grid-template-rows: minmax(190px, 0.86fr) minmax(190px, 1fr);
  }

  .calling-table__head {
    font-size: 16px;
  }

  .calling-row,
  .waiting-ticket {
    font-size: 22px;
  }

  .skipped-ticket {
    font-size: 16px;
  }

  .skipped-window {
    font-size: 13px;
  }
}
</style>
