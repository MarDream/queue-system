<template>
  <div class="page-shell">
    <el-drawer v-model="aiDrawerVisible" direction="rtl" size="620px" :with-header="false">
      <AiQueryPanel :workspace="'counter'" :counter-id="counterId" :hide-header="true" />
    </el-drawer>

    <header class="page-header">
      <span class="logo"><img src="/favicon.png" class="brand-icon" alt="QMS" /></span>
      <div class="sep"></div>
      <span class="page-title">窗口工作台</span>
      <div class="header-time-overlay">
        <div class="time-display">
          <span class="time-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12,6 12,12 16,14"/>
            </svg>
          </span>
          <span class="current-time mono">{{ currentTime }}</span>
        </div>
      </div>
      <div class="header-info">
        <span class="window-badge mono">{{ currentCounter?.name || '?' }}</span>
        <span class="staff-name">{{ userStore.name || userStore.username || '—' }}</span>
        <div class="live-dot"></div>
        <StatusBadge :status="counterStatus" type="counter" />
      </div>
      <div class="header-right">
        <div class="user-info">
          <span class="user-item">
            <el-icon class="info-icon"><Location /></el-icon>
            <span class="user-region">{{ userStore.regionName || '—' }}</span>
          </span>
          <el-divider direction="vertical" class="info-divider" />
          <span class="user-item">
            <el-icon class="info-icon"><UserFilled /></el-icon>
            <span class="user-role">{{ getRoleName(userStore.role) }}</span>
          </span>
          <el-divider direction="vertical" class="info-divider" />
          <span class="user-item">
            <el-icon class="info-icon"><Avatar /></el-icon>
            <span class="user-name">{{ userStore.name || userStore.username || '—' }}</span>
          </span>
        </div>
        <button v-if="canUseAi" class="logout-btn" @click="aiDrawerVisible = true" title="智能问数">
          <el-icon><ChatLineRound /></el-icon>
          <span>问数</span>
        </button>
        <button class="logout-btn" @click="handleLogout" title="退出登录">
          <el-icon><SwitchFilled /></el-icon>
          <span>退出</span>
        </button>
      </div>
    </header>

    <!-- Mobile tab bar -->
    <div class="mobile-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab-btn', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >
        <span class="tab-icon">{{ tab.icon }}</span>
        <span class="tab-label">{{ tab.label }}</span>
      </button>
    </div>

    <div class="counter-body">
      <div class="mobile-overview">
        <div class="overview-item">
          <span class="overview-label">当前窗口</span>
          <span class="overview-value mono">{{ currentCounter?.name || '未选择' }}</span>
        </div>
        <div class="overview-item">
          <span class="overview-label">窗口状态</span>
          <StatusBadge :status="counterStatus" type="counter" />
        </div>
        <div class="overview-item">
          <span class="overview-label">等待人数</span>
          <span class="overview-value mono">{{ waitingQueue.length }}</span>
        </div>
      </div>

      <div class="counter-selector">
        <label class="selector-label">选择窗口</label>
        <el-select v-model="counterId" placeholder="请选择窗口" @change="selectCounter" class="counter-select">
          <el-option v-for="c in counterList" :key="c.id" :label="c.name" :value="c.id" />
          <template #empty>
            <span style="color: var(--text-muted); font-size: 13px;">暂无窗口业务</span>
          </template>
        </el-select>
      </div>

      <!-- Left: queue list -->
      <div class="queue-panel" :class="{ 'tab-hidden': activeTab !== 'queue' }">
        <div class="panel-header">
          <span class="panel-title">等待队列</span>
          <span class="queue-count mono">{{ waitingQueue.length }}</span>
        </div>
        <div class="queue-list">
          <div v-if="waitingQueue.length === 0" class="no-queue">
            <el-icon style="font-size:32px;color:var(--text-muted)"><User /></el-icon>
            <p style="color:var(--text-muted);font-size:13px">暂无取号数据</p>
          </div>
          <div
            v-for="(item, idx) in waitingQueue"
            :key="item.id"
            :class="['queue-item', { 'queue-next': idx === 0 }]"
          >
            <span class="qi-seq">{{ idx + 1 }}</span>
            <div class="qi-info">
              <div class="qi-number">
                <el-tooltip v-if="item.reactivated" content="已激活" placement="top" :show-after="300">
                  <el-icon class="reactivated-icon"><RefreshRight /></el-icon>
                </el-tooltip>
                {{ getDisplayTicketNo(item.ticketNo) }}
              </div>
              <div class="qi-biz">{{ item.businessType }}</div>
            </div>
            <div class="qi-meta">
              <span class="qi-wait">{{ formatWait(item.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Center: main action area -->
      <div class="action-panel" :class="{ 'tab-hidden': activeTab !== 'action' }">
        <!-- Current serving -->
        <div class="serving-card" v-if="serving">
          <div class="sc-label">当前服务</div>
          <div class="sc-number mono glow">{{ getDisplayTicketNo(serving.number) }}</div>
          <div class="sc-biz">{{ serving.biz || '—' }}</div>
          <div class="sc-status">
            <StatusBadge :status="serving.status || 'called'" />
          </div>
          <div class="sc-timer mono">{{ formatTimer(serveSeconds) }}</div>
          <div class="sc-actions">
            <button
              v-if="serving.status !== 'serving'"
              class="action-btn btn-serve"
              :disabled="completing"
              @click="handleServe"
            >
              <el-icon><VideoPlay /></el-icon> 办理
            </button>
            <button class="action-btn btn-done" :disabled="completing" @click="handleComplete">
              <el-icon><Check /></el-icon> 办结
            </button>
            <button class="action-btn btn-recall" :disabled="completing || recalling || recallCount >= 3" @click="handleRecall">
              <el-icon><Bell /></el-icon> 重呼
              <span v-if="recallCount > 0" class="recall-badge">{{ 3 - recallCount }}</span>
            </button>
            <button class="action-btn btn-skip" :disabled="completing" @click="handleSkip">
              <el-icon><Right /></el-icon> 跳过
            </button>
          </div>
        </div>
        <div class="serving-empty" v-else>
          <el-icon style="font-size:48px;color:var(--text-muted)"><UserFilled /></el-icon>
          <p>暂无服务中客户</p>
          <span class="serving-hint">请点击"叫下一位"开始办理</span>
        </div>

        <!-- Call next button -->
        <button
          class="call-next-btn"
          :disabled="loading || !counterId || !!serving"
          @click="handleCallNext"
        >
          <el-icon><ArrowRight /></el-icon>
          <span>叫下一位</span>
        </button>

        <!-- Pause toggle -->
        <button
          :class="['pause-btn', paused ? 'paused' : '']"
          @click="handleTogglePause"
        >
          <el-icon><component :is="paused ? 'VideoPlay' : 'VideoPause'" /></el-icon>
          {{ paused ? '恢复服务' : '暂停服务' }}
        </button>
      </div>

      <!-- Right: today stats + history -->
      <div class="stats-panel" :class="{ 'tab-hidden': activeTab !== 'stats' }">
        <div class="panel-header">
          <span class="panel-title">今日统计</span>
        </div>
        <div class="stat-grid">
          <div class="stat-box" v-for="s in todayStats" :key="s.label">
            <div class="sb-value mono">{{ s.value }}</div>
            <div class="sb-label">{{ s.label }}</div>
          </div>
        </div>

        <div class="panel-header" style="margin-top:24px">
          <span class="panel-title">办结记录</span>
        </div>
        <div ref="historyListRef" class="history-list" @wheel="onHistoryWheel">
          <div v-for="h in pagedHistory" :key="h.id" class="history-row">
            <span class="mono text-muted" style="font-size:11px">{{ h.time }}</span>
            <span class="mono text-accent" style="font-size:13px">{{ getDisplayTicketNo(h.number) }}</span>
            <span style="font-size:12px;color:var(--text-secondary)">{{ h.biz }}</span>
            <span class="mono" style="font-size:11px;color:var(--text-muted)">{{ h.duration }}min</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useCommonStore } from '../stores/common'
import { callNext, recall, skip, serve, complete, togglePause } from '../api/counter'
import { getScreenData, getTicketList } from '../api/screen'
import { counterApi } from '../api/admin'
import { ElMessage } from 'element-plus'
import { SwitchFilled, Location, UserFilled, Avatar, RefreshRight, ChatLineRound } from '@element-plus/icons-vue'
import StatusBadge from '../components/StatusBadge.vue'
import { getDisplayTicketNo } from '../utils/ticketUtils'
import AiQueryPanel from '../components/admin/AiQueryPanel.vue'

const router = useRouter()
const userStore = useUserStore()
const store = useCommonStore()

const aiDrawerVisible = ref(false)
const canUseAi = computed(() => userStore.isSuperAdmin || userStore.role === 'WINDOW_OPERATOR')

const counterList = ref([])
const counterId = ref(null)
const currentCounter = computed(() => counterList.value.find(c => c.id === counterId.value))
const currentTime = ref('')
let timeTimer = null

function updateTime() {
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  currentTime.value = `${now.getFullYear()}年${pad(now.getMonth() + 1)}月${pad(now.getDate())}日 ${pad(now.getHours())}时${pad(now.getMinutes())}分${pad(now.getSeconds())}秒`
}
const paused = ref(false)
const serving = ref(null)
const counterStatus = computed(() => {
  if (paused.value) return 'paused'
  if (serving.value?.status === 'serving') return 'busy'
  if (serving.value) return 'called'
  return 'idle'
})
const serveSeconds = ref(0)
const history = ref([])
const historyListRef = ref(null)
const historyPage = ref(1)
const historyPageSize = 10
const historyTotalPages = computed(() => Math.max(1, Math.ceil(history.value.length / historyPageSize)))
const pagedHistory = computed(() => {
  const start = (historyPage.value - 1) * historyPageSize
  const end = start + historyPageSize
  return history.value.slice(start, end)
})
let historyWheelAccumulatedDelta = 0
let historyWheelResetTimer = null
let historyLastFlipAt = 0
const todayStats = ref([
  { label: '已办结', value: '0' },
  { label: '等待人数', value: '0' },
  { label: '重呼次数', value: '0' },
  { label: '跳过次数', value: '0' },
])
const loading = ref(false)
const completing = ref(false)
const recalling = ref(false)
const recallCount = ref(0)
const waitingQueue = ref([])

// Mobile tabs
const activeTab = ref('queue')
const tabs = computed(() => [
  { key: 'queue', label: `队列 ${waitingQueue.value.length}`, icon: '📋' },
  { key: 'action', label: '操作', icon: '⚡' },
  { key: 'stats', label: '统计', icon: '📊' },
])

let serveTimer = null
let pollTimer = null

// localStorage key: 窗口ID + 日期，确保每天独立计数
function todayKey() {
  const d = new Date()
  return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`
}
function stateKeyPrefix() {
  return `counter-state:${counterId.value}:${todayKey()}`
}

function loadCounterState() {
  if (!counterId.value) return
  try {
    const key = stateKeyPrefix()
    const saved = localStorage.getItem(key)
    if (saved) {
      const s = JSON.parse(saved)
      recallCount.value = s.recallCount ?? 0
      todayStats.value[2].value = String(s.recallStats ?? 0)
      todayStats.value[3].value = String(s.skipStats ?? 0)
      history.value = Array.isArray(s.history) ? s.history : []
      historyPage.value = 1
    }
  } catch {}
}

function saveCounterState() {
  if (!counterId.value) return
  const key = stateKeyPrefix()
  const s = {
    recallCount: recallCount.value,
    recallStats: parseInt(todayStats.value[2].value) || 0,
    skipStats: parseInt(todayStats.value[3].value) || 0,
    history: history.value,
  }
  localStorage.setItem(key, JSON.stringify(s))
}

onMounted(async () => {
  updateTime()
  timeTimer = setInterval(updateTime, 1000)
  await loadCounters()
  restoreDefaultCounter()
  await store.fetchBusinessTypes()
  loadCounterState()
  await fetchData()
  updateActiveTab(true)
  pollTimer = setInterval(fetchData, 5000)
})

onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
  if (pollTimer) clearInterval(pollTimer)
  if (historyWheelResetTimer) clearTimeout(historyWheelResetTimer)
})

watch(historyTotalPages, (p) => {
  if (historyPage.value > p) historyPage.value = p
})

async function loadCounters() {
  try {
    // 超级管理员不过滤；其他用户按区域权限过滤
    const params = userStore.isSuperAdmin ? {} : { userId: Number(userStore.userId) || undefined }
    counterList.value = await counterApi.list(params)
  } catch {
    counterList.value = []
  }
}

function restoreDefaultCounter() {
  const saved = localStorage.getItem('currentCounterId')
  if (saved) {
    const id = parseInt(saved)
    if (counterList.value.some(c => c.id === id)) {
      counterId.value = id
      return
    }
  }

  const myCounter = counterList.value.find(c => c.operatorName === userStore.name || c.operatorName === userStore.username)
  if (myCounter) {
    counterId.value = myCounter.id
    return
  }

  if (counterList.value.length === 1) {
    counterId.value = counterList.value[0].id
    return
  }

  if (!counterId.value && counterList.value.length > 0) {
    counterId.value = counterList.value[0].id
  }
}

function updateActiveTab(force = false) {
  if (!force) return
  if (serving.value) {
    activeTab.value = 'action'
    return
  }
  if (waitingQueue.value.length > 0) {
    activeTab.value = 'queue'
    return
  }
  activeTab.value = 'stats'
}

onUnmounted(() => {
  clearInterval(serveTimer)
  clearInterval(pollTimer)
})

async function selectCounter(id) {
  counterId.value = id
  localStorage.setItem('currentCounterId', id)
  loadCounterState()
  await fetchData()
  updateActiveTab(true)
}

async function fetchData() {
  if (!counterId.value) return
  try {
    const data = await getScreenData({ regionCode: userStore.regionCode })
    const myCounterName = currentCounter.value?.name
    if (!myCounterName) return

    const myCounter = (data.counters || []).find(c => c.name === myCounterName)
    if (myCounter) {
      paused.value = myCounter.status === 'paused'
    }

    const allWaiting = await getTicketList({ status: 'waiting' })
    const btIds = currentCounter.value?.businessTypeIds || []
    waitingQueue.value = btIds.length > 0
      ? allWaiting.filter(t => {
          const bt = store.businessTypes.find(b => b.id === t.businessTypeId || b.name === t.businessType)
          return bt && btIds.includes(bt.id)
        })
      : []

    const call = (data.currentCalls || []).find(c => c.counterName === myCounterName)
    if (call) {
      const currentTicket = allWaiting.find(t => t.ticketNo === call.ticketNo)
      serving.value = {
        number: getDisplayTicketNo(call.ticketNo),
        biz: currentTicket?.businessType || serving.value?.biz || '—',
        status: serving.value?.status === 'serving' ? 'serving' : 'called'
      }
    } else if (serving.value?.status !== 'serving') {
      serving.value = null
    }

    if (data.stats) {
      todayStats.value[0].value = String(data.stats.completedCount ?? 0)
      todayStats.value[1].value = data.stats.waitingCount != null ? String(data.stats.waitingCount) : '0'
    }

    updateActiveTab(false)
  } catch (err) {
    console.error('fetchData error:', err)
  }
}

function formatTimer(s) {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`
}

function getRoleName(role) {
  const map = {
    SUPER_ADMIN: '超级管理员',
    REGION_ADMIN: '区域管理员',
    WINDOW_OPERATOR: '窗口操作员'
  }
  return map[role] || role || '—'
}

function formatWait(createdAt) {
  if (!createdAt) return '--'
  // 后端返回 LocalDateTime 格式为数组 [year, month, day, hour, min, sec]
  let d
  if (Array.isArray(createdAt)) {
    d = new Date(createdAt[0], createdAt[1] - 1, createdAt[2], createdAt[3], createdAt[4], createdAt[5])
  } else {
    d = new Date(createdAt)
  }
  if (isNaN(d.getTime())) return '--'
  const now = new Date()
  const diffMs = now - d
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚'
  if (diffMin < 60) return `${diffMin}分钟`
  const h = Math.floor(diffMin / 60)
  const m = diffMin % 60
  return `${h}小时${m}分`
}

async function handleCallNext() {
  if (!counterId.value) { ElMessage.warning('请先选择窗口'); return }
  loading.value = true
  try {
    const data = await callNext(counterId.value)
    serving.value = { number: getDisplayTicketNo(data.ticketNo), biz: data.businessType || '—', status: 'called' }
    recallCount.value = 0
    serveSeconds.value = 0
    clearInterval(serveTimer)
    serveTimer = setInterval(() => serveSeconds.value++, 1000)
    await fetchData()
    updateActiveTab(true)
    ElMessage.success(`已叫号：${getDisplayTicketNo(data.ticketNo)}`)
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

async function handleRecall() {
  if (!counterId.value || !serving.value || recalling.value || recallCount.value >= 3) return
  recalling.value = true
  try {
    await recall(counterId.value)
    recallCount.value++
    todayStats.value[2].value = String(parseInt(todayStats.value[2].value) + 1)
    saveCounterState()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    recalling.value = false
  }
}

async function handleSkip() {
  if (!counterId.value || !serving.value || completing.value) return
  const savedNum = serving.value.number
  const savedBiz = serving.value.biz
  const savedStatus = serving.value.status
  completing.value = true
  try {
    await skip(counterId.value)
    serving.value = null
    clearInterval(serveTimer)
    await fetchData()
    todayStats.value[3].value = String(parseInt(todayStats.value[3].value) + 1)
    saveCounterState()
  } catch (err) {
    serving.value = { number: savedNum, biz: savedBiz, status: savedStatus }
    ElMessage.error(err.message)
  } finally {
    completing.value = false
  }
}

async function handleServe() {
  if (!counterId.value || !serving.value || completing.value) return
  try {
    await serve(counterId.value)
    serving.value = { ...serving.value, status: 'serving' }
    paused.value = false
    clearInterval(serveTimer)
    serveSeconds.value = 0
    serveTimer = setInterval(() => serveSeconds.value++, 1000)
    ElMessage.success('已开始办理')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function handleComplete() {
  if (!counterId.value || !serving.value || completing.value) return
  const savedNum = serving.value.number
  const savedBiz = serving.value.biz
  const savedStatus = serving.value.status
  const dur = Math.round(serveSeconds.value / 60) || 1
  const now = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  history.value.unshift({ id: Date.now(), time: now, number: savedNum, biz: savedBiz, duration: dur })
  historyPage.value = 1

  completing.value = true
  const completedCount = parseInt(todayStats.value[0].value) + 1
  todayStats.value[0].value = String(completedCount)
  serving.value = null
  clearInterval(serveTimer)
  saveCounterState()
  try {
    await complete(counterId.value)
    await fetchData()
  } catch (err) {
    serving.value = { number: savedNum, biz: savedBiz, status: savedStatus }
    todayStats.value[0].value = String(completedCount - 1)
    ElMessage.error(err.message)
  } finally {
    completing.value = false
  }
}

function scrollHistoryToTop() {
  const el = historyListRef.value
  if (el && typeof el.scrollTo === 'function') {
    el.scrollTo({ top: 0, behavior: 'smooth' })
  } else if (el) {
    el.scrollTop = 0
  }
}

function flipHistoryPage(direction) {
  const maxPage = historyTotalPages.value
  const next = Math.min(maxPage, Math.max(1, historyPage.value + direction))
  if (next === historyPage.value) return
  historyPage.value = next
  scrollHistoryToTop()
}

function onHistoryWheel(e) {
  const total = history.value.length
  if (!total) return
  if (historyTotalPages.value <= 1) return
  const el = historyListRef.value
  if (!el) return

  const now = Date.now()
  if (now - historyLastFlipAt < 250) return

  const scrollTop = el.scrollTop || 0
  const clientHeight = el.clientHeight || 0
  const scrollHeight = el.scrollHeight || 0
  const isAtTop = scrollTop <= 2
  const isAtBottom = scrollTop + clientHeight >= scrollHeight - 2

  historyWheelAccumulatedDelta += e.deltaY
  if (historyWheelResetTimer) clearTimeout(historyWheelResetTimer)
  historyWheelResetTimer = setTimeout(() => { historyWheelAccumulatedDelta = 0 }, 160)

  const threshold = 120
  if (Math.abs(historyWheelAccumulatedDelta) < threshold) return
  const dir = historyWheelAccumulatedDelta > 0 ? 1 : -1
  historyWheelAccumulatedDelta = 0

  if ((dir < 0 && isAtTop) || (dir > 0 && isAtBottom)) {
    e.preventDefault()
    historyLastFlipAt = now
    flipHistoryPage(dir)
  }
}

async function handleTogglePause() {
  if (!counterId.value) return
  try {
    await togglePause(counterId.value)
    paused.value = !paused.value
    if (paused.value) {
      updateActiveTab(true)
    }
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.counter-body {
  position: relative;
  padding: var(--sp-6);
  flex: 1;
  display: grid;
  grid-template-columns: 280px 1fr 280px;
  gap: var(--sp-4);
  overflow: hidden;
  background: var(--bg-body);
}

.counter-selector {
  position: absolute;
  top: 14px;
  left: 24px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.mobile-overview { display: none; }
.overview-item { display: flex; flex-direction: column; gap: 4px; }
.overview-label { font-size: 10px; color: var(--text-muted); letter-spacing: 0.08em; text-transform: uppercase; }
.overview-value { font-size: var(--text-sm); color: var(--text-primary); }
.selector-label { font-size: var(--text-xs); font-weight: 600; color: var(--text-secondary); }
.counter-select { width: 160px; }
.queue-panel {
  margin-top: 48px;
}

.queue-panel,
.stats-panel {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-4) var(--sp-5);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  min-height: 52px;
}

.panel-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.queue-count { font-size: var(--text-lg); color: var(--primary); font-weight: 700; line-height: 1; padding-left: 8px; }

.queue-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.queue-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.queue-next {
  border-color: rgba(0, 82, 217, 0.28);
  background: var(--primary-light);
}

.qi-seq { font-size: var(--text-xs); color: var(--text-muted); width: 20px; }
.queue-next .qi-seq,
.queue-next .qi-number { color: var(--primary); }
.qi-info { flex: 1; }
.qi-number { font-size: var(--text-sm); color: var(--text-primary); font-weight: 600; display: flex; align-items: center; gap: 4px; }
.reactivated-icon { font-size: 14px; color: var(--el-color-warning); flex-shrink: 0; }
.qi-biz,
.qi-wait { font-size: 11px; color: var(--text-muted); }
.qi-meta { display: flex; flex-direction: column; align-items: flex-end; gap: var(--sp-1); }

.action-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-5);
  padding: var(--sp-8) var(--sp-6);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.serving-card {
  width: 100%;
  max-width: 420px;
  background: linear-gradient(180deg, #ffffff 0%, #f7faff 100%);
  border: 1px solid rgba(0, 82, 217, 0.16);
  border-top: 4px solid var(--primary);
  border-radius: var(--radius-md);
  padding: var(--sp-8);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
}

.sc-label { font-size: var(--text-xs); font-weight: 600; color: var(--text-muted); }
.sc-number { font-size: clamp(48px, 5vw, 80px); color: var(--primary); line-height: 1; letter-spacing: 0.08em; }
.sc-biz { font-size: var(--text-base); color: var(--text-secondary); }
.sc-status { display: flex; justify-content: center; }
.sc-timer { font-size: var(--text-2xl); color: var(--text-primary); letter-spacing: 0.1em; }
.sc-actions { display: flex; gap: var(--sp-3); margin-top: var(--sp-3); width: 100%; flex-wrap: wrap; }

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  padding: var(--sp-3);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  border: 1px solid;
  min-height: var(--touch-md);
  background: #fff;
}

.btn-serve,
.btn-done { border-color: var(--primary); color: #fff; background: var(--primary); }
.btn-serve:hover,
.btn-done:hover { background: var(--primary-hover); border-color: var(--primary-hover); }
.btn-recall,
.btn-skip { border-color: var(--border-input); color: var(--text-primary); }
.btn-recall:hover,
.btn-skip:hover { border-color: var(--primary); color: var(--primary); background: var(--primary-light); }

.recall-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  background: var(--primary-light);
  color: var(--primary);
  border-radius: var(--radius-full);
  font-size: 10px;
  font-weight: 700;
}

.serving-empty {
  width: 100%;
  max-width: 420px;
  height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  background: #f7f8fa;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-md);
  color: var(--text-muted);
  font-size: var(--text-base);
}

.serving-hint { font-size: var(--text-sm); color: var(--text-secondary); }

.call-next-btn {
  width: 100%;
  max-width: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-4);
  background: var(--primary);
  border: 1px solid var(--primary);
  border-radius: var(--radius-md);
  color: #fff;
  font-size: var(--text-lg);
  font-weight: 700;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out);
  min-height: var(--touch-lg);
}

.call-next-btn:hover:not(:disabled) { background: var(--primary-hover); border-color: var(--primary-hover); }
.call-next-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.pause-btn {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-6);
  background: #fff;
  border: 1px solid var(--warning);
  border-radius: var(--radius-md);
  color: var(--warning);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  min-height: var(--touch-md);
}

.pause-btn:hover { background: var(--warning-light); }
.pause-btn.paused { border-color: var(--primary); color: var(--primary); }

.stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-2); padding: var(--sp-4); }
.stat-box {
  background: #f7f8fa;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: var(--sp-4) var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}
.sb-value { font-size: var(--text-xl); color: var(--primary); }
.sb-label { font-size: 10px; color: var(--text-muted); }

.history-list { padding: var(--sp-2) var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-2); overflow-y: auto; max-height: 320px; }
.history-row {
  display: grid;
  grid-template-columns: 44px 52px 1fr 32px;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  background: #f7f8fa;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.header-right { margin-left: auto; display: flex; align-items: center; gap: var(--sp-4); }
:deep(.page-header .logo) { font-size: 0; line-height: 0; text-shadow: none; }
:deep(.page-header .logo .brand-icon) { height: 28px; width: auto; }
.header-info { display: flex; align-items: center; gap: var(--sp-3); margin-left: var(--sp-4); }
.header-time-overlay { position: absolute; left: 50%; transform: translateX(-50%); }
.time-display {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-4);
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.14);
  border-radius: var(--radius-md);
}
.time-icon { display: flex; align-items: center; }
.time-icon svg { width: 18px; height: 18px; color: rgba(255,255,255,0.82); }
.current-time { font-size: var(--text-base); font-weight: 700; color: #fff; letter-spacing: 0.04em; }
.window-badge {
  font-size: var(--text-sm);
  color: #fff;
  background: var(--primary);
  border: 1px solid rgba(255,255,255,0.1);
  padding: 2px var(--sp-3);
  border-radius: var(--radius-sm);
}
.staff-name { font-size: var(--text-sm); font-weight: 600; color: var(--text-inverse); }
.status-text { font-size: 12px; color: rgba(255,255,255,0.6); }

.user-info {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-2) var(--sp-4);
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.14);
  border-radius: var(--radius-md);
}
.user-item { display: flex; align-items: center; gap: var(--sp-1); }
.info-icon { font-size: 14px; color: rgba(255,255,255,0.7); }
.info-divider { height: 16px; margin: 0; border-color: rgba(255,255,255,0.14); }
.user-region { font-size: var(--text-xs); color: rgba(255,255,255,0.72); }
.user-role {
  font-size: var(--text-xs);
  color: #fff;
  background: rgba(0,82,217,0.24);
  padding: 2px var(--sp-2);
  border-radius: var(--radius-sm);
}
.user-name { font-size: var(--text-sm); font-weight: 600; color: #fff; }
.logout-btn {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  padding: var(--sp-2) var(--sp-3);
  background: transparent;
  border: 1px solid rgba(255,255,255,0.16);
  border-radius: var(--radius-md);
  color: rgba(255,255,255,0.86);
  cursor: pointer;
  transition: all 0.15s;
}
.logout-btn:hover { border-color: #fff; color: #fff; background: rgba(255,255,255,0.08); }

.mobile-tabs {
  display: none;
  background: #fff;
  border-bottom: 1px solid var(--border);
  padding: var(--sp-2);
  gap: var(--sp-1);
}
.tab-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--sp-2);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  min-height: var(--touch-lg);
}
.tab-btn.active { color: var(--primary); background: var(--primary-light); border-color: rgba(0,82,217,0.15); }
.tab-icon { font-size: 18px; }
.tab-label { font-size: var(--text-xs); font-weight: 600; }

