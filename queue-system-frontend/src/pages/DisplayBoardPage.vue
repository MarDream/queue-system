<template>
  <div class="display-shell" :style="{ fontSize: fontSize + 'px' }">
    <!-- Header -->
    <header class="display-header">
      <div class="header-left">
        <div class="logo-area">
          <svg class="logo-icon" viewBox="0 0 24 24" fill="none">
            <rect x="3" y="3" width="7" height="7" rx="1" fill="currentColor" opacity="0.9"/>
            <rect x="14" y="3" width="7" height="7" rx="1" fill="currentColor" opacity="0.7"/>
            <rect x="3" y="14" width="7" height="7" rx="1" fill="currentColor" opacity="0.7"/>
            <rect x="14" y="14" width="7" height="7" rx="1" fill="currentColor" opacity="0.5"/>
          </svg>
          <div class="brand-text">
            <span class="brand-title">智慧排队系统</span>
            <span class="brand-subtitle">{{ regionName || '叫号大屏' }}</span>
          </div>
        </div>
      </div>

      <div class="header-center">
        <div class="time-box">
          <span class="time-week">{{ weekDay }}</span>
          <span class="time-date">{{ currentDate }}</span>
          <span class="time-clock">{{ currentTime }}</span>
        </div>
      </div>

      <div class="header-right">
        <div v-if="qrCodeUrl" class="qr-box" @click="previewQr">
          <div class="qr-label">扫码取号</div>
          <canvas ref="qrCanvasRef" class="qr-canvas"></canvas>
        </div>
      </div>
    </header>

    <!-- Main Content: 3-zone layout -->
    <main class="display-main">
      <!-- Left Sidebar: Window Status (Carousel) -->
      <aside class="sidebar-left">
        <div class="sidebar-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <line x1="9" y1="3" x2="9" y2="21"/>
            <line x1="15" y1="3" x2="15" y2="21"/>
          </svg>
          窗口
          <span v-if="windowPageTotal > 1" class="carousel-indicator">{{ windowPageIndex + 1 }}/{{ windowPageTotal }}</span>
        </div>
        <div class="window-list" ref="windowListRef">
          <transition-group name="window-fade" tag="div" class="window-page">
            <div v-for="w in currentWindowPage" :key="w.id" class="window-row">
              <div class="window-row-top">
                <span class="window-name">{{ w.name }}</span>
                <span class="window-dot" :class="'dot-' + w.status"></span>
                <span class="window-status-text" :class="'state-' + w.status">{{ statusLabel(w.status) }}</span>
              </div>
              <div class="window-current" v-if="w.current">{{ w.current }}</div>
            </div>
          </transition-group>
          <div v-if="windows.length === 0" class="sidebar-empty">暂未设置窗口</div>
        </div>
      </aside>

      <!-- Center: Hero Call Display -->
      <section class="center-area">
        <div class="center-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <polygon points="12,2 22,8.5 22,15.5 12,22 2,15.5 2,8.5"/>
            <line x1="12" y1="22" x2="12" y2="15.5"/>
            <polyline points="22,8.5 12,15.5 2,8.5"/>
          </svg>
          正在叫号
        </div>
        <div class="hero-calls">
          <div
            v-for="call in currentCalls"
            :key="call.window"
            class="hero-card"
            :class="{ 'card-flash': call.isNew }"
          >
            <div class="hero-card-bar"></div>
            <div class="hero-bt-tag" v-if="call.businessType">{{ call.businessType }}</div>
            <div class="hero-ticket">{{ call.number }}</div>
            <div class="hero-counter">{{ call.window }}</div>
          </div>
          <div v-if="currentCalls.length === 0" class="hero-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span>暂无叫号</span>
          </div>
        </div>
      </section>

      <!-- Right Sidebar: Stats + Recent Calls -->
      <aside class="sidebar-right">
        <!-- Stats -->
        <div class="right-stats">
          <div class="mini-stat">
            <span class="mini-stat-num">{{ stats.waitingCount ?? 0 }}</span>
            <span class="mini-stat-label">等待</span>
          </div>
          <div class="mini-stat mini-green">
            <span class="mini-stat-num">{{ stats.completedCount ?? 0 }}</span>
            <span class="mini-stat-label">已完成</span>
          </div>
          <div class="mini-stat mini-orange">
            <span class="mini-stat-num">{{ stats.skippedCount ?? 0 }}</span>
            <span class="mini-stat-label">已过号</span>
          </div>
        </div>
        <div class="sidebar-title" style="margin-top:0.6rem">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12,6 12,12 16,14"/>
          </svg>
          最近
        </div>

        <!-- Recent Calls -->
        <div class="right-recent">
          <div class="recent-list" ref="recentListRef">
            <div v-for="log in currentRecentPage" :key="log.id" class="recent-row">
              <span class="recent-time">{{ log.time }}</span>
              <span class="recent-num">{{ log.number }}</span>
              <span class="recent-sep">→</span>
              <span class="recent-win">{{ log.window }}</span>
            </div>
            <div v-if="recentLogs.length === 0" class="sidebar-empty">暂无记录</div>
          </div>
        </div>
      </aside>
    </main>

    <!-- Bottom: Full-width Marquee Announcement -->
    <footer class="display-footer">
      <div class="marquee-bar" v-if="notice">
        <svg class="marquee-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 17H2a3 3 0 0 0 3-3V9a7 7 0 0 1 14 0v5a3 3 0 0 0 3 3zm-8.27 4a2 2 0 0 1-3.46 0"/>
        </svg>
        <div class="marquee-scroll">
          <span class="marquee-text">{{ notice }}</span>
        </div>
      </div>
      <div class="marquee-bar marquee-empty" v-else>
        <span class="marquee-placeholder">暂无公告</span>
      </div>
    </footer>

    <!-- QR Preview Dialog -->
    <el-dialog v-model="qrPreviewVisible" width="360px" title="扫码取号" align-center>
      <div class="qr-dialog-content">
        <canvas ref="qrPreviewCanvasRef" class="qr-dialog-canvas"></canvas>
        <p class="qr-dialog-tip">使用微信扫一扫</p>
        <p class="qr-dialog-url">{{ qrCodeUrl }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getScreenData } from '../api/screen'
import { useUserStore } from '../stores/user'
import QRCode from 'qrcode'
import request from '../api/index'

const userStore = useUserStore()
const route = useRoute()

const currentTime = ref('')
const currentDate = ref('')
const weekDay = ref('')
const currentCalls = ref([])
const windows = ref([])
const recentLogs = ref([])
const stats = ref({})
const notice = ref('')
const regionCode = ref('')
const regionName = ref('')
const qrCodeUrl = ref('')
const qrCanvasRef = ref(null)
const qrPreviewVisible = ref(false)
const qrPreviewCanvasRef = ref(null)
const windowListRef = ref(null)
const recentListRef = ref(null)

// 窗口轮播
const windowPageIndex = ref(0)
const windowPageSize = ref(6) // 根据高度自适应计算
const windowPageTotal = computed(() => Math.max(1, Math.ceil(windows.value.length / windowPageSize.value)))
const currentWindowPage = computed(() => {
  const start = windowPageIndex.value * windowPageSize.value
  return windows.value.slice(start, start + windowPageSize.value)
})

// 最近记录轮播
const recentPageIndex = ref(0)
const recentPageSize = ref(6)
const recentPageTotal = computed(() => Math.max(1, Math.ceil(recentLogs.value.length / recentPageSize.value)))
const currentRecentPage = computed(() => {
  const start = recentPageIndex.value * recentPageSize.value
  return recentLogs.value.slice(start, start + recentPageSize.value)
})

/**
 * 根据容器高度计算每页可显示的行数
 */
function calcPageSizes() {
  // 窗口列表：每行约 52px（含 gap）
  if (windowListRef.value) {
    const h = windowListRef.value.clientHeight
    windowPageSize.value = Math.max(1, Math.floor(h / 52))
  }
  // 最近列表：每行约 34px（含 gap）
  if (recentListRef.value) {
    const h = recentListRef.value.clientHeight
    recentPageSize.value = Math.max(1, Math.floor(h / 34))
  }
  // 重置页码防止越界
  if (windowPageIndex.value >= windowPageTotal.value) windowPageIndex.value = 0
  if (recentPageIndex.value >= recentPageTotal.value) recentPageIndex.value = 0
}