@media (max-width: 1200px) {
  .counter-body { grid-template-columns: 220px 1fr 220px; }
  .serving-card { padding: var(--sp-6); }
  .sc-number { font-size: clamp(40px, 4vw, 64px); }
}

@media (max-width: 768px) {
  .counter-body {
    grid-template-columns: 1fr;
    padding-top: 56px;
  }

  .counter-selector { top: 10px; left: var(--sp-4); right: var(--sp-4); }
  .counter-select { flex: 1; }
  .queue-panel,
  .stats-panel { max-height: 35vh; }
  .action-panel { min-height: 300px; }
  .serving-card,
  .serving-empty,
  .call-next-btn { max-width: 100%; }
  .history-row { grid-template-columns: 40px 48px 1fr 28px; }
}

@media (max-width: 480px) {
  .page-header { padding: var(--sp-2) var(--sp-3); }
  .header-info { margin-left: var(--sp-2); gap: var(--sp-2); }
  .staff-name,
  .status-text { display: none; }
  .mobile-tabs { display: flex; }
  .tab-hidden { display: none; }
  .mobile-overview {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--sp-2);
    padding: var(--sp-3);
    background: #fff;
    border-bottom: 1px solid var(--border);
  }
  .counter-body { grid-template-columns: 1fr; padding: 0; gap: 0; }
  .counter-selector {
    position: static;
    padding: var(--sp-3);
    background: #fff;
    border-bottom: 1px solid var(--border);
  }
  .queue-panel,
  .stats-panel,
  .action-panel { border-radius: 0; border-left: none; border-right: none; max-height: none; box-shadow: none; }
  .action-panel { padding: var(--sp-5) var(--sp-4); }
  .sc-number { font-size: clamp(36px, 10vw, 56px); }
  .action-btn { padding: var(--sp-3) var(--sp-2); font-size: var(--text-xs); }
  .call-next-btn { font-size: var(--text-base); padding: var(--sp-3); }
  .history-row { grid-template-columns: 1fr; gap: var(--sp-1); text-align: center; }
}
</style>