const fontSize = ref(16)

function updateClock() {
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  weekDay.value = weekdays[now.getDay()]
  currentDate.value = `${now.getFullYear()}年${pad(now.getMonth() + 1)}月${pad(now.getDate())}日`
  currentTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  fontSize.value = Math.max(14, Math.min(18, window.innerWidth / 100))
}

function statusLabel(s) {
  return { serving: '服务中', calling: '叫号中', paused: '暂停', idle: '空闲' }[s] || s
}

/**
 * 格式化叫号时间，兼容后端返回的 HH:mm:ss 格式
 */
function formatCallTime(val) {
  if (!val) return '--:--'
  // 后端返回的是 HH:mm:ss 格式（如 "14:30:00"），直接截取 HH:mm
  if (/^\d{1,2}:\d{2}/.test(val)) {
    const parts = val.split(':')
    return parts[0].padStart(2, '0') + ':' + parts[1]
  }
  // 兜底：尝试用 Date 解析
  try {
    const d = new Date(val)
    if (!isNaN(d.getTime())) {
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }
  } catch {}
  return '--:--'
}

async function fetchScreenData() {
  try {
    const data = await getScreenData({ regionCode: regionCode.value || undefined })
    currentCalls.value = (data.currentCalls || []).map(c => ({
      window: c.counterName,
      number: c.ticketNo,
      businessType: c.businessTypeName || '',
      isNew: false,
    }))
    windows.value = (data.counters || []).map(c => ({
      id: c.name,
      name: c.name,
      status: c.status === 'idle' ? 'idle' : c.status === 'busy' ? 'serving' : 'paused',
      current: c.currentTicketNo || null,
    }))
    recentLogs.value = (data.recentCalls || []).map((r, i) => ({
      id: Date.now() + i,
      time: formatCallTime(r.calledAt),
      number: r.ticketNo,
      window: r.counterName,
    }))
    if (data.stats) {
      stats.value = {
        waitingCount: data.stats.waitingCount ?? 0,
        skippedCount: data.stats.skippedCount ?? 0,
        completedCount: data.stats.completedCount ?? 0,
      }
    }
    if (data.announcementText) {
      notice.value = data.announcementText
    }
    // 数据更新后重新计算分页并重置页码
    nextTick(() => {
      calcPageSizes()
      windowPageIndex.value = 0
      recentPageIndex.value = 0
    })
  } catch {
    // keep current data
  }
}

async function fetchQrCode() {
  if (!regionCode.value) return
  try {
    const res = await request.get('/qrcode/url', {
      params: {
        regionCode: regionCode.value,
        baseUrl: window.location.origin
      }
    })
    if (res && res.url) {
      qrCodeUrl.value = res.url
      regionName.value = res.regionName || ''
      await nextTick()
      await drawQrCode(qrCanvasRef.value, qrCodeUrl.value, 70)
    }
  } catch (e) {
    console.warn('获取二维码失败:', e)
  }
}

async function drawQrCode(canvas, url, size) {
  if (!canvas || !url) return
  try {
    await QRCode.toCanvas(canvas, url, {
      width: size,
      margin: 1,
      color: { dark: '#000000', light: '#ffffff' }
    })
  } catch (e) {
    console.error('绘制二维码失败:', e)
  }
}

async function previewQr() {
  qrPreviewVisible.value = true
  await nextTick()
  await drawQrCode(qrPreviewCanvasRef.value, qrCodeUrl.value, 280)
}

let clockTimer, pollTimer, carouselTimer
onMounted(() => {
  regionCode.value = route.query.region || ''
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  fetchScreenData()
  fetchQrCode()
  pollTimer = setInterval(fetchScreenData, 5000)
  // 首次计算分页大小
  nextTick(() => calcPageSizes())
  // 窗口尺寸变化时重新计算
  window.addEventListener('resize', calcPageSizes)
  // 轮播定时器：每 5 秒翻页
  carouselTimer = setInterval(() => {
    if (windowPageTotal.value > 1) {
      windowPageIndex.value = (windowPageIndex.value + 1) % windowPageTotal.value
    }
    if (recentPageTotal.value > 1) {
      recentPageIndex.value = (recentPageIndex.value + 1) % recentPageTotal.value
    }
  }, 5000)
})
onUnmounted(() => {
  clearInterval(clockTimer)
  clearInterval(pollTimer)
  clearInterval(carouselTimer)
  window.removeEventListener('resize', calcPageSizes)
})
</script>

<style scoped>
.display-shell {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(160deg, #0f1923 0%, #1a1f35 40%, #0d1117 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #e6edf3;
}

/* ===== HEADER ===== */
.display-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  background: linear-gradient(180deg, rgba(20, 25, 35, 0.98) 0%, rgba(15, 18, 25, 0.95) 100%);
  border-bottom: 1px solid rgba(0, 229, 255, 0.12);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.4);
  position: relative;
  z-index: 10;
  flex-shrink: 0;
}
.display-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, 0.4), transparent);
}

.header-left, .header-right {
  flex: 1;
}
.header-right {
  display: flex;
  justify-content: flex-end;
}
.header-center {
  flex: 0 0 auto;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.logo-icon {
  width: 40px;
  height: 40px;
  color: #00e5ff;
  filter: drop-shadow(0 0 12px rgba(0, 229, 255, 0.6));
}
.brand-text {
  display: flex;
  flex-direction: column;
}
.brand-title {
  font-size: 1.4em;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.1em;
  text-shadow: 0 0 20px rgba(0, 229, 255, 0.3);
}
.brand-subtitle {
  font-size: 0.75em;
  color: rgba(0, 229, 255, 0.7);
  letter-spacing: 0.15em;
}

.time-box {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.6rem 1.5rem;
  background: rgba(0, 229, 255, 0.06);
  border: 1px solid rgba(0, 229, 255, 0.2);
  border-radius: 8px;
  box-shadow: 0 0 25px rgba(0, 229, 255, 0.1), inset 0 0 20px rgba(0, 229, 255, 0.03);
}
.time-week {
  font-size: 0.85em;
  color: rgba(0, 229, 255, 0.8);
  padding-right: 1rem;
  border-right: 1px solid rgba(0, 229, 255, 0.2);
}
.time-date {
  font-size: 0.85em;
  color: rgba(230, 237, 243, 0.7);
  padding-right: 1rem;
  border-right: 1px solid rgba(0, 229, 255, 0.2);
}
.time-clock {
  font-size: 1.2em;
  font-weight: 700;
  color: #00e5ff;
  font-family: 'Share Tech Mono', monospace;
  letter-spacing: 0.05em;
  text-shadow: 0 0 15px rgba(0, 229, 255, 0.5);
}

.qr-box {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.5rem 1rem 0.5rem 0.5rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(0, 229, 255, 0.25);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.qr-box:hover {
  background: rgba(0, 229, 255, 0.08);
  border-color: rgba(0, 229, 255, 0.4);
  transform: translateY(-1px);
}
.qr-label {
  font-size: 0.8em;
  font-weight: 600;
  color: rgba(0, 229, 255, 0.9);
}
.qr-canvas {
  width: 60px;
  height: 60px;
  border-radius: 4px;
}

/* ===== MAIN: 3-column layout ===== */
.display-main {
  flex: 1;
  display: grid;
  grid-template-columns: 220px 1fr 260px;
  gap: 1rem;
  padding: 1rem 1.5rem;
  min-height: 0;
  overflow: hidden;
}

/* ===== SIDEBAR COMMON ===== */
.sidebar-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8em;
  font-weight: 600;
  color: rgba(0, 229, 255, 0.7);
  letter-spacing: 0.08em;
  margin-bottom: 0.8rem;
  text-transform: uppercase;
}
.sidebar-title svg { color: #00e5ff; }
.sidebar-empty {
  color: rgba(230, 237, 243, 0.25);
  font-size: 0.8em;
  text-align: center;
  padding: 1rem 0;
}

/* ===== LEFT SIDEBAR: Window Status ===== */
.sidebar-left {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.window-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  overflow-y: auto;
}
.window-row {
  padding: 0.6rem 0.7rem;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.window-row-top {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.window-name {
  font-size: 0.85em;
  font-weight: 600;
  color: rgba(0, 229, 255, 0.8);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.window-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-serving { background: #4caf50; box-shadow: 0 0 6px rgba(76, 175, 80, 0.6); }
.dot-idle { background: rgba(230, 237, 243, 0.3); }
.dot-paused { background: #ff9800; box-shadow: 0 0 6px rgba(255, 152, 0, 0.5); }
.window-status-text {
  font-size: 0.65em;
  padding: 0.1em 0.4em;
  border-radius: 3px;
  flex-shrink: 0;
}
.state-serving { background: rgba(76, 175, 80, 0.2); color: #4caf50; }
.state-idle { background: rgba(255, 255, 255, 0.05); color: rgba(230, 237, 243, 0.35); }
.state-paused { background: rgba(255, 152, 0, 0.2); color: #ff9800; }
.window-current {
  font-size: 0.8em;
  font-weight: 600;
  color: #fff;
  padding-left: 0.2rem;
}

/* 轮播指示器 */
.carousel-indicator {
  margin-left: auto;
  font-size: 0.75em;
  color: rgba(0, 229, 255, 0.5);
  font-weight: 400;
  letter-spacing: 0;
}
/* 窗口轮播容器 */
.window-page {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
}
/* 轮播过渡 */
.window-fade-enter-active,
.window-fade-leave-active {
  transition: opacity 0.4s ease;
}
.window-fade-enter-from,
.window-fade-leave-to {
  opacity: 0;
}

/* ===== CENTER AREA ===== */
.center-area {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.center-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9em;
  font-weight: 600;
  color: rgba(0, 229, 255, 0.8);
  letter-spacing: 0.1em;
  margin-bottom: 1rem;
  text-transform: uppercase;
  flex-shrink: 0;
}
.center-title svg { color: #00e5ff; }
.hero-calls {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 1.2rem;
  align-content: flex-start;
  overflow-y: auto;
  min-height: 0;
}
.hero-card {
  flex: 1 1 calc(50% - 0.6rem);
  min-width: 200px;
  padding: 2rem 1.5rem 1.5rem;
  background: linear-gradient(145deg, rgba(0, 229, 255, 0.08) 0%, rgba(0, 151, 167, 0.03) 100%);
  border: 1px solid rgba(0, 229, 255, 0.2);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 229, 255, 0.08), inset 0 0 30px rgba(0, 229, 255, 0.02);
  transition: all 0.3s;
}
.hero-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at top, rgba(0, 229, 255, 0.1) 0%, transparent 60%);
  pointer-events: none;
}
.hero-card-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #00e5ff, #00b8d4, #00e5ff);
  background-size: 200% 100%;
  animation: shimmer 2s linear infinite;
}
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
.hero-bt-tag {
  position: absolute;
  top: 0.6rem;
  left: 0.8rem;
  font-size: 0.6em;
  font-weight: 600;
  color: rgba(0, 229, 255, 0.8);
  background: rgba(0, 229, 255, 0.1);
  padding: 0.15em 0.6em;
  border-radius: 3px;
  z-index: 1;
}
.hero-ticket {
  font-size: 5em;
  font-weight: 700;
  color: #fff;
  line-height: 1;
  text-shadow: 0 0 40px rgba(0, 229, 255, 0.6), 0 0 80px rgba(0, 229, 255, 0.3);
  letter-spacing: 0.05em;
  position: relative;
  z-index: 1;
}
.hero-counter {
  font-size: 0.9em;
  font-weight: 600;
  color: rgba(0, 229, 255, 0.8);
  letter-spacing: 0.1em;
  margin-top: 0.8rem;
  position: relative;
  z-index: 1;
}
.card-flash {
  animation: pulse-glow 1.5s ease-out;
}
@keyframes pulse-glow {
  0% { box-shadow: 0 0 50px rgba(0, 229, 255, 0.6); }
  100% { box-shadow: 0 4px 20px rgba(0, 229, 255, 0.08); }
}
.hero-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 3rem;
  color: rgba(230, 237, 243, 0.25);
  font-size: 1em;
  width: 100%;
}
.hero-empty svg { opacity: 0.3; }

/* ===== RIGHT SIDEBAR ===== */
.sidebar-right {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-height: 0;
  overflow: hidden;
}

/* Stats */
.right-stats {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}
.mini-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.5rem 0.4rem;
  background: rgba(255, 193, 7, 0.06);
  border: 1px solid rgba(255, 193, 7, 0.12);
  border-radius: 8px;
}
.mini-stat-num {
  font-size: 1.5em;
  font-weight: 700;
  color: #ffc107;
  line-height: 1;
  text-shadow: 0 0 15px rgba(255, 193, 7, 0.3);
}
.mini-stat-label {
  font-size: 0.6em;
  color: rgba(230, 237, 243, 0.5);
  margin-top: 0.15rem;
}
.mini-green {
  background: rgba(76, 175, 80, 0.06) !important;
  border-color: rgba(76, 175, 80, 0.12) !important;
}
.mini-green .mini-stat-num {
  color: #4caf50 !important;
  text-shadow: 0 0 15px rgba(76, 175, 80, 0.3) !important;
}
.mini-orange {
  background: rgba(255, 152, 0, 0.06) !important;
  border-color: rgba(255, 152, 0, 0.12) !important;
}
.mini-orange .mini-stat-num {
  color: #ff9800 !important;
  text-shadow: 0 0 15px rgba(255, 152, 0, 0.3) !important;
}

/* Recent */
.right-recent {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.recent-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  overflow-y: auto;
  min-height: 0;
}
.recent-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.55rem;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 5px;
  font-size: 0.78em;
}
.recent-time { color: rgba(230, 237, 243, 0.35); flex-shrink: 0; font-variant-numeric: tabular-nums; }
.recent-num { color: #00e5ff; font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recent-sep { color: rgba(230, 237, 243, 0.2); flex-shrink: 0; }
.recent-win { color: rgba(230, 237, 243, 0.6); flex-shrink: 0; }

/* ===== FOOTER: Marquee ===== */
.display-footer {
  flex-shrink: 0;
  border-top: 1px solid rgba(255, 152, 0, 0.15);
  background: rgba(255, 152, 0, 0.04);
  overflow: hidden;
}
.marquee-bar {
  display: flex;
  align-items: center;
  padding: 0.6rem 1.5rem;
  gap: 0.8rem;
}
.marquee-icon {
  width: 18px;
  height: 18px;
  color: #ff9800;
  flex-shrink: 0;
}
.marquee-icon svg { width: 100%; height: 100%; }
.marquee-scroll {
  flex: 1;
  overflow: hidden;
  position: relative;
}
.marquee-text {
  display: inline-block;
  white-space: nowrap;
  animation: marquee 20s linear infinite;
  color: rgba(255, 152, 0, 0.9);
  font-size: 0.85em;
  line-height: 1.5;
}
@keyframes marquee {
  0% { transform: translateX(100%); }
  100% { transform: translateX(-100%); }
}
.marquee-empty {
  justify-content: center;
}
.marquee-placeholder {
  color: rgba(255, 152, 0, 0.3);
  font-size: 0.8em;
}

/* ===== QR DIALOG ===== */
.qr-dialog-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
}
.qr-dialog-canvas {
  width: 280px;
  height: 280px;
  border: 1px solid rgba(0, 229, 255, 0.2);
  border-radius: 8px;
}
.qr-dialog-tip {
  font-size: 1em;
  color: rgba(0, 229, 255, 0.9);
  font-weight: 600;
}
.qr-dialog-url {
  font-size: 0.75em;
  color: rgba(230, 237, 243, 0.4);
  word-break: break-all;
  text-align: center;
}

/* ===== RESPONSIVE ===== */
@media (max-width: 1200px) {
  .display-main {
    grid-template-columns: 180px 1fr 220px;
  }
  .hero-ticket {
    font-size: 3.5em;
  }
}
@media (max-width: 900px) {
  .display-header { flex-direction: column; gap: 1rem; padding: 1rem; }
  .header-left, .header-right { width: 100%; justify-content: center; }
  .display-main {
    grid-template-columns: 1fr;
  }
  .sidebar-left, .sidebar-right {
    display: none;
  }
  .hero-ticket {
    font-size: 3em;
  }
}
</style>
